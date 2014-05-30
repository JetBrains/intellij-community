/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.mock;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.BinaryFileTypeDecompilers;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.WeakFactoryMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MockFileDocumentManagerImpl extends FileDocumentManager {
  private static final Key<VirtualFile> MOCK_VIRTUAL_FILE_KEY = Key.create("MockVirtualFile");
  private final Function<CharSequence, Document> myFactory;
  @Nullable private final Key<Document> myCachedDocumentKey;

  public MockFileDocumentManagerImpl(Function<CharSequence, Document> factory, @Nullable Key<Document> cachedDocumentKey) {
    myFactory = factory;
    myCachedDocumentKey = cachedDocumentKey;
  }

  private final WeakFactoryMap<VirtualFile,Document> myDocuments = new WeakFactoryMap<VirtualFile, Document>() {
    @Override
    protected Document create(final VirtualFile key) {
      if (key.isDirectory() || isBinaryWithoutDecompiler(key)) return null;

      CharSequence text = LoadTextUtil.loadText(key);
      final Document document = myFactory.fun(text);
      document.putUserData(MOCK_VIRTUAL_FILE_KEY, key);
      return document;
    }

    private boolean isBinaryWithoutDecompiler(VirtualFile file) {
      final FileType ft = file.getFileType();
      return ft.isBinary() && BinaryFileTypeDecompilers.INSTANCE.forFileType(ft) == null;
    }
  };

  @Override
  public Document getDocument(@NotNull VirtualFile file) {
    return myDocuments.get(file);
  }

  @Override
  public Document getCachedDocument(@NotNull VirtualFile file) {
    if (myCachedDocumentKey != null) {
      return file.getUserData(myCachedDocumentKey);
    }
    return null;
  }

  @Override
  public VirtualFile getFile(@NotNull Document document) {
    return document.getUserData(MOCK_VIRTUAL_FILE_KEY);
  }

  @Override
  public void saveAllDocuments() {
  }

  @Override
  public void saveDocument(@NotNull Document document) {
  }

  @Override
  public void saveDocumentAsIs(@NotNull Document document) {
  }

  @Override
  @NotNull
  public Document[] getUnsavedDocuments() {
    return new Document[0];
  }

  @Override
  public boolean isDocumentUnsaved(@NotNull Document document) {
    return false;
  }

  @Override
  public boolean isFileModified(@NotNull VirtualFile file) {
    return false;
  }

  @Override
  public void reloadFromDisk(@NotNull Document document) {
  }

  @Override
  public void reloadFiles(@NotNull final VirtualFile... files) {
  }

  @Override
  @NotNull
  public String getLineSeparator(VirtualFile file, Project project) {
    return "";
  }

  @Override
  public boolean requestWriting(@NotNull Document document, @Nullable Project project) {
    return true;
  }
}
