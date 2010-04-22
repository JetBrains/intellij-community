/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.refactoring.rename.naming;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageInfo;

import java.util.Collection;

/**
 * @author yole
 */
public class AutomaticTestRenamerFactory implements AutomaticRenamerFactory {
  public boolean isApplicable(final PsiElement element) {
    return element instanceof PsiClass;
  }

  public String getOptionName() {
    return RefactoringBundle.message("rename.tests");
  }

  public boolean isEnabled() {
    return JavaRefactoringSettings.getInstance().isToRenameTests();
  }

  public void setEnabled(final boolean enabled) {
    JavaRefactoringSettings.getInstance().setRenameTests(enabled);
  }

  public AutomaticRenamer createRenamer(final PsiElement element, final String newName, final Collection<UsageInfo> usages) {
    return new TestsRenamer((PsiClass)element, newName);
  }

  private static class TestsRenamer extends AutomaticRenamer {
    public TestsRenamer(PsiClass aClass, String newClassName) {

      appendTestClass(aClass, "Test");
      appendTestClass(aClass, "TestCase");

      suggestAllNames(aClass.getName(), newClassName);
    }

    private void appendTestClass(PsiClass aClass, String testSuffix) {
      final Project project = aClass.getProject();
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
      final PsiClass psiClassTest = facade.findClass(aClass.getQualifiedName() + testSuffix, GlobalSearchScope.projectScope(project));
      if (psiClassTest != null) {
        myElements.add(psiClassTest);
      }
    }

    public String getDialogTitle() {
      return RefactoringBundle.message("rename.tests.title");
    }

    public String getDialogDescription() {
      return RefactoringBundle.message("rename.tests.with.the.following.names.to");
    }

    public String entityName() {
      return RefactoringBundle.message("entity.name.test");
    }
  }
}
