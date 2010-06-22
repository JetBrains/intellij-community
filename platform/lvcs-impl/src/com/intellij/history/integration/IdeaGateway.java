/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.history.integration;

import com.intellij.history.Clock;
import com.intellij.history.core.LocalHistoryFacade;
import com.intellij.history.core.Paths;
import com.intellij.history.core.storage.Content;
import com.intellij.history.core.storage.StoredContent;
import com.intellij.history.core.tree.DirectoryEntry;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.core.tree.FileEntry;
import com.intellij.history.core.tree.RootEntry;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class IdeaGateway {
  private static final Key<Pair<StoredContent, Long>> SAVED_DOCUMENT_CONTENT_AND_STAMP_KEY
    = Key.create("LocalHistory.SAVED_DOCUMENT_CONTENT_AND_STAMP_KEY");

  public boolean isVersioned(VirtualFile f) {
    if (!f.isInLocalFileSystem()) return false;

    String fileName = f.getName();
    if (!f.isDirectory() && fileName.endsWith(".class")) return false;

    for (Project each : ProjectManager.getInstance().getOpenProjects()) {
      if (each.isDefault()) continue;
      if (each.getWorkspaceFile() == f) return false;
      if (ProjectRootManager.getInstance(each).getFileIndex().isIgnored(f)) return false;
    }

    return !FileTypeManager.getInstance().isFileIgnored(fileName);
  }

  public boolean areContentChangesVersioned(VirtualFile f) {
    if (!isVersioned(f) || f.isDirectory()) return false;
    return areContentChangesVersioned(f.getName());
  }

  public boolean areContentChangesVersioned(String fileName) {
    return !FileTypeManager.getInstance().getFileTypeByFileName(fileName).isBinary();
  }

  public boolean ensureFilesAreWritable(Project p, List<VirtualFile> ff) {
    ReadonlyStatusHandler h = ReadonlyStatusHandler.getInstance(p);
    return !h.ensureFilesWritable(ff.toArray(new VirtualFile[ff.size()])).hasReadonlyFiles();
  }

  @Nullable
  public VirtualFile findVirtualFile(String path) {
    return LocalFileSystem.getInstance().findFileByPath(path);
  }

  @NotNull
  public VirtualFile findOrCreateFileSafely(VirtualFile parent, String name, boolean isDirectory) throws IOException {
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
  public VirtualFile findOrCreateFileSafely(String path, boolean isDirectory) throws IOException {
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

  public List<VirtualFile> getAllFilesFrom(String path) {
    VirtualFile f = findVirtualFile(path);
    if (f == null) return Collections.emptyList();
    return collectFiles(f, new ArrayList<VirtualFile>());
  }

  private List<VirtualFile> collectFiles(VirtualFile f, List<VirtualFile> result) {
    if (f.isDirectory()) {
      for (VirtualFile child : ((NewVirtualFile)f).iterInDbChildren()) {
        collectFiles(child, result);
      }
    }
    else {
      result.add(f);
    }
    return result;
  }

  public RootEntry createTransientRootEntry() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    RootEntry root = new RootEntry();
    doCreateChildren(root, Arrays.asList(ManagingFS.getInstance().getLocalRoots()), false);
    return root;
  }

  @Nullable
  public Entry createTransientEntry(VirtualFile file) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    return doCreateEntry(file, false);
  }

  @Nullable
  public Entry createEntryForDeletion(VirtualFile file) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    return doCreateEntry(file, true);
  }

  @Nullable
  private Entry doCreateEntry(VirtualFile file, boolean forDeletion) {
    if (!file.isDirectory()) {
      if (!isVersioned(file)) return null;

      Content c;
      if (forDeletion) {
        FileDocumentManager m = FileDocumentManager.getInstance();
        Document d = m.getCachedDocument(file); // should not try to load document
        if (d != null && !m.isDocumentUnsaved(d)) d = null;
        c = acquireAndClearCurrentContent(file, d);
      }
      else {
        c = getActualContentNoAcquire(file);
      }
      return new FileEntry(file.getName(), c, file.getTimeStamp(), !file.isWritable());
    }
    DirectoryEntry newDir = new DirectoryEntry(file.getName());
    doCreateChildren(newDir, ((NewVirtualFile)file).iterInDbChildren(), forDeletion);
    if (!isVersioned(file) && newDir.getChildren().isEmpty()) return null;
    return newDir;
  }

  private void doCreateChildren(DirectoryEntry parent, Iterable<VirtualFile> children, boolean forDeletion) {
    for (VirtualFile each : children) {
      Entry child = doCreateEntry(each, forDeletion);
      if (child != null) parent.addChild(child);
    }
  }

  public void registerUnsavedDocuments(final LocalHistoryFacade vcs) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        vcs.beginChangeSet();
        for (Document d : FileDocumentManager.getInstance().getUnsavedDocuments()) {
          VirtualFile f = getFile(d);
          if (!shouldRegisterDocument(f)) continue;
          registerDocumentContents(vcs, f, d);
          registerDocumentContents(vcs, f, d);
        }
        vcs.endChangeSet(null);
      }
    });
  }

  private boolean shouldRegisterDocument(VirtualFile f) {
    if (f == null || !f.isValid()) return false;
    return areContentChangesVersioned(f);
  }

  private void registerDocumentContents(LocalHistoryFacade vcs, VirtualFile f, Document d) {
    Content content = acquireAndUpdateActualContent(f, d);
    if (content != null) {
      vcs.contentChanged(f.getPath(), content, Clock.getCurrentTimestamp());
    }
  }

  // returns null is content has not been changes since last time
  @Nullable
  public Content acquireAndUpdateActualContent(VirtualFile f, @Nullable Document d) {
    Pair<StoredContent, Long> contentAndStamp = f.getUserData(SAVED_DOCUMENT_CONTENT_AND_STAMP_KEY);
    if (contentAndStamp == null) {
      if (d != null) saveDocumentContent(f, d);
      return StoredContent.acquireContent(f);
    }

    StoredContent content = contentAndStamp.first;

    // if no need to save current document content when simply return and clear stored one
    if (d == null) {
      f.putUserData(SAVED_DOCUMENT_CONTENT_AND_STAMP_KEY, null);
      return content;
    }

    // if the stored content equals the current one, do not store it and return null
    if (d.getModificationStamp() == contentAndStamp.second) return null;

    // is current content has been changed, store it and return the previous one
    saveDocumentContent(f, d);
    return content;
  }

  private void saveDocumentContent(VirtualFile f, Document d) {
    f.putUserData(SAVED_DOCUMENT_CONTENT_AND_STAMP_KEY,
                  Pair.create(StoredContent.acquireContent(bytesFromDocument(d)), d.getModificationStamp()));
  }

  public Content acquireAndClearCurrentContent(VirtualFile f, @Nullable Document d) {
    Pair<StoredContent, Long> contentAndStamp = f.getUserData(SAVED_DOCUMENT_CONTENT_AND_STAMP_KEY);
    f.putUserData(SAVED_DOCUMENT_CONTENT_AND_STAMP_KEY, null);

    if (d != null && contentAndStamp != null) {
      // if previously stored content was not changed, return it
      if (d.getModificationStamp() == contentAndStamp.second) return contentAndStamp.first;
    }

    // release previously stored
    if (contentAndStamp != null) {
      contentAndStamp.first.release();
    }

    // take document's content if any
    if (d != null) {
      return StoredContent.acquireContent(bytesFromDocument(d));
    }

    return StoredContent.acquireContent(f);
  }

  private Content getActualContentNoAcquire(VirtualFile f) {
    Pair<StoredContent, Long> result = f.getUserData(SAVED_DOCUMENT_CONTENT_AND_STAMP_KEY);
    if (result == null) {
      return StoredContent.transientContent(f);
    }
    return result.first;
  }

  private byte[] bytesFromDocument(Document d) {
    try {
      return d.getText().getBytes(getFile(d).getCharset().name());
    }
    catch (UnsupportedEncodingException e) {
      return d.getText().getBytes();
    }
  }

  public String stringFromBytes(byte[] bytes, String path) {
    try {
      VirtualFile file = findVirtualFile(path);
      if (file == null) {
        return CharsetToolkit.bytesToString(bytes);
      }
      return new String(bytes, file.getCharset().name());
    }
    catch (UnsupportedEncodingException e1) {
      return new String(bytes);
    }
  }

  public void saveAllUnsavedDocuments() {
    FileDocumentManager.getInstance().saveAllDocuments();
  }

  private VirtualFile getFile(Document d) {
    return FileDocumentManager.getInstance().getFile(d);
  }

  public Document getDocument(String path) {
    return FileDocumentManager.getInstance().getDocument(findVirtualFile(path));
  }

  public FileType getFileType(String fileName) {
    return FileTypeManager.getInstance().getFileTypeByFileName(fileName);
  }
}
