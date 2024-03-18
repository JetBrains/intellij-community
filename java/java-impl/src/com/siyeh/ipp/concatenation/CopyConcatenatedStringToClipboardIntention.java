// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.concatenation;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiBasedModCommandAction;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public final class CopyConcatenatedStringToClipboardIntention extends PsiBasedModCommandAction<PsiElement> {
  public CopyConcatenatedStringToClipboardIntention() {
    super(PsiElement.class);
  }
  
  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("copy.concatenated.string.to.clipboard.intention.family.name");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiElement element) {
    if (element instanceof PsiFragment) {
      return Presentation.of(IntentionPowerPackBundle.message("copy.string.template.text.to.clipboard.intention.name"));
    }
    PsiPolyadicExpression polyadicExpression =
      PsiTreeUtil.getParentOfType(element, PsiPolyadicExpression.class, false,
                                  PsiStatement.class, PsiMember.class, PsiLambdaExpression.class);
    if (ExpressionUtils.isStringConcatenation(polyadicExpression)) {
      return Presentation.of(IntentionPowerPackBundle.message("copy.concatenated.string.to.clipboard.intention.name"));
    }
    if (PsiUtil.isJavaToken(element, ElementType.STRING_LITERALS)) {
      return Presentation.of(IntentionPowerPackBundle.message("copy.string.literal.to.clipboard.intention.name"));
    }
    return null;
  }

  @Override
  protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull PsiElement element) {
    final String text;
    if (element instanceof PsiFragment) {
      text = buildStringTemplateText((PsiTemplate)element.getParent());
    }
    else {
      PsiPolyadicExpression polyadic = PsiTreeUtil.getNonStrictParentOfType(element, PsiPolyadicExpression.class);
      if (ExpressionUtils.isStringConcatenation(polyadic)) {
        text = buildConcatenationText(polyadic);
      }
      else if (element.getParent() instanceof PsiLiteralExpression literalExpression) {
        if (!(literalExpression.getValue() instanceof String string)) {
          return ModCommand.nop();
        }
        text = string;
      }
      else {
        return ModCommand.nop();
      }
    }
    return ModCommand.copyToClipboard(text);
  }

  @NotNull
  private static String buildStringTemplateText(PsiTemplate template) {
    StringBuilder sb = new StringBuilder();
    boolean separator = false;
    for (PsiFragment fragment : template.getFragments()) {
      if (separator) {
        sb.append('?');
      }
      else {
        separator = true;
      }
      sb.append(fragment.getValue());
    }
    return sb.toString();
  }

  public static String buildConcatenationText(PsiPolyadicExpression polyadicExpression) {
    final StringBuilder out = new StringBuilder();
    for(PsiElement element = polyadicExpression.getFirstChild(); element != null; element = element.getNextSibling()) {
      if (element instanceof PsiExpression expression) {
        final Object value = ExpressionUtils.computeConstantExpression(expression);
        out.append((value == null) ? "?" : value.toString());
      }
      else if (element instanceof PsiWhiteSpace && element.getText().contains("\n") &&
               (out.isEmpty() || out.charAt(out.length() - 1) != '\n')) {
        out.append('\n');
      }
    }
    return out.toString();
  }
}
