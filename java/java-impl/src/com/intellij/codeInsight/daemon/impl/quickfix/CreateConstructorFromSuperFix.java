/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * @author ven
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class CreateConstructorFromSuperFix extends CreateConstructorFromThisOrSuperFix {

  public CreateConstructorFromSuperFix(@NotNull PsiMethodCallExpression methodCall) {
    super(methodCall);
  }

  @Override
  protected String getSyntheticMethodName() {
    return "super";
  }

  @Override
  @NotNull
  protected List<PsiClass> getTargetClasses(PsiElement element) {
    do {
      element = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    }
    while (element instanceof PsiTypeParameter);
    PsiClass curClass = (PsiClass)element;
    if (curClass == null || curClass instanceof PsiAnonymousClass) return Collections.emptyList();
    PsiClassType[] extendsTypes = curClass.getExtendsListTypes();
    if (extendsTypes.length == 0) return Collections.emptyList();
    PsiClass aClass = extendsTypes[0].resolve();
    if (aClass instanceof PsiTypeParameter) return Collections.emptyList();
    if (aClass != null && aClass.isValid() && aClass.getManager().isInProject(aClass)) {
      return Collections.singletonList(aClass);
    }
    return Collections.emptyList();
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("create.constructor.from.super.call.family");
  }
}