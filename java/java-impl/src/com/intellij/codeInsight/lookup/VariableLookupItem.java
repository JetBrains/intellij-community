package com.intellij.codeInsight.lookup;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.*;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
* @author peter
*/
public class VariableLookupItem extends LookupItem<PsiVariable> implements TypedLookupItem, StaticallyImportable {
  @Nullable private final MemberLookupHelper myHelper;

  public VariableLookupItem(PsiVariable object) {
    super(object, object.getName());
    myHelper = null;
  }

  public VariableLookupItem(PsiField field, boolean shouldImport) {
    super(field, field.getName());
    myHelper = new MemberLookupHelper(field, field.getContainingClass(), shouldImport, false);
    if (!shouldImport) {
      forceQualify();
    }
  }

  @Override
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
  public void setShouldBeImported(boolean shouldImportStatic) {
    assert myHelper != null;
    myHelper.setShouldBeImported(shouldImportStatic);
  }

  @Override
  public boolean canBeImported() {
    return myHelper != null;
  }

  @Override
  public boolean willBeImported() {
    return myHelper != null && myHelper.willBeImported();
  }

  @Override
  public void renderElement(LookupElementPresentation presentation) {
    super.renderElement(presentation);
    if (myHelper != null) {
      myHelper.renderElement(presentation, getAttribute(FORCE_QUALIFY) != null ? Boolean.TRUE : null, getSubstitutor());
    }
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
    PsiVariable variable = getObject();

    Document document = context.getDocument();
    document.replaceString(context.getStartOffset(), context.getTailOffset(), variable.getName());
    context.commitDocument();

    if (variable instanceof PsiField) {
      if (willBeImported()) {
        RangeMarker toDelete = JavaCompletionUtil.insertTemporary(context.getTailOffset(), document, " ");
        context.commitDocument();
        final PsiReferenceExpression
          ref = PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), PsiReferenceExpression.class, false);
        if (ref != null) {
          ref.bindToElementViaStaticImport(((PsiField)variable).getContainingClass());
          PostprocessReformattingAspect.getInstance(ref.getProject()).doPostponedFormatting();
        }
        if (toDelete.isValid()) {
          document.deleteString(toDelete.getStartOffset(), toDelete.getEndOffset());
        }
        context.commitDocument();
      }
      else if (shouldQualify((PsiField)variable, context)) {
        qualifyFieldReference(context, (PsiField)variable);
      }
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
      AutoPopupController.getInstance(context.getProject()).autoPopupParameterInfo(context.getEditor(), null);
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
        document.insertString(ref.getTextRange().getStartOffset(), "!");
      }
    }
  }

  private boolean shouldQualify(PsiField field, InsertionContext context) {
    if (myHelper != null && !myHelper.willBeImported()) {
      return true;
    }

    if (getAttribute(FORCE_QUALIFY) != null) {
      return true;
    }

    PsiReference reference = context.getFile().findReferenceAt(context.getStartOffset());
    if (reference instanceof PsiReferenceExpression && !((PsiReferenceExpression)reference).isQualified()) {
      final PsiVariable target = JavaPsiFacade.getInstance(context.getProject()).getResolveHelper()
        .resolveReferencedVariable(field.getName(), (PsiElement)reference);
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
      OffsetKey oldStart = context.trackOffset(context.getStartOffset(), true);
      JavaCompletionUtil.insertClassReference(containingClass, file, context.getStartOffset());
      context.getDocument().insertString(context.getOffsetMap().getOffset(oldStart), ".");
      PsiDocumentManager.getInstance(context.getProject()).commitDocument(context.getDocument());
    }
  }
}
