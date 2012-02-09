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

import java.lang.ref.Reference;

public class MockFileDocumentManagerImpl extends FileDocumentManager {
  private static final Key<VirtualFile> MOCK_VIRTUAL_FILE_KEY = Key.create("MockVirtualFile");
  private final Function<CharSequence, Document> myFactory;
  @Nullable private final Key<Reference<Document>> myCachedDocumentKey;

  public MockFileDocumentManagerImpl(Function<CharSequence, Document> factory, @Nullable Key<Reference<Document>> cachedDocumentKey) {
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
      Reference<Document> reference = file.getUserData(myCachedDocumentKey);
      return reference != null ? reference.get() : null;
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
  public void reloadFiles(final VirtualFile... files) {
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
