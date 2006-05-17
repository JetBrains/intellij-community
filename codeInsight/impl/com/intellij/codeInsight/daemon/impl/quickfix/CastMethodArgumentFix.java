/**
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Nov 13, 2002
 * Time: 3:26:50 PM
 * To change this template use Options | File Templates.
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class CastMethodArgumentFix extends FixMethodArgumentAction {
  private CastMethodArgumentFix(PsiExpressionList list, int i, PsiType toType, final ArgumentFixerActionFactory factory) {
    super(list, i, toType, factory);
  }

  @NotNull
  public String getText() {
    if (myArgList.getExpressions().length == 1) {
      return QuickFixBundle.message("cast.single.parameter.text", HighlightUtil.formatType(myToType));
    }

    return QuickFixBundle.message("cast.parameter.text", myIndex + 1, HighlightUtil.formatType(myToType));
  }

  private static class MyFixerActionFactory extends ArgumentFixerActionFactory {
    public CastMethodArgumentFix createFix(final PsiExpressionList list, final int i, final PsiType toType) {
      return new CastMethodArgumentFix(list, i, toType, this);
    }

    protected PsiExpression getModifiedArgument(final PsiExpression expression, final PsiType toType) throws IncorrectOperationException {
      if (!toType.isConvertibleFrom(expression.getType())) return null;
      return AddTypeCastFix.createCastExpression(expression, expression.getProject(), toType);
    }
  }

  public static ArgumentFixerActionFactory REGISTRAR = new MyFixerActionFactory();
}
