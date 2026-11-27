// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.heavyJavaCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class AddClassToFileTest {
  private final TestFixture<HeavyJavaCodeInsightFixture> heavyFixture = heavyJavaCodeInsightFixture(JavaTestUtil.getJavaTestDataPath());

  @Test
  public void test() throws Exception {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    VirtualFile root = fixture.createTestProjectStructure(null);
    PsiDirectory dir = ReadAction.computeBlocking(() -> fixture.getPsiManager().findDirectory(root));
    assertNotNull(dir);
    PsiFile file = WriteCommandAction.writeCommandAction(fixture.getProject()).compute(() -> dir.createFile("AAA.java"));
    PsiClass aClass = ReadAction.computeBlocking(() -> fixture.getJavaFacade().getElementFactory().createClass("AAA"));
    WriteCommandAction.writeCommandAction(fixture.getProject()).run(() -> {
      file.add(aClass);
    });


    ReadAction.runBlocking(() -> PsiTestUtil.checkFileStructure(file));
  }

  @Test
  public void testFileModified() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    VirtualFile root = fixture.createTestProjectStructure(null);
    VirtualFile pkg = VfsTestUtil.createDir(root, "foo");
    PsiDirectory dir = ReadAction.computeBlocking(() -> fixture.getPsiManager().findDirectory(pkg));
    assertNotNull(dir);
    String text = "package foo;\n\nclass A {}";
    PsiElement created = WriteCommandAction.writeCommandAction(fixture.getProject())
      .compute(() -> dir.add(PsiFileFactory.getInstance(fixture.getProject()).createFileFromText("A.java", JavaFileType.INSTANCE, text)));
    VirtualFile virtualFile = ReadAction.computeBlocking(() -> created.getContainingFile().getVirtualFile());
    assertNotNull(virtualFile);
    String fileText = LoadTextUtil.loadText(virtualFile).toString();
    assertEquals(text, fileText);

    Document doc = ReadAction.computeBlocking(() -> FileDocumentManager.getInstance().getDocument(virtualFile));
    assertNotNull(doc);
    assertFalse(FileDocumentManager.getInstance().isDocumentUnsaved(doc));
    assertFalse(FileDocumentManager.getInstance().isFileModified(virtualFile));
  }
}
