/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.intention;

import com.intellij.codeInsight.intention.impl.CreateSubclassAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.search.ProjectScope;
import com.intellij.refactoring.MultiFileTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class CreateSubclassTest extends MultiFileTestCase {
  public void testGenerics() throws Exception {
    doTest();
  }

  public void testInnerClassImplement() throws Exception {
    doTestInner();
  }

  public void testInnerClass() throws Exception {
    doTestInner();
  }

  private void doTestInner() throws Exception {
    doTest(new PerformAction() {
      @Override
      public void performAction(final VirtualFile rootDir, final VirtualFile rootAfter) throws Exception {
        PsiClass superClass = myJavaFacade.findClass("Test", ProjectScope.getAllScope(myProject));
        assertNotNull(superClass);
        final PsiClass inner = superClass.findInnerClassByName("Inner", false);
        assertNotNull(inner);
        CreateSubclassAction.createInnerClass(inner);
      }
    });
  }

  private void doTest() throws Exception {
    doTest(new PerformAction() {
      @Override
      public void performAction(final VirtualFile rootDir, final VirtualFile rootAfter) throws Exception {
        PsiDirectory root = myPsiManager.findDirectory(rootDir);
        PsiClass superClass = myJavaFacade.findClass("Superclass", ProjectScope.getAllScope(myProject));
        CreateSubclassAction.createSubclass(superClass, root, "Subclass");
      }
    });
  }

  @NotNull
  @Override
  protected String getTestRoot() {
    return "/codeInsight/createSubclass/";
  }
}
