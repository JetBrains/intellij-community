// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestUtil;

@PlatformTestCase.WrapInCommand
public class AddClassToFileTest extends PsiTestCase{
  public void test() throws Exception {
    VirtualFile root = createTestProjectStructure();
    PsiDirectory dir = myPsiManager.findDirectory(root);
    assertNotNull(dir);
    PsiFile file = WriteAction.compute(() -> dir.createFile("AAA.java"));
    PsiClass aClass = myJavaFacade.getElementFactory().createClass("AAA");
    ApplicationManager.getApplication().runWriteAction(() -> {
      file.add(aClass);
    });


    PsiTestUtil.checkFileStructure(file);
  }

  public void testFileModified() throws Exception {
    VirtualFile root = createTestProjectStructure();
    VirtualFile pkg = createChildDirectory(root, "foo");
    PsiDirectory dir = myPsiManager.findDirectory(pkg);
    assertNotNull(dir);
    String text = "package foo;\n\nclass A {}";
    PsiElement created = WriteAction
      .compute(() -> dir.add(PsiFileFactory.getInstance(getProject()).createFileFromText("A.java", JavaFileType.INSTANCE, text)));
    VirtualFile virtualFile = created.getContainingFile().getVirtualFile();
    assertNotNull(virtualFile);
    String fileText = LoadTextUtil.loadText(virtualFile).toString();
    assertEquals(text, fileText);

    Document doc = FileDocumentManager.getInstance().getDocument(virtualFile);
    assertNotNull(doc);
    assertFalse(FileDocumentManager.getInstance().isDocumentUnsaved(doc));
    assertFalse(FileDocumentManager.getInstance().isFileModified(virtualFile));
  }
}
