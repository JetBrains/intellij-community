// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CreateStaticMethodQuickFix implements LocalQuickFix {
  private final SmartPsiElementPointer<PsiClass> myTargetClass;
  private final String myMethodName;
  private final List<String> myTypes;

  public CreateStaticMethodQuickFix(@NotNull PsiClass aClass,
                                    @NotNull String name,
                                    @NotNull List<String> types) {
    myTargetClass = SmartPointerManager.createPointer(aClass);
    myMethodName = name;
    myTypes = types;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("create.method.from.usage.family");
  }

  @Override
  public void applyFix(@NotNull final Project project, @NotNull ProblemDescriptor descriptor) {
    PsiClass targetClass = myTargetClass.getElement();

    if (targetClass == null) return;

    boolean java8Interface = false;
    if (targetClass.isInterface()) {
      if (PsiUtil.isLanguageLevel8OrHigher(targetClass)) {
        java8Interface = true;
      }
      else {
        return;
      }
    }

    PsiMethod method = CreateMethodFromUsageFix.createMethod(targetClass, null, null, myMethodName);
    if (method == null) return;

    if (!java8Interface) {
      PsiUtil.setModifierProperty(method, PsiModifier.PUBLIC, true);
    }
    PsiUtil.setModifierProperty(method, PsiModifier.STATIC, true);

    List<Pair<PsiExpression, PsiType>> args = ContainerUtil.map(myTypes, s -> new Pair<>(null, PsiType.getTypeByName(s, project, GlobalSearchScope.allScope(project))));
    CreateMethodFromUsageFix.doCreate(targetClass, method, false,
                                      args,
                                      PsiSubstitutor.UNKNOWN,
                                      ExpectedTypeInfo.EMPTY_ARRAY,
                                      null);
  }
}
