// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.naming;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.regex.Pattern;

/**
 * @author yole
 */
public class AutomaticTestRenamerFactory implements AutomaticRenamerFactory {
  @Override
  public boolean isApplicable(@NotNull PsiElement element) {
    return element instanceof PsiClass &&
           !(element instanceof PsiTypeParameter) &&
           TestFrameworks.detectFramework((PsiClass)element) == null;
  }

  @Override
  public String getOptionName() {
    return RefactoringBundle.message("rename.tests");
  }

  @Override
  public boolean isEnabled() {
    return JavaRefactoringSettings.getInstance().isToRenameTests();
  }

  @Override
  public void setEnabled(boolean enabled) {
    JavaRefactoringSettings.getInstance().setRenameTests(enabled);
  }

  @NotNull
  @Override
  public AutomaticRenamer createRenamer(PsiElement element, String newName, Collection<UsageInfo> usages) {
    return new TestsRenamer((PsiClass)element, newName);
  }

  private static class TestsRenamer extends AutomaticRenamer {
    public TestsRenamer(PsiClass aClass, String newClassName) {
      Module module = ModuleUtilCore.findModuleForPsiElement(aClass);
      if (module != null) {
        GlobalSearchScope moduleScope = GlobalSearchScope.moduleWithDependentsScope(module);

        PsiShortNamesCache cache = PsiShortNamesCache.getInstance(aClass.getProject());

        Pattern pattern = Pattern.compile(".*" + aClass.getName() + ".*");

        int count = 0;
        for (String eachName : ContainerUtil.newHashSet(cache.getAllClassNames())) {
          if (pattern.matcher(eachName).matches()) {
            if (count ++ > 1000) break;
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

    @Override
    public String getDialogTitle() {
      return RefactoringBundle.message("rename.tests.title");
    }

    @Override
    public String getDialogDescription() {
      return RefactoringBundle.message("rename.tests.with.the.following.names.to");
    }

    @Override
    public String entityName() {
      return RefactoringBundle.message("entity.name.test");
    }
  }
}