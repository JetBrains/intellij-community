package com.intellij.codeInsight.lookup;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
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

    if (context.getCompletionChar() == '=') {
      context.setAddCompletionChar(false);
      TailType.EQ.processTail(context.getEditor(), context.getTailOffset());
    }
    else if (context.getCompletionChar() == ',' && getAttribute(LookupItem.TAIL_TYPE_ATTR) != TailType.UNKNOWN) {
      context.setAddCompletionChar(false);
      TailType.COMMA.processTail(context.getEditor(), context.getTailOffset());
    }
  }
}
