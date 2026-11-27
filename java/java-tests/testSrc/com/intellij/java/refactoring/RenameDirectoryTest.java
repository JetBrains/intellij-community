// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.intellij.testFramework.JavaCodeInsightFixtureKt.javaModuleCodeInsightFixture;

@TestApplication
public class RenameDirectoryTest {
  protected final TestFixture<JavaCodeInsightTestFixture> codeInsightFixture = javaModuleCodeInsightFixture(PathManager.getHomePath());

  @Test
  public void testRenameSrcRootWithTextOccurrences() throws IOException {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    VirtualFile srcRoot = myFixture.getTempDirFixture().findOrCreateDir("src2");
    PsiTestUtil.removeAllRoots(myFixture.getModule(), null);
    PsiTestUtil.addSourceRoot(myFixture.getModule(), srcRoot);

    PsiClass fooClass = myFixture.addClass("""
                                             // PsiPackage:
                                             class Foo {
                                               String s1 = "PsiPackage:"
                                             }
                                             """);
    myFixture.configureFromExistingVirtualFile(fooClass.getContainingFile().getVirtualFile());

    EdtTestUtil.runInEdtAndWait(() -> rename(PsiManager.getInstance(myFixture.getProject()).findDirectory(srcRoot), "newName"));

    assert srcRoot.getPath().endsWith("newName");
    myFixture.checkResult("""
                            // PsiPackage:
                            class Foo {
                              String s1 = "PsiPackage:"
                            }
                            """);
  }

  /** Renames {@code element}, searching in comments and in text occurrences; a subclass renames with another engine. */
  protected void rename(@NotNull PsiElement element, @NotNull String newName) {
    new RenameProcessor(codeInsightFixture.get().getProject(), element, newName, true, true).run();
  }
}
