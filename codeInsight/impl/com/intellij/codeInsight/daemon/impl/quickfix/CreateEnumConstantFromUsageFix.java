package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypeUtil;
import com.intellij.openapi.diagnostic.Logger;
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
      final PsiEnumConstant enumConstant = myReferenceExpression.getManager().getElementFactory().createEnumConstantFromText(name, null);
      targetClass.add(enumConstant);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }


  protected boolean isAvailableImpl(int offset) {
    if (!super.isAvailableImpl(offset)) return false;
    final List<PsiClass> classes = getTargetClasses(getElement());
    if (classes.size() != 1 || !classes.get(0).isEnum()) return false;
    ExpectedTypeInfo[] typeInfos = CreateFromUsageUtils.guessExpectedTypes(myReferenceExpression, false);
    PsiType enumType = myReferenceExpression.getManager().getElementFactory().createType(classes.get(0));
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
