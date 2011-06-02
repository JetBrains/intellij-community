package com.intellij.codeInsight.lookup;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.CodeCompletionFeatures;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
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
  public LookupItem<PsiVariable> forceQualify() {
    PsiVariable var = getObject();
    if (var instanceof PsiField) {
      for (String s : JavaCompletionUtil.getAllLookupStrings((PsiField)var)) {
        setLookupString(s); //todo set the string that will be inserted
      }
    }
    return super.forceQualify();
  }

  @Override
  public void handleInsert(InsertionContext context) {
    super.handleInsert(context);

    PsiVariable variable = getObject();
    context.getDocument().replaceString(context.getStartOffset(), context.getTailOffset(), variable.getName());

    PsiDocumentManager.getInstance(context.getProject()).commitDocument(context.getDocument());
    if (variable instanceof PsiField && shouldQualify((PsiField)variable, context)) {
      qualifyFieldReference(context, (PsiField)variable);
    }

    PsiReferenceExpression ref = PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), context.getTailOffset() - 1, PsiReferenceExpression.class, false);
    if (ref != null) {
      JavaCodeStyleManager.getInstance(context.getProject()).shortenClassReferences(ref);
    }

    final char completionChar = context.getCompletionChar();
    if (completionChar == '=') {
      context.setAddCompletionChar(false);
      TailType.EQ.processTail(context.getEditor(), context.getTailOffset());
    }
    else if (completionChar == ',' && getAttribute(LookupItem.TAIL_TYPE_ATTR) != TailType.UNKNOWN) {
      context.setAddCompletionChar(false);
      TailType.COMMA.processTail(context.getEditor(), context.getTailOffset());
    }
    else if (completionChar == ':') {
      context.setAddCompletionChar(false);
      TailType.COND_EXPR_COLON.processTail(context.getEditor(), context.getTailOffset());
    }
    else if (completionChar == '.') {
      AutoPopupController.getInstance(context.getProject()).autoPopupMemberLookup(context.getEditor(), null);
    }
    else if (completionChar == '!' && PsiType.BOOLEAN.isAssignableFrom(variable.getType())) {
      context.setAddCompletionChar(false);
      if (ref != null) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.EXCLAMATION_FINISH);
        context.getDocument().insertString(ref.getTextRange().getStartOffset(), "!");
      }
    }
  }

  protected boolean shouldQualify(PsiField field, InsertionContext context) {
    if (getAttribute(FORCE_QUALIFY) != null) {
      return true;
    }

    PsiReference reference = context.getFile().findReferenceAt(context.getStartOffset());
    if (reference instanceof PsiReferenceExpression && !((PsiReferenceExpression) reference).isQualified()) {
      final PsiVariable target = JavaPsiFacade.getInstance(context.getProject()).getResolveHelper().resolveReferencedVariable(field.getName(), (PsiElement)reference);
      return !field.getManager().areElementsEquivalent(target, CompletionUtil.getOriginalOrSelf(field));
    }
    return false;
  }

  private static void qualifyFieldReference(InsertionContext context, PsiField field) {
    context.commitDocument();
    PsiFile file = context.getFile();
    final PsiReference reference = file.findReferenceAt(context.getStartOffset());
    if (reference instanceof PsiJavaCodeReferenceElement && ((PsiJavaCodeReferenceElement)reference).isQualified()) {
      return;
    }

    PsiClass containingClass = field.getContainingClass();
    if (containingClass != null && containingClass.getName() != null) {
      context.getDocument().insertString(context.getStartOffset(), ".");
      JavaCompletionUtil.insertClassReference(containingClass, file, context.getStartOffset());
      PsiDocumentManager.getInstance(context.getProject()).commitDocument(context.getDocument());
    }
  }
}
