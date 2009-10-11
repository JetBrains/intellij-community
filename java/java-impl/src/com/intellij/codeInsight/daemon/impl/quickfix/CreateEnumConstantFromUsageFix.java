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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypeUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CreateEnumConstantFromUsageFix extends CreateVarFromUsageFix {
  private static final Logger LOG = Logger.getInstance("com.intellij.codeInsight.daemon.impl.quickfix.CreateEnumConstantFromUsageFix");
  public CreateEnumConstantFromUsageFix(final PsiReferenceExpression referenceElement) {
    super(referenceElement);
  }

  protected String getText(String varName) {
    return QuickFixBundle.message("create.enum.constant.from.usage.text", myReferenceExpression.getReferenceName());
  }

  protected void invokeImpl(PsiClass targetClass) {
    LOG.assertTrue(targetClass.isEnum());
    final String name = myReferenceExpression.getReferenceName();
    LOG.assertTrue(name != null);
    try {
      final PsiEnumConstant enumConstant =
        JavaPsiFacade.getInstance(myReferenceExpression.getProject()).getElementFactory().createEnumConstantFromText(name, null);
      targetClass.add(enumConstant);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }


  protected boolean isAvailableImpl(int offset) {
    if (!super.isAvailableImpl(offset)) return false;
    PsiElement element = getElement();
    final List<PsiClass> classes = getTargetClasses(element);
    if (classes.size() != 1 || !classes.get(0).isEnum()) return false;
    ExpectedTypeInfo[] typeInfos = CreateFromUsageUtils.guessExpectedTypes(myReferenceExpression, false);
    PsiType enumType = JavaPsiFacade.getInstance(myReferenceExpression.getProject()).getElementFactory().createType(classes.get(0));
    for (final ExpectedTypeInfo typeInfo : typeInfos) {
      if (ExpectedTypeUtil.matches(enumType, typeInfo)) return true;
    }
    return false;
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("create.constant.from.usage.family");
  }
}
