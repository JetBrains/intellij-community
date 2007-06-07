package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.codeInsight.daemon.QuickFixBundle;

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
          PsiJavaCodeReferenceElement ref = newExpression.getClassReference();
          assert ref != null;
          String refName = ref.getReferenceName();
          LOG.assertTrue(refName != null);
          PsiElementFactory elementFactory = newExpression.getManager().getElementFactory();
          PsiClass created = elementFactory.createClass(refName);
          final PsiModifierList modifierList = created.getModifierList();
          LOG.assertTrue(modifierList != null);
          modifierList.setModifierProperty(PsiModifier.PRIVATE, true);

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