// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.core;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.testFramework.JavaCodeInsightFixtureKt.javaModuleCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public final class JavaPsiAnnotationUtilTest {
  private final TestFixture<JavaCodeInsightTestFixture> codeInsightFixture = javaModuleCodeInsightFixture(PathManager.getHomePath());

  @Test
  public void testProcessPackageAnnotationsTwoRoots() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    List<String> annoText = new ArrayList<>();
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
      try {
        VirtualFile tests = myFixture.getTempDirFixture().findOrCreateDir("tests");
        VirtualFile testsB = tests.createChildDirectory(this, "b");
        VirtualFile packageInfo = testsB.findOrCreateChildData(this, "package-info.java");
        PsiFile packageInfoPsiFile = PsiManager.getInstance(myFixture.getProject()).findFile(packageInfo);
        packageInfoPsiFile.getFileDocument().insertString(0, "@Anno package a.c.b;");
        PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
        ModuleRootModificationUtil.updateModel(myFixture.getModule(), model ->
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
