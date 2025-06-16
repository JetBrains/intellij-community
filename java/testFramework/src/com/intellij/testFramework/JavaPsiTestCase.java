// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

public abstract class JavaPsiTestCase extends JavaModuleTestCase {
  protected PsiManagerEx myPsiManager;
  protected PsiFile myFile;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myPsiManager = PsiManagerEx.getInstanceEx(myProject);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myPsiManager = null;
      myFile = null;
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  protected @NotNull PsiFile createDummyFile(@NotNull String fileName, @NotNull String text) throws IncorrectOperationException {
    FileType type = FileTypeRegistry.getInstance().getFileTypeByFileName(fileName);
    return PsiFileFactory.getInstance(myProject).createFileFromText(fileName, type, text);
  }

  protected @NotNull PsiFile createFile(@NonNls @NotNull String fileName, @NotNull String text) throws Exception {
    return createFile(myModule, fileName, text);
  }

  protected @NotNull PsiFile createFile(@NotNull Module module, @NotNull String fileName, @NotNull String text) throws Exception {
    return createFile(module, getTempDir().createVirtualDir(), fileName, text);
  }

  protected @NotNull PsiFile createFile(final @NotNull Module module, final @NotNull VirtualFile vDir, final @NotNull String fileName, final @NotNull String text)
    throws IOException {
    VirtualFile virtualFile = WriteAction.computeAndWait(() -> {
      if (!ModuleRootManager.getInstance(module).getFileIndex().isInSourceContent(vDir)) {
        addSourceContentToRoots(module, vDir);
      }

      VirtualFile vFile = Objects.requireNonNull(vDir.createChildData(vDir, fileName));
      VfsUtil.saveText(vFile, text);
      return vFile;
    });
    IndexingTestUtil.waitUntilIndexesAreReady(myProject);
    return Objects.requireNonNull(myPsiManager.findFile(virtualFile));
  }

  protected void addSourceContentToRoots(@NotNull Module module, @NotNull VirtualFile vDir) {
    PsiTestUtil.addSourceContentToRoots(module, vDir);
  }

  protected PsiElement configureByFileWithMarker(@NotNull String filePath, @NotNull String marker) throws Exception{
    final VirtualFile vFile = VfsTestUtil.findFileByCaseSensitivePath(filePath);

    String fileText = VfsUtilCore.loadText(vFile);
    fileText = StringUtil.convertLineSeparators(fileText);

    int offset = fileText.indexOf(marker);
    assertTrue(offset >= 0);
    fileText = fileText.substring(0, offset) + fileText.substring(offset + marker.length());

    myFile = createFile(vFile.getName(), fileText);

    return myFile.findElementAt(offset);
  }

  protected @NotNull String getTestDataPath() {
    return PathManagerEx.getTestDataPath();
  }

  protected @NotNull String loadFile(@NotNull String name) throws Exception {
    String result = FileUtil.loadFile(new File(getTestDataPath() + File.separatorChar + name));
    return StringUtil.convertLineSeparators(result);
  }

  public PsiFile getFile() {
    return myFile;
  }

  public Document getDocument(@NotNull PsiFile file) {
    return PsiDocumentManager.getInstance(getProject()).getDocument(file);
  }

  public Document getDocument(@NotNull VirtualFile file) {
    return FileDocumentManager.getInstance().getDocument(file);
  }

  public void commitDocument(@NotNull Document document) {
    PsiDocumentManager.getInstance(getProject()).commitDocument(document);
  }
}
