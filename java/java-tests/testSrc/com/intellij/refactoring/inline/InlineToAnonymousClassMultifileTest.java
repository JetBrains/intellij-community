/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.refactoring.inline;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.search.ProjectScope;
import com.intellij.refactoring.RefactoringTestCase;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.MultiMap;

import java.io.File;

/**
 * @author yole
 */
public class InlineToAnonymousClassMultifileTest extends RefactoringTestCase {
  public void testProtectedMember() throws Exception {   // IDEADEV-18738
    doTest("p1.SubjectWithSuper");
  }

  public void testImportForConstructor() throws Exception {   // IDEADEV-18714
    doTest("p1.ChildCtor");
  }

  public void testStaticImports() throws Exception {   // IDEADEV-18745
    doTest("p1.Inlined");
  }

  private String getRoot() {
    return JavaTestUtil.getJavaTestDataPath() + "/refactoring/inlineToAnonymousClass/multifile/" + getTestName(true);
  }

  private void doTest(String className) throws Exception {
    String rootBefore = getRoot() + "/before";
    PsiTestUtil.removeAllRoots(myModule, IdeaTestUtil.getMockJdk17());
    final VirtualFile rootDir = PsiTestUtil.createTestProjectStructure(myProject, myModule, rootBefore, myFilesToDelete);
    PsiClass classToInline = myJavaFacade.findClass(className, ProjectScope.getAllScope(myProject));
    assertEquals(null, InlineToAnonymousClassHandler.getCannotInlineMessage(classToInline));
    InlineToAnonymousClassProcessor processor = new InlineToAnonymousClassProcessor(myProject, 
                                                                                    classToInline,
                                                                                    null, false, false, false);
    UsageInfo[] usages = processor.findUsages();
    MultiMap<PsiElement,String> conflicts = processor.getConflicts(usages);
    assertEquals(0, conflicts.size());
    processor.run();

    String rootAfter = getRoot() + "/after";
    VirtualFile rootDir2 = LocalFileSystem.getInstance().findFileByPath(rootAfter.replace(File.separatorChar, '/'));
    myProject.getComponent(PostprocessReformattingAspect.class).doPostponedFormatting();
    PlatformTestUtil.assertDirectoriesEqual(rootDir2, rootDir);
  }
}
