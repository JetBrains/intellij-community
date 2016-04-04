/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.testFramework;

import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.JdomKt;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.StringTokenizer;

/**
 * @author Mike
 */
public abstract class PsiTestCase extends ModuleTestCase {
  protected PsiManagerImpl myPsiManager;
  protected PsiFile myFile;
  protected PsiTestData myTestDataBefore;
  protected PsiTestData myTestDataAfter;
  private String myDataRoot;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myPsiManager = (PsiManagerImpl) PsiManager.getInstance(myProject);
  }

  @Override
  protected void tearDown() throws Exception {
    myPsiManager = null;
    myFile = null;
    myTestDataBefore = null;
    myTestDataAfter = null;
    super.tearDown();
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
    File dir = createTempDirectory();
    VirtualFile vDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(dir.getCanonicalPath().replace(File.separatorChar, '/'));

    assert vDir != null : dir;
    return createFile(module, vDir, fileName, text);
  }

  @NotNull
  protected PsiFile createFile(@NotNull final Module module, @NotNull final VirtualFile vDir, @NotNull final String fileName, @NotNull final String text) throws IOException {
    return new WriteAction<PsiFile>() {
      @Override
      protected void run(@NotNull Result<PsiFile> result) throws Throwable {
        if (!ModuleRootManager.getInstance(module).getFileIndex().isInSourceContent(vDir)) {
          addSourceContentToRoots(module, vDir);
        }

        final VirtualFile vFile = vDir.createChildData(vDir, fileName);
        VfsUtil.saveText(vFile, text);
        assertNotNull(vFile);
        final PsiFile file = myPsiManager.findFile(vFile);
        assertNotNull(file);
        result.setResult(file);
      }
    }.execute().getResultObject();
  }

  protected void addSourceContentToRoots(final Module module, final VirtualFile vDir) {
    PsiTestUtil.addSourceContentToRoots(module, vDir);
  }

  protected PsiElement configureByFileWithMarker(String filePath, String marker) throws Exception{
    final VirtualFile vFile = VfsTestUtil.findFileByCaseSensitivePath(filePath);

    String fileText = VfsUtil.loadText(vFile);
    fileText = StringUtil.convertLineSeparators(fileText);

    int offset = fileText.indexOf(marker);
    assertTrue(offset >= 0);
    fileText = fileText.substring(0, offset) + fileText.substring(offset + marker.length());

    myFile = createFile(vFile.getName(), fileText);

    return myFile.findElementAt(offset);
  }

  protected void configure(@NotNull String path, String dataName) throws Exception {
    myDataRoot = getTestDataPath() + path;

    myTestDataBefore = loadData(dataName);

    PsiTestUtil.removeAllRoots(myModule, IdeaTestUtil.getMockJdk17());
    VirtualFile vDir = PsiTestUtil.createTestProjectStructure(myProject, myModule, myDataRoot, myFilesToDelete);

    final VirtualFile vFile = vDir.findChild(myTestDataBefore.getTextFile());
    myFile = myPsiManager.findFile(vFile);
  }

  protected String getTestDataPath() {
    return PathManagerEx.getTestDataPath();
  }

  protected String loadFile(String name) throws Exception {
    String result = FileUtil.loadFile(new File(getTestDataPath() + File.separatorChar + name));
    return StringUtil.convertLineSeparators(result);
  }

  private PsiTestData loadData(String dataName) throws Exception {
    PsiTestData data = createData();
    Element documentElement = JdomKt.loadElement(Paths.get(myDataRoot, "data.xml"));
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

  protected PsiTestData createData() {
    return new PsiTestData();
  }

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

  protected static void printText(String text) {
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

  protected void addLibraryToRoots(final VirtualFile jarFile, OrderRootType rootType) {
    addLibraryToRoots(myModule, jarFile, rootType);
  }

  protected static void addLibraryToRoots(final Module module, final VirtualFile root, final OrderRootType rootType) {
    assertEquals(OrderRootType.CLASSES, rootType);
    ModuleRootModificationUtil.addModuleLibrary(module, root.getUrl());
  }


  public PsiFile getFile() {
    return myFile;
  }

  public com.intellij.openapi.editor.Document getDocument(PsiFile file) {
    return PsiDocumentManager.getInstance(getProject()).getDocument(file);
  }

  public com.intellij.openapi.editor.Document getDocument(VirtualFile file) {
    return FileDocumentManager.getInstance().getDocument(file);
  }

  public void commitDocument(com.intellij.openapi.editor.Document document) {
    PsiDocumentManager.getInstance(getProject()).commitDocument(document);
  }
}
