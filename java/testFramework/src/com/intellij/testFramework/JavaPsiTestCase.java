// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.util.IncorrectOperationException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.StringTokenizer;

public abstract class JavaPsiTestCase extends JavaModuleTestCase {
  protected PsiManagerImpl myPsiManager;
  protected PsiFile myFile;
  private String myDataRoot;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myPsiManager = (PsiManagerImpl)PsiManager.getInstance(myProject);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myPsiManager = null;
      myFile = null;
      myTestDataBefore = null;
      myTestDataAfter = null;
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  @NotNull
  protected PsiFile createDummyFile(@NotNull String fileName, @NotNull String text) throws IncorrectOperationException {
    FileType type = FileTypeRegistry.getInstance().getFileTypeByFileName(fileName);
    return PsiFileFactory.getInstance(myProject).createFileFromText(fileName, type, text);
  }

  @NotNull
  protected PsiFile createFile(@NonNls @NotNull String fileName, @NotNull String text) throws Exception {
    return createFile(myModule, fileName, text);
  }

  @NotNull
  protected PsiFile createFile(@NotNull Module module, @NotNull String fileName, @NotNull String text) throws Exception {
    return createFile(module, getTempDir().createVirtualDir(), fileName, text);
  }

  @NotNull
  protected PsiFile createFile(@NotNull final Module module, @NotNull final VirtualFile vDir, @NotNull final String fileName, @NotNull final String text)
    throws IOException {
    VirtualFile virtualFile = WriteAction.computeAndWait(() -> {
      if (!ModuleRootManager.getInstance(module).getFileIndex().isInSourceContent(vDir)) {
        addSourceContentToRoots(module, vDir);
      }

      VirtualFile vFile = Objects.requireNonNull(vDir.createChildData(vDir, fileName));
      VfsUtil.saveText(vFile, text);
      return vFile;
    });
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

  /**
   * @deprecated use other methods to configure the files, data.xml files aren't supported anymore
   */
  @Deprecated
  protected void configure(@NotNull String path, String dataName) throws Exception {
    myDataRoot = getTestDataPath() + path;

    myTestDataBefore = loadData(dataName);

    PsiTestUtil.removeAllRoots(myModule, IdeaTestUtil.getMockJdk17());
    VirtualFile vDir = createTestProjectStructure(myModule, myDataRoot, true, getTempDir());

    VirtualFile vFile = vDir.findChild(myTestDataBefore.getTextFile());
    myFile = myPsiManager.findFile(Objects.requireNonNull(vFile));
  }

  @NotNull
  protected String getTestDataPath() {
    return PathManagerEx.getTestDataPath();
  }

  @NotNull
  protected String loadFile(@NotNull String name) throws Exception {
    String result = FileUtil.loadFile(new File(getTestDataPath() + File.separatorChar + name));
    return StringUtil.convertLineSeparators(result);
  }

  @NotNull
  private PsiTestData loadData(String dataName) throws Exception {
    PsiTestData data = createData();
    Element documentElement = JDOMUtil.load(Paths.get(myDataRoot, "data.xml"));
    for (Element node : documentElement.getChildren("data")) {
      String value = node.getAttributeValue("name");
      if (value.equals(dataName)) {
        DefaultJDOMExternalizer.readExternal(data, node);
        data.loadText(myDataRoot);

        return data;
      }
    }

    throw new IllegalArgumentException("Cannot find data chunk '" + dataName + "'");
  }

  /**
   * @deprecated use other methods to configure the files, data.xml files aren't supported anymore
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  @NotNull
  protected PsiTestData createData() {
    return new PsiTestData();
  }

  /**
   * @deprecated use other methods to configure the files, data.xml files aren't supported anymore
   */
  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  @Deprecated
  protected void checkResult(String dataName) throws Exception {
    myTestDataAfter = loadData(dataName);

    final String textExpected = myTestDataAfter.getText();
    final String actualText = myFile.getText();

    if (!textExpected.equals(actualText)) {
      System.out.println("Text mismatch: " + getName() + "(" + getClass().getName() + ")");
      System.out.println("Text expected:");
      printText(textExpected);
      System.out.println("Text found:");
      printText(actualText);

      fail("text");
    }

//    assertEquals(myTestDataAfter.getText(), myFile.getText());
  }

  /**
   * @deprecated printing text to {@code System.out} is discouraged, use other methods instead 
   */
  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  @Deprecated
  protected static void printText(@NotNull String text) {
    final String q = "\"";
    System.out.print(q);

    text = StringUtil.convertLineSeparators(text);

    StringTokenizer tokenizer = new StringTokenizer(text, "\n", true);
    while (tokenizer.hasMoreTokens()) {
      final String token = tokenizer.nextToken();

      if (token.equals("\n")) {
        System.out.print(q);
        System.out.println();
        System.out.print(q);
        continue;
      }

      System.out.print(token);
    }

    System.out.print(q);
    System.out.println();
  }

  /**
   * @deprecated use {@link ModuleRootModificationUtil#addModuleLibrary} directly instead
   */
  @Deprecated
  protected void addLibraryToRoots(@NotNull VirtualFile jarFile, @NotNull OrderRootType rootType) {
    addLibraryToRoots(myModule, jarFile, rootType);
  }

  /**
   * @deprecated use {@link ModuleRootModificationUtil#addModuleLibrary} directly instead
   */
  @Deprecated
  protected static void addLibraryToRoots(@NotNull Module module, @NotNull VirtualFile root, @NotNull OrderRootType rootType) {
    assertEquals(OrderRootType.CLASSES, rootType);
    ModuleRootModificationUtil.addModuleLibrary(module, root.getUrl());
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

  /**
   * @deprecated use other methods to configure the files, data.xml files aren't supported anymore
   */
  @SuppressWarnings("DeprecatedIsStillUsed") 
  @Deprecated
  protected PsiTestData myTestDataBefore;
  
  /**
   * @deprecated use other methods to configure the files, data.xml files aren't supported anymore
   */
  @SuppressWarnings("DeprecatedIsStillUsed") 
  @Deprecated
  protected PsiTestData myTestDataAfter;
}
