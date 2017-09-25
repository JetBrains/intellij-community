/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.MultiFileTestCase;
import com.intellij.refactoring.removemiddleman.DelegationUtils;
import com.intellij.refactoring.removemiddleman.RemoveMiddlemanProcessor;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class RemoveMiddleManTest extends MultiFileTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  @NotNull
  @Override
  protected String getTestRoot() {
    return "/refactoring/removemiddleman/";
  }

  private void doTest(final String conflict) {
    doTest((rootDir, rootAfter) -> {
      PsiClass aClass = myJavaFacade.findClass("Test", GlobalSearchScope.allScope(getProject()));

      if (aClass == null) aClass = myJavaFacade.findClass("p.Test", GlobalSearchScope.allScope(getProject()));
      assertNotNull("Class Test not found", aClass);

      final PsiField field = aClass.findFieldByName("myField", false);
      final Set<PsiMethod> methods = DelegationUtils.getDelegatingMethodsForField(field);
      List<MemberInfo> infos = new ArrayList<>();
      for (PsiMethod method : methods) {
        final MemberInfo info = new MemberInfo(method);
        info.setChecked(true);
        info.setToAbstract(true);
        infos.add(info);
      }
      try {
        RemoveMiddlemanProcessor processor = new RemoveMiddlemanProcessor(field, infos);
        processor.run();
        LocalFileSystem.getInstance().refresh(false);
        FileDocumentManager.getInstance().saveAllDocuments();
        if (conflict != null) fail("Conflict expected");
      }
      catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
        if (conflict == null) throw e;
        assertEquals(conflict, e.getMessage());
      }
    });
  }

  public void testNoGetter() {
    doTest((String)null);
  }

  public void testSiblings() {
    doTest("foo() will be deleted. Hierarchy will be broken");
  }

  
  public void testInterface() {
    doTest("foo() will be deleted. Hierarchy will be broken");
  }

  public void testPresentGetter() {
    doTest("foo() will be deleted. Hierarchy will be broken");
  }

  public void testInterfaceDelegation() {
    doTest("foo() will be deleted. Hierarchy will be broken");
  }
}