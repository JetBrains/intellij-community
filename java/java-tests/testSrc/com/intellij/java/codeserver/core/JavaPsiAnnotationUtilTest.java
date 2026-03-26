// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.core;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

public final class JavaPsiAnnotationUtilTest extends JavaCodeInsightFixtureTestCase {
  public void testProcessPackageAnnotationsTwoRoots() {
    List<String> annoText = new ArrayList<>();
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      try {
        VirtualFile tests = myFixture.getTempDirFixture().findOrCreateDir("tests");
        VirtualFile testsB = tests.createChildDirectory(this, "b");
        VirtualFile packageInfo = testsB.findOrCreateChildData(this, "package-info.java");
        PsiFile packageInfoPsiFile = PsiManager.getInstance(getProject()).findFile(packageInfo);
        packageInfoPsiFile.getFileDocument().insertString(0, "@Anno package a.c.b;");
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        ModuleRootModificationUtil.updateModel(getModule(), model ->
          model.getContentEntries()[0].addSourceFolder(tests, true, "a.c"));
        PsiFile file = myFixture.addFileToProject("a/c/b/Test.java", "package a.c.b; public class Test{}");
        JavaPsiAnnotationUtil.processPackageAnnotations(file, (annotations, superPackage) -> {
          annotations.forEach(a -> annoText.add(a.getText()));
        }, false);
      }
      catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    });
    assertEquals(List.of("@Anno"), annoText);
  }
}
