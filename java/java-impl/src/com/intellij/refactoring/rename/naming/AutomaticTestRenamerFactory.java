/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.regex.Pattern;

/**
 * @author yole
 */
public class AutomaticTestRenamerFactory implements AutomaticRenamerFactory {
  public boolean isApplicable(@NotNull final PsiElement element) {
    return element instanceof PsiClass && TestFrameworks.detectFramework((PsiClass)element) == null;
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

        PsiShortNamesCache cache = PsiShortNamesCache.getInstance(aClass.getProject());

        String klassName = aClass.getName();
        Pattern pattern = Pattern.compile(".*" + klassName + ".*");

        HashSet<String> names = new HashSet<>();
        cache.getAllClassNames(names);
        for (String eachName : names) {
          if (pattern.matcher(eachName).matches()) {
            for (PsiClass eachClass : cache.getClassesByName(eachName, moduleScope)) {
              if (TestFrameworks.detectFramework(eachClass) != null) {
                myElements.add(eachClass);
              }
            }
          }
        }

        suggestAllNames(aClass.getName(), newClassName);
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
