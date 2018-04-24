// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring.inline;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.search.ProjectScope;
import com.intellij.refactoring.MockInlineMethodOptions;
import com.intellij.refactoring.RefactoringTestCase;
import com.intellij.refactoring.inline.InlineMethodProcessor;
import com.intellij.refactoring.inline.InlineOptions;
import com.intellij.refactoring.util.InlineUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;

import java.io.File;


public class InlineMethodMultifileTest extends RefactoringTestCase {

  private String getRoot() {
    return JavaTestUtil.getJavaTestDataPath() + "/refactoring/inlineMethod/multifile/" + getTestName(true);
  }

  public void testRemoveStaticImports() throws Exception {
    doTest("Foo", "foo");
  }
  public void testPreserveStaticImportsIfOverloaded() throws Exception {
    doTest("Foo", "foo");
  }

  public void testDecodeQualifierInMethodReference() throws Exception {
    doTest("Foo", "foo");
  }

  private void doTest(String className, String methodName) throws Exception {
    String rootBefore = getRoot() + "/before";
    PsiTestUtil.removeAllRoots(myModule, IdeaTestUtil.getMockJdk17());
    final VirtualFile rootDir = createTestProjectStructure(rootBefore);
    PsiClass aClass = myJavaFacade.findClass(className, ProjectScope.getAllScope(myProject));
    assertNotNull(aClass);
    PsiMethod method = aClass.findMethodsByName(methodName, false)[0];
    final boolean condition = InlineMethodProcessor.checkBadReturns(method) && !InlineUtil.allUsagesAreTailCalls(method);
    assertFalse("Bad returns found", condition);

    InlineOptions options = new MockInlineMethodOptions();
    final InlineMethodProcessor processor = new InlineMethodProcessor(getProject(), method, null, myEditor, options.isInlineThisOnly());
    processor.run();

    String rootAfter = getRoot() + "/after";
    VirtualFile rootDir2 = LocalFileSystem.getInstance().findFileByPath(rootAfter.replace(File.separatorChar, '/'));
    myProject.getComponent(PostprocessReformattingAspect.class).doPostponedFormatting();
    PlatformTestUtil.assertDirectoriesEqual(rootDir2, rootDir);
  }
}
