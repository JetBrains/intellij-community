// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

import java.io.IOException;

public class RenameDirectoryTest extends JavaCodeInsightFixtureTestCase {
  public void testRenameSrcRootWithTextOccurrences() throws IOException {
    VirtualFile srcRoot = myFixture.getTempDirFixture().findOrCreateDir("src2");
    PsiTestUtil.removeAllRoots(getModule(), null);
    PsiTestUtil.addSourceRoot(getModule(), srcRoot);

    PsiClass fooClass = myFixture.addClass("""
                                             // PsiPackage:
                                             class Foo {
                                               String s1 = "PsiPackage:"
                                             }
                                             """);
    myFixture.configureFromExistingVirtualFile(fooClass.getContainingFile().getVirtualFile());

    new RenameProcessor(getProject(), getPsiManager().findDirectory(srcRoot), "newName", true, true).run();

    assert srcRoot.getPath().endsWith("newName");
    myFixture.checkResult("""
                            // PsiPackage:
                            class Foo {
                              String s1 = "PsiPackage:"
                            }
                            """);
  }
}
