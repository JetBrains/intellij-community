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
package com.intellij.codeInspection.unusedReturnValue;

import com.intellij.codeInsight.daemon.impl.UnusedSymbolUtil;
import com.intellij.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.reference.RefUtil;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtil;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UnusedReturnValueLocalInspection extends BaseJavaLocalInspectionTool {
  private final UnusedReturnValue myGlobal;

  public UnusedReturnValueLocalInspection(UnusedReturnValue global) {myGlobal = global;}

  @Override
  public boolean runForWholeFile() {
    return true;
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return myGlobal.getGroupDisplayName();
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return myGlobal.getDisplayName();
  }

  @Override
  @NotNull
  public String getShortName() {
    return myGlobal.getShortName();
  }

  @Nullable
  @Override
  public ProblemDescriptor[] checkMethod(@NotNull PsiMethod method, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (method.isConstructor() ||
        PsiType.VOID.equals(method.getReturnType()) ||
        myGlobal.IGNORE_BUILDER_PATTERN && PropertyUtil.isSimplePropertySetter(method) ||
        method.hasModifierProperty(PsiModifier.NATIVE) ||
        MethodUtils.hasSuper(method) ||
        RefUtil.isImplicitRead(method)) return null;

    final boolean[] atLeastOneUsageExists = new boolean[]{false};
    if (UnusedSymbolUtil.processUsages(manager.getProject(), method.getContainingFile(), method, new EmptyProgressIndicator(), null, u -> {
      if (!atLeastOneUsageExists[0]) atLeastOneUsageExists[0] = true;
      return PsiJavaPatterns.psiElement(PsiReferenceExpression.class)
        .withParent(PsiJavaPatterns.psiElement(PsiMethodCallExpression.class)
                                   .withParent(PsiExpressionStatement.class))
        .accepts(u.getElement());
    })) {
      if (atLeastOneUsageExists[0]) {
        return new ProblemDescriptor[]{UnusedReturnValue.createProblemDescriptor(method, manager, null, false)};
      }
    }
    return null;
  }
}
