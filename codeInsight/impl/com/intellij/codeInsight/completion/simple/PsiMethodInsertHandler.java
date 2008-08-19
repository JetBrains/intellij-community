/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion.simple;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.completion.CodeCompletionFeatures;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.MutableLookupElement;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
*/
public class PsiMethodInsertHandler implements InsertHandler<LookupItem> {
  private final PsiMethod myMethod;

  public PsiMethodInsertHandler(final PsiMethod method) {
    myMethod = method;
  }

  public void handleInsert(final InsertionContext context, final LookupItem item) {
    context.setAddCompletionChar(false);
    final Editor editor = context.getEditor();
    final char completionChar = context.getCompletionChar();
    TailType tailType = getTailType(item, editor, completionChar);
    if (tailType == null) {
      tailType = LookupItem.handleCompletionChar(editor, item, completionChar);
    }
    final int tailOffset;
    try {
      tailOffset = handleInsert(editor, context.getStartOffset(), item, context.getElements(), tailType, completionChar);
    }
    catch (IncorrectOperationException e) {
      throw new RuntimeException(e);
    }
    tailType.processTail(editor, tailOffset);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
  }

  @Nullable
  protected TailType getTailType(final LookupItem item, final Editor editor, final char completionChar) {
    return null;
  }

  public int handleInsert(final Editor editor, final int startOffset, final LookupElement item, final LookupElement[] allItems, final TailType tailType,
                          final char completionChar)
    throws IncorrectOperationException {
    final Document document = editor.getDocument();
    final PsiFile file = PsiDocumentManager.getInstance(myMethod.getProject()).getPsiFile(document);

    boolean signatureSelected = allItems.length > 1 && CodeInsightSettings.getInstance().SHOW_SIGNATURES_IN_LOOKUPS ||
                                ((LookupItem)item).getAttribute(LookupItem.FORCE_SHOW_SIGNATURE_ATTR) != null;
    final boolean needLeftParenth = file == null || isToInsertParenth(file.findElementAt(startOffset));
    final boolean hasParams = needLeftParenth && hasParams(item, allItems, signatureSelected);
    int offset = editor.getCaretModel().getOffset();
    int tailOffset = SimpleInsertHandlerFactory.handleParenses(hasParams, needLeftParenth, offset, document, file, editor, tailType);
    tailOffset = insertExplicitTypeParams(item, document, offset, tailOffset, file);

    final PsiType type = myMethod.getReturnType();
    if (completionChar == '!' && type != null && PsiType.BOOLEAN.isAssignableFrom(type)) {
      PsiDocumentManager.getInstance(myMethod.getProject()).commitDocument(document);
      final PsiMethodCallExpression methodCall =
          PsiTreeUtil.findElementOfClassAtOffset(file, offset, PsiMethodCallExpression.class, false);
      if (methodCall != null) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.EXCLAMATION_FINISH);
        document.insertString(methodCall.getTextRange().getStartOffset(), "!");
        tailOffset++;
      }
    }

    if (needLeftParenth && hasParams) {
      // Invoke parameters popup
      AutoPopupController.getInstance(myMethod.getProject()).autoPopupParameterInfo(editor, signatureSelected ? myMethod : null);
    }
    return tailOffset;
  }

  private boolean isToInsertParenth(PsiElement place){
    if (place == null) return true;
    if (JavaCompletionUtil.isCompletionOfAnnotationMethod(myMethod, place)) {
      return false;
    }
    return !(place.getParent() instanceof PsiImportStaticReferenceElement);
  }

  private boolean hasParams(LookupElement item, LookupElement[] allItems, boolean signatureSelected){
    boolean hasParms = myMethod.getParameterList().getParametersCount() > 0;
    if (!signatureSelected){
      hasParms |= hasOverloads(item, allItems);
    }
    return hasParms;
  }

  private boolean hasOverloads(LookupElement item, LookupElement[] allItems) {
    if (!(item instanceof MutableLookupElement)) return false;

    String name = myMethod.getName();
    for (LookupElement item1 : allItems) {
      final Object o = item1.getObject();
      if (item.getObject() != o && o instanceof PsiMethod && ((PsiMethod)o).getName().equals(name)) {
        return true;
      }
    }
    return false;
  }

  static int insertExplicitTypeParams(final LookupElement item, final Document document, final int offset, int tailOffset, PsiFile file)
    throws IncorrectOperationException {
    if (((LookupItem)item).getAttribute(LookupItem.INSERT_TYPE_PARAMS) != null) {
      final PsiSubstitutor substitutor = (PsiSubstitutor)((LookupItem)item).getAttribute(LookupItem.SUBSTITUTOR);
      assert substitutor != null;
      final int nameLength = item.getLookupString().length();
      final PsiMethod method = (PsiMethod)((LookupItem)item).getObject();
      final PsiTypeParameter[] parameters = method.getTypeParameters();
      assert parameters.length > 0;
      final StringBuilder builder = new StringBuilder("<");
      boolean first = true;
      for (final PsiTypeParameter parameter : parameters) {
        if (!first) builder.append(", ");
        first = false;
        final PsiType type = substitutor.substitute(parameter);
        if (type == null || type instanceof PsiWildcardType || type instanceof PsiCapturedWildcardType) return tailOffset;

        final String text = type.getCanonicalText();
        if (text.indexOf('?') >= 0) return tailOffset;

        builder.append(text);
      }
      final RangeMarker tailMarker = document.createRangeMarker(tailOffset, tailOffset);
      final String typeParams = builder.append(">").toString();
      document.insertString(offset - nameLength, typeParams);
      PsiDocumentManager.getInstance(method.getProject()).commitDocument(document);
      final PsiReference reference = file.findReferenceAt(offset - nameLength + typeParams.length() + 1);
      if (reference instanceof PsiJavaCodeReferenceElement) {
        CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(JavaCodeStyleManager.getInstance(file.getProject()).shortenClassReferences((PsiElement)reference));
      }
      assert tailMarker.isValid();
      return tailMarker.getStartOffset();
    }
    return tailOffset;
  }
}
