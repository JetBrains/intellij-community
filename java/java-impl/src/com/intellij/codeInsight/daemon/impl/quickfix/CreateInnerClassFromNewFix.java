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

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;

/**
 * @author yole
 */
public class CreateInnerClassFromNewFix extends CreateClassFromNewFix {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.CreateInnerClassFromNewFix");

  public CreateInnerClassFromNewFix(final PsiNewExpression expr) {
    super(expr);
  }

  public String getText(String varName) {
    return QuickFixBundle.message("create.inner.class.from.usage.text", StringUtil.capitalize(CreateClassKind.CLASS.getDescription()), varName);
  }

  protected boolean isAllowOuterTargetClass() {
    return true;
  }

  protected void invokeImpl(final PsiClass targetClass) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        try {
          PsiNewExpression newExpression = getNewExpression();
          PsiJavaCodeReferenceElement ref = newExpression.getClassOrAnonymousClassReference();
          assert ref != null;
          String refName = ref.getReferenceName();
          LOG.assertTrue(refName != null);
          PsiElementFactory elementFactory = JavaPsiFacade.getInstance(newExpression.getProject()).getElementFactory();
          PsiClass created = elementFactory.createClass(refName);
          final PsiModifierList modifierList = created.getModifierList();
          LOG.assertTrue(modifierList != null);
          modifierList.setModifierProperty(PsiModifier.PRIVATE, true);
          if (PsiUtil.getEnclosingStaticElement(newExpression, targetClass) != null) {
            modifierList.setModifierProperty(PsiModifier.STATIC, true);            
          }
          created = (PsiClass)targetClass.add(created);

          setupClassFromNewExpression(created, newExpression);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    });
  }
}