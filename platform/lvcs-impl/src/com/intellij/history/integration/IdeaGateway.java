// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.integration;

import com.intellij.history.core.LocalHistoryFacade;
import com.intellij.history.core.Paths;
import com.intellij.history.core.StoredContent;
import com.intellij.history.core.tree.DirectoryEntry;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.core.tree.FileEntry;
import com.intellij.history.core.tree.RootEntry;
import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VersionManagingFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.encoding.EncodingRegistry;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.util.SlowOperations;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public class IdeaGateway {
  private static final Logger LOG = Logger.getInstance(IdeaGateway.class);
  private static final Key<DocumentContentWithTimestamps> SAVED_DOCUMENT_CONTENT_AND_STAMP_KEY
    = Key.create("LocalHistory.SAVED_DOCUMENT_CONTENT_AND_STAMP_KEY");

  public static @NotNull IdeaGateway getInstance() {
    return ApplicationManager.getApplication().getService(IdeaGateway.class);
  }

  public boolean isVersioned(@NotNull VirtualFile f) {
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

    int numberOfOpenProjects = versionedFilterData.myProjectFileIndices.size();

    // optimisation: FileTypeManager.isFileIgnored(f) will be checked inside ProjectFileIndex.isUnderIgnored()
    if (numberOfOpenProjects == 0) {
      if (FileTypeManager.getInstance().isFileIgnored(f)) return false;

      return true;
    }

    boolean underAnyProject = false;
    for (ProjectFileIndex projectFileIndex : versionedFilterData.myProjectFileIndices) {
      boolean isProjectRelated = projectFileIndex.isInProjectOrExcluded(f) || projectFileIndex.isUnderIgnored(f);
      // isIsContent returns true when the file or directory is under the content root of the project,
      // AND is not excluded or ignored
      if (isProjectRelated && projectFileIndex.isInContent(f)) {
        // File is under the content root and isn't ignored/excluded. Track it in LVCS.
        return true;
      }
      underAnyProject |= isProjectRelated;
    }

    if (underAnyProject) {
      // File does not belong to any content root, but it is excluded by one or more projects.
      // Do not track it in LVCS.
      return false;
    }
    else {
      // File is outside all the projects. Let's track it anyway because a user may edit some external files or scratch files.
      // Check only if the file matches any ignored pattern.
      return !FileTypeManager.getInstance().isFileIgnored(f);
    }
  }

  public @NotNull String getPathOrUrl(@NotNull VirtualFile file) {
    return file.isInLocalFileSystem() ? file.getPath() : file.getUrl();
  }

  public static @NotNull String getNameOrUrlPart(@NotNull VirtualFile file) {
    String name = file.getName();
    if (file.getParent() != null) return name;
    if (file.isInLocalFileSystem()) {
      return "/".equals(name) ? "" : name; // on Unix FS root name is "/"
    }
    return VirtualFileManager.constructUrl(file.getFileSystem().getProtocol(), StringUtil.trimStart(name, "/"));
  }

  protected static @NotNull VersionedFilterData getVersionedFilterData() {
    VersionedFilterData versionedFilterData;
    VfsEventDispatchContext vfsEventDispatchContext = ourCurrentEventDispatchContext.get();
    if (vfsEventDispatchContext != null) {
      versionedFilterData = vfsEventDispatchContext.myFilterData;
      if (versionedFilterData == null) versionedFilterData = vfsEventDispatchContext.myFilterData = new VersionedFilterData();
    }
    else {
      versionedFilterData = new VersionedFilterData();
    }
    return versionedFilterData;
  }

  private static final ThreadLocal<VfsEventDispatchContext> ourCurrentEventDispatchContext = new ThreadLocal<>();

  private static final class VfsEventDispatchContext implements AutoCloseable {
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

  public void runWithVfsEventsDispatchContext(List<? extends VFileEvent> events, boolean beforeEvents, @NotNull Runnable action) {
    try (VfsEventDispatchContext ignored = new VfsEventDispatchContext(events, beforeEvents)) {
      action.run();
    }
  }

  protected static final class VersionedFilterData {
    final List<ProjectFileIndex> myProjectFileIndices = new ArrayList<>();

    VersionedFilterData() {
      Project[] openProjects = ProjectManager.getInstance().getOpenProjects();

      for (Project each : openProjects) {
        if (each.isDefault()) continue;
        if (!each.isInitialized()) continue;

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

  public @Nullable VirtualFile findVirtualFile(@NotNull String path) {
    if (path.contains(URLUtil.SCHEME_SEPARATOR)) {
      return VirtualFileManager.getInstance().findFileByUrl(path);
    }
    VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
    if (file == null && ApplicationManager.getApplication().isUnitTestMode()) {
      return TempFileSystem.getInstance().findFileByPath(path);
    }
    return file;
  }

  public @NotNull VirtualFile findOrCreateFileSafely(@NotNull VirtualFile parent, @NotNull String name, boolean isDirectory)
    throws IOException {
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

  public @NotNull VirtualFile findOrCreateFileSafely(@NotNull String path, boolean isDirectory) throws IOException {
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

  public static @NotNull Iterable<VirtualFile> iterateDBChildren(VirtualFile f) {
    if (!(f instanceof NewVirtualFile nf) || !f.isValid()) return Collections.emptyList();
    return nf.iterInDbChildrenWithoutLoadingVfsFromOtherProjects();
  }

  public static @NotNull Iterable<VirtualFile> loadAndIterateChildren(VirtualFile f) {
    if (!(f instanceof NewVirtualFile nf) || !f.isValid()) return Collections.emptyList();
    return Arrays.asList(nf.getChildren());
  }

  @RequiresReadLock
  public @NotNull RootEntry createTransientRootEntry() {
    RootEntry root = new RootEntry();
    doCreateChildren(root, getLocalRoots(), IdeaGateway::getActualContentNoAcquire, null);
    return root;
  }

  @RequiresReadLock
  public @NotNull RootEntry createTransientRootEntryForPath(@NotNull String path, boolean includeChildren) {
    RootEntry root = new RootEntry();
    doCreateChildren(root, getLocalRoots(), IdeaGateway::getActualContentNoAcquire, new SinglePathVisitor(path, includeChildren));
    return root;
  }

  @RequiresReadLock
  public @NotNull RootEntry createTransientRootEntryForPaths(@NotNull Collection<String> paths, boolean includeChildren) {
    if (paths.isEmpty()) return new RootEntry();

    String singlePath = ContainerUtil.getOnlyItem(paths);
    if (singlePath != null) {
      return createTransientRootEntryForPath(singlePath, includeChildren);
    }

    RootEntry root = new RootEntry();
    List<SinglePathVisitor> visitors = ContainerUtil.map(paths, s -> new SinglePathVisitor(s, includeChildren));
    doCreateChildren(root, getLocalRoots(), IdeaGateway::getActualContentNoAcquire, new MergingPathVisitor(visitors));
    return root;
  }

  private static @NotNull List<VirtualFile> getLocalRoots() {
    List<VirtualFile> roots = new SmartList<>();

    for (VirtualFile root : ManagingFS.getInstance().getRoots()) {
      if ((root.isInLocalFileSystem() || VersionManagingFileSystem.isEnforcedNonLocal(root)) &&
          !(root.getFileSystem() instanceof TempFileSystem)) {
        roots.add(root);
      }
    }
    return roots;
  }

  @RequiresReadLock
  public @Nullable Entry createTransientEntry(@NotNull VirtualFile file) {
    return doCreateEntry(file, IdeaGateway::getActualContentNoAcquire, null);
  }

  @RequiresReadLock
  public @Nullable Entry createEntryForDeletion(@NotNull VirtualFile file) {
    return doCreateEntry(file, f -> acquireContentForDeletedFile(f, null), null);
  }

  @Nullable Entry doCreateEntry(@NotNull VirtualFile file,
                                @NotNull Function<@NotNull VirtualFile, @NotNull ContentWithTimestamp> contentProvider,
                                @Nullable FileTreeVisitor visitor) {
    if (!file.isDirectory()) {
      if (!isVersioned(file)) return null;

      return doCreateFileEntry(file, contentProvider);
    }

    DirectoryEntries entries = doCreateDirectoryEntries(file);
    if (entries == null) return null;

    doCreateChildren(entries.last, iterateDBChildren(file), contentProvider, visitor);
    if (!isVersioned(file) && entries.last.getChildren().isEmpty()) return null;
    return entries.first;
  }

  private static @NotNull Entry doCreateFileEntry(@NotNull VirtualFile file,
                                                  @NotNull Function<@NotNull VirtualFile, @NotNull ContentWithTimestamp> contentProvider) {
    ContentWithTimestamp contentAndStamps = contentProvider.apply(file);

    StoredContent content = contentAndStamps.content;
    long timestamp = contentAndStamps.timestamp;

    if (file instanceof VirtualFileSystemEntry) {
      return new FileEntry(((VirtualFileSystemEntry)file).getNameId(), content, timestamp, !file.isWritable());
    }
    return new FileEntry(file.getName(), content, timestamp, !file.isWritable());
  }

  private record DirectoryEntries(@NotNull DirectoryEntry first, @NotNull DirectoryEntry last) {
  }

  private static @Nullable DirectoryEntries doCreateDirectoryEntries(@NotNull VirtualFile file) {
    if (file.isInLocalFileSystem() || file.getParent() != null) {
      if (file instanceof VirtualFileSystemEntry) {
        int nameId = ((VirtualFileSystemEntry)file).getNameId();
        if (nameId > 0) {
          DirectoryEntry newDir = new DirectoryEntry(nameId);
          return new DirectoryEntries(newDir, newDir);
        }
      }
      DirectoryEntry newDir = new DirectoryEntry(file.getName());
      return new DirectoryEntries(newDir, newDir);
    }

    DirectoryEntry first = null;
    DirectoryEntry last = null;
    for (String item : Paths.split(file.getUrl())) {
      DirectoryEntry current = new DirectoryEntry(item);
      if (first == null) first = current;
      if (last != null) last.addChild(current);
      last = current;
    }

    if (first == null) return null;
    return new DirectoryEntries(first, last);
  }

  private void doCreateChildren(@NotNull DirectoryEntry parent, @NotNull Iterable<? extends VirtualFile> children, @NotNull Function<@NotNull VirtualFile, @NotNull ContentWithTimestamp> contentProvider,
                                @Nullable FileTreeVisitor visitor) {
    List<Entry> entries = ContainerUtil.mapNotNull(children, each -> {
      if (visitor != null && !visitor.before(each)) return null;

      Entry newEntry = null;
      Entry existingEntry = parent.findEntry(each.getName());
      if (existingEntry != null) {
        if (existingEntry instanceof DirectoryEntry existingDirectoryEntry) {
          doCreateChildren(existingDirectoryEntry, iterateDBChildren(each), contentProvider, visitor);
        }
      }
      else {
        newEntry = doCreateEntry(each, contentProvider, visitor);
      }

      if (visitor != null) visitor.after(each);

      return newEntry;
    });
    parent.addChildren(entries);
  }

  private interface FileTreeVisitor {
    boolean before(@NotNull VirtualFile file);

    void after(@NotNull VirtualFile file);
  }

  private static class SinglePathVisitor implements FileTreeVisitor {
    private final @NotNull List<String> myPathsStack = new ArrayList<>();
    private final boolean myIncludeChildren;

    private SinglePathVisitor(@NotNull String path, boolean includeChildren) {
      myIncludeChildren = includeChildren;
      myPathsStack.add(path);
    }

    @Override
    public boolean before(@NotNull VirtualFile child) {
      String targetPath = ContainerUtil.getLastItem(myPathsStack);
      if (targetPath == null) return false;
      if (myIncludeChildren && targetPath.isEmpty()) {
        myPathsStack.add("");
        return true;
      }

      String childName = getNameOrUrlPart(child);
      if (!targetPath.startsWith(childName)) return false;
      String targetPathRest = targetPath.substring(childName.length());
      if (!targetPathRest.isEmpty() && targetPathRest.charAt(0) != '/') return false;
      if (!targetPathRest.isEmpty() && targetPathRest.charAt(0) == '/') {
        targetPathRest = targetPathRest.substring(1);
      }

      myPathsStack.add(targetPathRest);
      return true;
    }

    @Override
    public void after(@NotNull VirtualFile file) {
      myPathsStack.remove(myPathsStack.size() - 1);
    }
  }

  private static class MergingPathVisitor implements FileTreeVisitor {
    private final List<? extends FileTreeVisitor> myVisitors;

    private final int[] myTerminatedAtDepth;
    private int myDepth = 1; // not 0 to simplify array initialization

    private MergingPathVisitor(@NotNull List<? extends FileTreeVisitor> visitors) {
      myVisitors = visitors;
      myTerminatedAtDepth = new int[visitors.size()];
    }

    @Override
    public boolean before(@NotNull VirtualFile file) {
      boolean result = false;
      for (int i = 0; i < myVisitors.size(); i++) {
        int terminatedAt = myTerminatedAtDepth[i];
        if (terminatedAt != 0) continue;

        if (myVisitors.get(i).before(file)) {
          result = true;
        }
        else {
          myTerminatedAtDepth[i] = myDepth;
        }
      }
      if (result) {
        myDepth++;
        return true;
      }
      else {
        for (int i = 0; i < myVisitors.size(); i++) {
          int terminatedAt = myTerminatedAtDepth[i];
          if (terminatedAt == myDepth) {
            myTerminatedAtDepth[i] = 0; // rollback because 'after' won't be called
          }
        }
        return false;
      }
    }

    @Override
    public void after(@NotNull VirtualFile file) {
      myDepth--;
      LOG.assertTrue(myDepth >= 1);

      for (int i = 0; i < myVisitors.size(); i++) {
        int terminatedAt = myTerminatedAtDepth[i];
        if (terminatedAt == myDepth) {
          myTerminatedAtDepth[i] = 0;
        }
        else if (terminatedAt == 0) {
          myVisitors.get(i).after(file);
        }
      }
    }
  }

  public void registerUnsavedDocuments(final @NotNull LocalHistoryFacade vcs) {
    ApplicationManager.getApplication().runReadAction(() -> {
      vcs.beginChangeSet();
      for (Document d : FileDocumentManager.getInstance().getUnsavedDocuments()) {
        VirtualFile f = getFile(d);
        if (!shouldRegisterDocument(f)) continue;
        registerDocumentContents(vcs, f, d);
      }
      vcs.endChangeSet(null, null);
    });
  }

  private boolean shouldRegisterDocument(@Nullable VirtualFile f) {
    try (AccessToken ignore = SlowOperations.knownIssue("IDEA-307668, EA-821075")) {
      return f != null && f.isValid() && areContentChangesVersioned(f);
    }
  }

  private void registerDocumentContents(@NotNull LocalHistoryFacade vcs, @NotNull VirtualFile f, @NotNull Document d) {
    ContentWithTimestamp contentAndStamp = acquireAndUpdateActualContent(f, d);
    if (contentAndStamp != null) {
      vcs.contentChanged(getPathOrUrl(f), contentAndStamp.content, contentAndStamp.timestamp);
    }
  }

  // returns null is content has not been changes since last time
  private static @Nullable ContentWithTimestamp acquireAndUpdateActualContent(@NotNull VirtualFile f, @NotNull Document d) {
    DocumentContentWithTimestamps contentAndStamp = f.getUserData(SAVED_DOCUMENT_CONTENT_AND_STAMP_KEY);
    if (contentAndStamp == null) {
      saveDocumentContent(f, d);
      return new ContentWithTimestamp(f.getTimeStamp(), StoredContent.acquireContent(f));
    }

    // if the stored content equals the current one, do not store it and return null
    if (d.getModificationStamp() == contentAndStamp.documentModificationStamp) return null;

    // is current content has been changed, store it and return the previous one
    saveDocumentContent(f, d);
    return contentAndStamp;
  }

  // returns null is content has not been changes since last time
  @Nullable ContentWithTimestamp acquireActualContentAndForgetSavedContent(@NotNull VirtualFile f, @Nullable Document d) {
    DocumentContentWithTimestamps contentAndStamp = f.getUserData(SAVED_DOCUMENT_CONTENT_AND_STAMP_KEY);
    if (contentAndStamp == null) {
      return new ContentWithTimestamp(f.getTimeStamp(), StoredContent.acquireContent(f));
    }
    f.putUserData(SAVED_DOCUMENT_CONTENT_AND_STAMP_KEY, null);
    if (d != null && d.getModificationStamp() == contentAndStamp.documentModificationStamp) return null;
    return contentAndStamp;
  }

  private static void saveDocumentContent(@NotNull VirtualFile f, @NotNull Document d) {
    f.putUserData(SAVED_DOCUMENT_CONTENT_AND_STAMP_KEY,
                  new DocumentContentWithTimestamps(Clock.getTime(),
                                                     StoredContent.acquireContent(bytesFromDocument(d)),
                                                     d.getModificationStamp()));
  }

  /**
   * @param contentFallback fallback in case if acquired content is unavailable
   *                        (see {@link StoredContent#acquireContent(VirtualFile)} implementation details)
   */
  @NotNull ContentWithTimestamp acquireContentForDeletedFile(@NotNull VirtualFile f,
                                                             @Nullable Supplier<? extends @NotNull StoredContent> contentFallback) {
    FileDocumentManager m = FileDocumentManager.getInstance();
    Document d = m.isFileModified(f) ? m.getCachedDocument(f) : null; // should not try to load document

    DocumentContentWithTimestamps contentAndStamp = f.getUserData(SAVED_DOCUMENT_CONTENT_AND_STAMP_KEY);
    f.putUserData(SAVED_DOCUMENT_CONTENT_AND_STAMP_KEY, null);

    if (d != null && contentAndStamp != null) {
      // if previously stored content was not changed, return it
      if (d.getModificationStamp() == contentAndStamp.documentModificationStamp) {
        return contentAndStamp;
      }
    }

    // release previously stored
    if (contentAndStamp != null) {
      contentAndStamp.content.release();
    }

    // take document's content if any
    if (d != null) {
      return new ContentWithTimestamp(Clock.getTime(), StoredContent.acquireContent(bytesFromDocument(d)));
    }

    StoredContent content = StoredContent.acquireContent(f);
    if (!content.isAvailable() && contentFallback != null) {
      content = contentFallback.get();
    }

    return new ContentWithTimestamp(f.getTimeStamp(), content);
  }

  private static @NotNull ContentWithTimestamp getActualContentNoAcquire(@NotNull VirtualFile f) {
    DocumentContentWithTimestamps result = f.getUserData(SAVED_DOCUMENT_CONTENT_AND_STAMP_KEY);
    if (result == null) {
      return new ContentWithTimestamp(f.getTimeStamp(), StoredContent.transientContent(f));
    }
    return result;
  }

  private static byte @NotNull [] bytesFromDocument(@NotNull Document d) {
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

  private static @Nullable VirtualFile getFile(@NotNull Document d) {
    return FileDocumentManager.getInstance().getFile(d);
  }

  public @Nullable Document getDocument(@NotNull String path) {
    VirtualFile file = findVirtualFile(path);
    if (file == null) return null;
    return FileDocumentManager.getInstance().getDocument(file);
  }

  public @NotNull FileType getFileType(@NotNull String fileName) {
    return FileTypeManager.getInstance().getFileTypeByFileName(fileName);
  }

  static class ContentWithTimestamp {
    public final long timestamp;
    public final StoredContent content;

    private ContentWithTimestamp(long timestamp, StoredContent content) {
      this.timestamp = timestamp;
      this.content = content;
    }
  }

  private static final class DocumentContentWithTimestamps extends ContentWithTimestamp {
    final long documentModificationStamp;

    private DocumentContentWithTimestamps(long registeredTimestamp, StoredContent content, long documentModificationStamp) {
      super(registeredTimestamp, content);
      this.documentModificationStamp = documentModificationStamp;
    }
  }
}
