package com.intellij.codeInsight.lookup;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.CodeCompletionFeatures;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
* @author peter
*/
public class VariableLookupItem extends LookupItem<PsiVariable> implements TypedLookupItem {
  public VariableLookupItem(PsiVariable object) {
    super(object, object.getName());
  }

  public PsiType getType() {
    return getSubstitutor().substitute(getObject().getType());
  }

  @NotNull
  public PsiSubstitutor getSubstitutor() {
    final PsiSubstitutor substitutor = (PsiSubstitutor)getAttribute(LookupItem.SUBSTITUTOR);
    return substitutor == null ? PsiSubstitutor.EMPTY : substitutor;
  }

  public void setSubstitutor(@NotNull PsiSubstitutor substitutor) {
    setAttribute(SUBSTITUTOR, substitutor);
  }

  @Override
  public void handleInsert(InsertionContext context) {
    super.handleInsert(context);

    final char completionChar = context.getCompletionChar();
    if (completionChar == '=') {
      context.setAddCompletionChar(false);
      TailType.EQ.processTail(context.getEditor(), context.getTailOffset());
    }
    else if (completionChar == ',' && getAttribute(LookupItem.TAIL_TYPE_ATTR) != TailType.UNKNOWN) {
      context.setAddCompletionChar(false);
      TailType.COMMA.processTail(context.getEditor(), context.getTailOffset());
    }
    else if (completionChar == '.') {
      AutoPopupController.getInstance(context.getProject()).autoPopupMemberLookup(context.getEditor(), null);
    }
    else if (completionChar == '!' && PsiType.BOOLEAN.isAssignableFrom(getObject().getType())) {
      context.setAddCompletionChar(false);
      final PsiReferenceExpression ref = PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), context.getTailOffset() - 1, PsiReferenceExpression.class, false);
      if (ref != null) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.EXCLAMATION_FINISH);
        context.getDocument().insertString(ref.getTextRange().getStartOffset(), "!");
      }
    }


  }
}
