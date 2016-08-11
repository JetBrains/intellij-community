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

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.HashSet;

import java.util.Collection;
import java.util.regex.Pattern;

/**
 * @author yole
 */
public class AutomaticTestRenamerFactory implements AutomaticRenamerFactory {
  public boolean isApplicable(final PsiElement element) {
    if (element instanceof PsiClass) {
      final String qualifiedName = ((PsiClass)element).getQualifiedName();
      if (qualifiedName != null) {
        return !qualifiedName.endsWith("Test") && !qualifiedName.endsWith("TestCase");
      }
    }
    return false;
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

      final Module module = ModuleUtilCore.findModuleForPsiElement(aClass);
      if (module != null) {
        final GlobalSearchScope moduleScope = GlobalSearchScope.moduleWithDependentsScope(module);

        appendTestClass(aClass, "Test", moduleScope);
        appendTestClass(aClass, "TestCase", moduleScope);

        suggestAllNames(aClass.getName(), newClassName);
      }
    }

    private void appendTestClass(PsiClass aClass, String testSuffix, final GlobalSearchScope moduleScope) {
      PsiShortNamesCache cache = PsiShortNamesCache.getInstance(aClass.getProject());

      String klassName = aClass.getName();
      Pattern pattern = Pattern.compile(".*" + klassName + ".*" + testSuffix);

      HashSet<String> names = new HashSet<>();
      cache.getAllClassNames(names);
      for (String eachName : names) {
        if (pattern.matcher(eachName).matches()) {
          for (PsiClass eachClass : cache.getClassesByName(eachName, moduleScope)) {
            if (TestFrameworks.getInstance().isTestClass(eachClass)) {
              myElements.add(eachClass);
            }
          }
        }
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
