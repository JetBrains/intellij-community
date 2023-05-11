// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.history.integration;

import com.intellij.history.core.LocalHistoryFacade;
import com.intellij.history.core.Paths;
import com.intellij.history.core.StoredContent;
import com.intellij.history.core.tree.DirectoryEntry;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.core.tree.FileEntry;
import com.intellij.history.core.tree.RootEntry;
import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Clock;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.encoding.EncodingRegistry;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class IdeaGateway {
  private static final Key<ContentAndTimestamps> SAVED_DOCUMENT_CONTENT_AND_STAMP_KEY
    = Key.create("LocalHistory.SAVED_DOCUMENT_CONTENT_AND_STAMP_KEY");

  public boolean isVersioned(@NotNull VirtualFile f) {
    return isVersioned(f, false);
  }

  public boolean isVersioned(@NotNull VirtualFile f, boolean shouldBeInContent) {
    if (VersionManagingFileSystem.isDisabled(f)) {
      return false;
    }
    if (!f.isInLocalFileSystem()) {
      return VersionManagingFileSystem.isEnforcedNonLocal(f);
    }

    if (!f.isDirectory()) {
      CharSequence fileName = f.getNameSequence();
      if (StringUtil.equals(fileName, "workspace.xml") || StringUtil.endsWith(fileName, ".iws")
          || StringUtil.endsWith(fileName, ".class")) {
        return false;
      }
    }

    VersionedFilterData versionedFilterData = getVersionedFilterData();

    int numberOfOpenProjects = versionedFilterData.myOpenedProjects.size();

    // optimisation: FileTypeManager.isFileIgnored(f) will be checked inside ProjectFileIndex.isUnderIgnored()
    if (numberOfOpenProjects == 0) {
      if (shouldBeInContent) return false; // there is no project, so the file can't be in content
      if (FileTypeManager.getInstance().isFileIgnored(f)) return false;

      return true;
    }

    boolean isExcludedFromAll = true;
    boolean isInContent = false;

    for (int i = 0; i < numberOfOpenProjects; ++i) {
      ProjectFileIndex index = versionedFilterData.myProjectFileIndices.get(i);

      if (index.isUnderIgnored(f)) return false;
      isInContent |= index.isInContent(f);
      isExcludedFromAll &= index.isExcluded(f);
    }

    if (isExcludedFromAll) return false;
    if (shouldBeInContent && !isInContent) return false;

    return true;
  }

  public String getPathOrUrl(@NotNull VirtualFile file) {
    return file.isInLocalFileSystem() ? file.getPath() : file.getUrl();
  }

  @NotNull
  protected static VersionedFilterData getVersionedFilterData() {
    VersionedFilterData versionedFilterData;
    VfsEventDispatchContext vfsEventDispatchContext = ourCurrentEventDispatchContext.get();
    if (vfsEventDispatchContext != null) {
      versionedFilterData = vfsEventDispatchContext.myFilterData;
      if (versionedFilterData == null) versionedFilterData = vfsEventDispatchContext.myFilterData = new VersionedFilterData();
    } else {
      versionedFilterData = new VersionedFilterData();
    }
    return versionedFilterData;
  }

  private static final ThreadLocal<VfsEventDispatchContext> ourCurrentEventDispatchContext = new ThreadLocal<>();

  private static class VfsEventDispatchContext implements AutoCloseable {
    final List<? extends VFileEvent> myEvents;
    final boolean myBeforeEvents;
    final VfsEventDispatchContext myPreviousContext;

    VersionedFilterData myFilterData;

    VfsEventDispatchContext(List<? extends VFileEvent> events, boolean beforeEvents) {
      myEvents = events;
      myBeforeEvents = beforeEvents;
      myPreviousContext = ourCurrentEventDispatchContext.get();
      if (myPreviousContext != null) {
        myFilterData = myPreviousContext.myFilterData;
      }
      ourCurrentEventDispatchContext.set(this);
    }

    @Override
    public void close() {
      ourCurrentEventDispatchContext.set(myPreviousContext);
      if (myPreviousContext != null && myPreviousContext.myFilterData == null && myFilterData != null) {
        myPreviousContext.myFilterData = myFilterData;
      }
    }
  }

  public void runWithVfsEventsDispatchContext(List<? extends VFileEvent> events, boolean beforeEvents, Runnable action) {
    try (VfsEventDispatchContext ignored = new VfsEventDispatchContext(events, beforeEvents)) {
      action.run();
    }
  }

  private static class VersionedFilterData {
    final List<Project> myOpenedProjects = new ArrayList<>();
    final List<ProjectFileIndex> myProjectFileIndices = new ArrayList<>();

    VersionedFilterData() {
      Project[] openProjects = ProjectManager.getInstance().getOpenProjects();

      for (Project each : openProjects) {
        if (each.isDefault()) continue;
        if (!each.isInitialized()) continue;

        myOpenedProjects.add(each);
        myProjectFileIndices.add(ProjectRootManager.getInstance(each).getFileIndex());
      }
    }
  }

  public boolean areContentChangesVersioned(@NotNull VirtualFile f) {
    return isVersioned(f) && !f.isDirectory() &&
           (areContentChangesVersioned(f.getName()) || ScratchFileService.findRootType(f) != null);
  }

  public boolean areContentChangesVersioned(@NotNull String fileName) {
    return !FileTypeManager.getInstance().getFileTypeByFileName(fileName).isBinary();
  }

  public boolean ensureFilesAreWritable(@NotNull Project p, @NotNull List<? extends VirtualFile> ff) {
    ReadonlyStatusHandler h = ReadonlyStatusHandler.getInstance(p);
    return !h.ensureFilesWritable(ff).hasReadonlyFiles();
  }

  @Nullable
  public VirtualFile findVirtualFile(@NotNull String path) {
    if (path.contains(URLUtil.SCHEME_SEPARATOR)) {
      return VirtualFileManager.getInstance().findFileByUrl(path);
    }
    VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
    if (file == null && ApplicationManager.getApplication().isUnitTestMode()) {
      return TempFileSystem.getInstance().findFileByPath(path);
    }
    return file;
  }

  @NotNull
  public VirtualFile findOrCreateFileSafely(@NotNull VirtualFile parent, @NotNull String name, boolean isDirectory) throws IOException {
    VirtualFile f = parent.findChild(name);
    if (f != null && f.isDirectory() != isDirectory) {
      f.delete(this);
      f = null;
    }
    if (f == null) {
      f = isDirectory
          ? parent.createChildDirectory(this, name)
          : parent.createChildData(this, name);
    }
    return f;
  }

  @NotNull
  public VirtualFile findOrCreateFileSafely(@NotNull String path, boolean isDirectory) throws IOException {
    VirtualFile f = findVirtualFile(path);
    if (f != null && f.isDirectory() != isDirectory) {
      f.delete(this);
      f = null;
    }
    if (f == null) {
      VirtualFile parent = findOrCreateFileSafely(Paths.getParentOf(path), true);
      String name = Paths.getNameOf(path);
      f = isDirectory
          ? parent.createChildDirectory(this, name)
          : parent.createChildData(this, name);
    }
    return f;
  }

  public List<VirtualFile> getAllFilesFrom(@NotNull String path) {
    VirtualFile f = findVirtualFile(path);
    if (f == null) return Collections.emptyList();
    return collectFiles(f, new ArrayList<>());
  }

  @NotNull
  private static List<VirtualFile> collectFiles(@NotNull VirtualFile f, @NotNull List<VirtualFile> result) {
    if (f.isDirectory()) {
      for (VirtualFile child : iterateDBChildren(f)) {
        collectFiles(child, result);
      }
    }
    else {
      result.add(f);
    }
    return result;
  }

  @NotNull
  public static Iterable<VirtualFile> iterateDBChildren(VirtualFile f) {
    if (!(f instanceof NewVirtualFile nf)) return Collections.emptyList();
    return nf.iterInDbChildrenWithoutLoadingVfsFromOtherProjects();
  }

  @NotNull
  public static Iterable<VirtualFile> loadAndIterateChildren(VirtualFile f) {
    if (!(f instanceof NewVirtualFile nf)) return Collections.emptyList();
    return Arrays.asList(nf.getChildren());
  }

  @NotNull
  public RootEntry createTransientRootEntry() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    RootEntry root = new RootEntry();
    doCreateChildren(root, getLocalRoots(), false);
    return root;
  }

  @NotNull
  public RootEntry createTransientRootEntryForPathOnly(@NotNull String path) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    RootEntry root = new RootEntry();
    doCreateChildrenForPathOnly(root, path, getLocalRoots());
    return root;
  }

  private static List<VirtualFile> getLocalRoots() {
    List<VirtualFile> roots = new SmartList<>();

    for (VirtualFile root : ManagingFS.getInstance().getRoots()) {
      if ((root.isInLocalFileSystem() || VersionManagingFileSystem.isEnforcedNonLocal(root)) && !(root.getFileSystem() instanceof TempFileSystem)) {
        roots.add(root);
      }
    }
    return roots;
  }

  private void doCreateChildrenForPathOnly(@NotNull DirectoryEntry parent,
                                           @NotNull String path,
                                           @NotNull Iterable<? extends VirtualFile> children) {
    for (VirtualFile child : children) {
      String name = StringUtil.trimStart(child.getName(), "/"); // on Mac FS root name is "/"
      if (!path.startsWith(name)) continue;
      String rest = path.substring(name.length());
      if (!rest.isEmpty() && rest.charAt(0) != '/') continue;
      if (!rest.isEmpty() && rest.charAt(0) == '/') {
        rest = rest.substring(1);
      }
      Entry e = doCreateEntryForPathOnly(child, rest);
      if (e == null) continue;
      parent.addChild(e);
    }
  }

  @Nullable
  private Entry doCreateEntryForPathOnly(@NotNull VirtualFile file, @NotNull String path) {
    if (!file.isDirectory()) {
      if (!isVersioned(file)) return null;

      return doCreateFileEntry(file, getActualContentNoAcquire(file));
    }
    DirectoryEntry newDir = new DirectoryEntry(file.getName());
    doCreateChildrenForPathOnly(newDir, path, iterateDBChildren(file));
    if (!isVersioned(file) && newDir.getChildren().isEmpty()) return null;
    return newDir;
  }

  @Nullable
  public Entry createTransientEntry(@NotNull VirtualFile file) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    return doCreateEntry(file, false);
  }

  @Nullable
  public Entry createEntryForDeletion(@NotNull VirtualFile file) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    return doCreateEntry(file, true);
  }

  @Nullable
  private Entry doCreateEntry(@NotNull VirtualFile file, boolean forDeletion) {
    if (!file.isDirectory()) {
      if (!isVersioned(file)) return null;

      Pair<StoredContent, Long> contentAndStamps;
      if (forDeletion) {
        FileDocumentManager m = FileDocumentManager.getInstance();
        Document d = m.isFileModified(file) ? m.getCachedDocument(file) : null; // should not try to load document
        contentAndStamps = acquireAndClearCurrentContent(file, d);
      }
      else {
        contentAndStamps = getActualContentNoAcquire(file);
      }

      return doCreateFileEntry(file, contentAndStamps);
    }

    DirectoryEntry newDir = null;
    boolean nonLocalRoot = !file.isInLocalFileSystem() && file.getParent() == null;
    if (file instanceof VirtualFileSystemEntry && !nonLocalRoot) {
      int nameId = ((VirtualFileSystemEntry)file).getNameId();
      if (nameId > 0) {
        newDir = new DirectoryEntry(nameId);
      }
    }

    DirectoryEntry res;
    if (newDir == null) {
      if (nonLocalRoot) {
        DirectoryEntry first = null;
        for (String item : Paths.split(file.getUrl())) {
          DirectoryEntry cur = new DirectoryEntry(item);
          if (first == null) first = cur;
          if (newDir != null) newDir.addChild(cur);
          newDir = cur;
        }
        res = first;
      }
      else {
        newDir = new DirectoryEntry(file.getName());
        res = newDir;
      }
    }
    else {
      res = newDir;
    }

    doCreateChildren(newDir, iterateDBChildren(file), forDeletion);
    if (!isVersioned(file) && newDir.getChildren().isEmpty()) return null;
    return res;
  }

  @NotNull
  private Entry doCreateFileEntry(@NotNull VirtualFile file, Pair<StoredContent, Long> contentAndStamps) {
    if (file instanceof VirtualFileSystemEntry) {
      return new FileEntry(((VirtualFileSystemEntry)file).getNameId(), contentAndStamps.first, contentAndStamps.second, !file.isWritable());
    }
    return new FileEntry(file.getName(), contentAndStamps.first, contentAndStamps.second, !file.isWritable());
  }

  private void doCreateChildren(@NotNull DirectoryEntry parent, Iterable<? extends VirtualFile> children, final boolean forDeletion) {
    List<Entry> entries = ContainerUtil.mapNotNull(children, each -> doCreateEntry(each, forDeletion));
    parent.addChildren(entries);
  }

  public void registerUnsavedDocuments(@NotNull final LocalHistoryFacade vcs) {
    ApplicationManager.getApplication().runReadAction(() -> {
      vcs.beginChangeSet();
      for (Document d : FileDocumentManager.getInstance().getUnsavedDocuments()) {
        VirtualFile f = getFile(d);
        if (!shouldRegisterDocument(f)) continue;
        registerDocumentContents(vcs, f, d);
      }
      vcs.endChangeSet(null);
    });
  }

  private boolean shouldRegisterDocument(@Nullable VirtualFile f) {
    return f != null && f.isValid() && areContentChangesVersioned(f);
  }

  private void registerDocumentContents(@NotNull LocalHistoryFacade vcs, @NotNull VirtualFile f, Document d) {
    Pair<StoredContent, Long> contentAndStamp = acquireAndUpdateActualContent(f, d);
    if (contentAndStamp != null) {
      vcs.contentChanged(getPathOrUrl(f), contentAndStamp.first, contentAndStamp.second);
    }
  }

  // returns null is content has not been changes since last time
  @Nullable
  public Pair<StoredContent, Long> acquireAndUpdateActualContent(@NotNull VirtualFile f, @Nullable Document d) {
    ContentAndTimestamps contentAndStamp = f.getUserData(SAVED_DOCUMENT_CONTENT_AND_STAMP_KEY);
    if (contentAndStamp == null) {
      if (d != null) saveDocumentContent(f, d);
      return Pair.create(StoredContent.acquireContent(f), f.getTimeStamp());
    }

    // if no need to save current document content when simply return and clear stored one
    if (d == null) {
      f.putUserData(SAVED_DOCUMENT_CONTENT_AND_STAMP_KEY, null);
      return Pair.create(contentAndStamp.content, contentAndStamp.registeredTimestamp);
    }

    // if the stored content equals the current one, do not store it and return null
    if (d.getModificationStamp() == contentAndStamp.documentModificationStamp) return null;

    // is current content has been changed, store it and return the previous one
    saveDocumentContent(f, d);
    return Pair.create(contentAndStamp.content, contentAndStamp.registeredTimestamp);
  }

  private static void saveDocumentContent(@NotNull VirtualFile f, @NotNull Document d) {
    f.putUserData(SAVED_DOCUMENT_CONTENT_AND_STAMP_KEY,
                  new ContentAndTimestamps(Clock.getTime(),
                                           StoredContent.acquireContent(bytesFromDocument(d)),
                                           d.getModificationStamp()));
  }

  @NotNull
  public Pair<StoredContent, Long> acquireAndClearCurrentContent(@NotNull VirtualFile f, @Nullable Document d) {
    ContentAndTimestamps contentAndStamp = f.getUserData(SAVED_DOCUMENT_CONTENT_AND_STAMP_KEY);
    f.putUserData(SAVED_DOCUMENT_CONTENT_AND_STAMP_KEY, null);

    if (d != null && contentAndStamp != null) {
      // if previously stored content was not changed, return it
      if (d.getModificationStamp() == contentAndStamp.documentModificationStamp) {
        return Pair.create(contentAndStamp.content, contentAndStamp.registeredTimestamp);
      }
    }

    // release previously stored
    if (contentAndStamp != null) {
      contentAndStamp.content.release();
    }

    // take document's content if any
    if (d != null) {
      return Pair.create(StoredContent.acquireContent(bytesFromDocument(d)), Clock.getTime());
    }

    return Pair.create(StoredContent.acquireContent(f), f.getTimeStamp());
  }

  @NotNull
  private static Pair<StoredContent, Long> getActualContentNoAcquire(@NotNull VirtualFile f) {
    ContentAndTimestamps result = f.getUserData(SAVED_DOCUMENT_CONTENT_AND_STAMP_KEY);
    if (result == null) {
      return Pair.create(StoredContent.transientContent(f), f.getTimeStamp());
    }
    return Pair.create(result.content, result.registeredTimestamp);
  }

  private static byte[] bytesFromDocument(@NotNull Document d) {
    VirtualFile file = getFile(d);
    Charset charset = file != null ? file.getCharset() : EncodingRegistry.getInstance().getDefaultCharset();
    return d.getText().getBytes(charset);
  }

  public String stringFromBytes(byte @NotNull [] bytes, @NotNull String path) {
    VirtualFile file = findVirtualFile(path);
    Charset charset = file != null ? file.getCharset() : EncodingRegistry.getInstance().getDefaultCharset();
    return CharsetToolkit.bytesToString(bytes, charset);
  }

  public void saveAllUnsavedDocuments() {
    FileDocumentManager.getInstance().saveAllDocuments();
  }

  @Nullable
  private static VirtualFile getFile(@NotNull Document d) {
    return FileDocumentManager.getInstance().getFile(d);
  }

  @Nullable
  public Document getDocument(@NotNull String path) {
    return FileDocumentManager.getInstance().getDocument(findVirtualFile(path));
  }

  @NotNull
  public FileType getFileType(@NotNull String fileName) {
    return FileTypeManager.getInstance().getFileTypeByFileName(fileName);
  }

  private static final class ContentAndTimestamps {
    long registeredTimestamp;
    StoredContent content;
    long documentModificationStamp;

    private ContentAndTimestamps(long registeredTimestamp, StoredContent content, long documentModificationStamp) {
      this.registeredTimestamp = registeredTimestamp;
      this.content = content;
      this.documentModificationStamp = documentModificationStamp;
    }
  }
}
