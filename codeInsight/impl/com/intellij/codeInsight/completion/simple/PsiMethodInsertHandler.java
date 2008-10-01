/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion.simple;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.completion.util.MethodParenthesesHandler;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
*/
public class PsiMethodInsertHandler implements InsertHandler<LookupItem<PsiMethod>> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.simple.PsiMethodInsertHandler");
  private final PsiMethod myMethod;

  public PsiMethodInsertHandler(final PsiMethod method) {
    myMethod = method;
  }

  public void handleInsert(final InsertionContext context, final LookupItem<PsiMethod> item) {
    context.setAddCompletionChar(false);
    final Editor editor = context.getEditor();
    final char completionChar = context.getCompletionChar();
    TailType tailType = getTailType(item, editor, completionChar);
    final Document document = editor.getDocument();
    final PsiFile file = context.getFile();

    final LookupElement[] allItems = context.getElements();
    boolean signatureSelected = allItems.length > 1 && CodeInsightSettings.getInstance().SHOW_SIGNATURES_IN_LOOKUPS ||
                                item.getAttribute(LookupItem.FORCE_SHOW_SIGNATURE_ATTR) != null;

    int offset = editor.getCaretModel().getOffset();
    final boolean needLeftParenth = isToInsertParenth(file.findElementAt(context.getStartOffset()));
    final boolean hasParams = MethodParenthesesHandler.hasParams(item, allItems, !signatureSelected, myMethod);
    if (needLeftParenth) {
      final CodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(context.getProject());
      new MethodParenthesesHandler(myMethod, !signatureSelected,
                                           styleSettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES,
                                           styleSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES && hasParams,
                                           shouldInsertRightParenthesis(item, hasParams, tailType)
      ).handleInsert(context, item);
    }
    
    insertExplicitTypeParams(item, document, offset, file);

    final PsiType type = myMethod.getReturnType();
    if (completionChar == '!' && type != null && PsiType.BOOLEAN.isAssignableFrom(type)) {
      PsiDocumentManager.getInstance(myMethod.getProject()).commitDocument(document);
      final PsiMethodCallExpression methodCall =
          PsiTreeUtil.findElementOfClassAtOffset(file, offset, PsiMethodCallExpression.class, false);
      if (methodCall != null) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.EXCLAMATION_FINISH);
        document.insertString(methodCall.getTextRange().getStartOffset(), "!");
      }
    }

    if (needLeftParenth && hasParams) {
      // Invoke parameters popup
      AutoPopupController.getInstance(myMethod.getProject()).autoPopupParameterInfo(editor, signatureSelected ? myMethod : null);
    }
    tailType.processTail(editor, context.getTailOffset());
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
  }

  protected static boolean shouldInsertRightParenthesis(final LookupItem<PsiMethod> item, boolean hasParams, TailType tailType) {
    if (tailType == TailType.SMART_COMPLETION) return false;

    final CodeInsightSettings settings = CodeInsightSettings.getInstance();
    if (settings.INSERT_SINGLE_PARENTH && (!settings.INSERT_DOUBLE_PARENTH_WHEN_NO_ARGS || hasParams) && tailType == TailType.NONE) {
      return false;
    }
    return true;
  }

  @NotNull
  private static TailType getTailType(final LookupItem item, final Editor editor, final char completionChar) {
    if (completionChar == '!') return item.getTailType();
    if (completionChar == '(') {
      final PsiMethod psiMethod = (PsiMethod)item.getObject();
      return psiMethod.getParameterList().getParameters().length > 0 || psiMethod.getReturnType() != PsiType.VOID
             ? TailType.NONE : TailType.SEMICOLON;
    }
    if (completionChar == Lookup.COMPLETE_STATEMENT_SELECT_CHAR) return TailType.SMART_COMPLETION;
    return LookupItem.handleCompletionChar(editor, item, completionChar);
  }

  private boolean isToInsertParenth(PsiElement place){
    if (place == null) return true;
    if (JavaCompletionUtil.isCompletionOfAnnotationMethod(myMethod, place)) {
      return false;
    }
    return !(place.getParent() instanceof PsiImportStaticReferenceElement);
  }

  private static void insertExplicitTypeParams(final LookupItem<PsiMethod> item, final Document document, final int offset, PsiFile file) {
    if (item.getAttribute(LookupItem.INSERT_TYPE_PARAMS) != null) {
      final PsiSubstitutor substitutor = (PsiSubstitutor)item.getAttribute(LookupItem.SUBSTITUTOR);
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
        if (type == null || type instanceof PsiWildcardType || type instanceof PsiCapturedWildcardType) return;

        final String text = type.getCanonicalText();
        if (text.indexOf('?') >= 0) return;

        builder.append(text);
      }
      final String typeParams = builder.append(">").toString();
      document.insertString(offset - nameLength, typeParams);
      PsiDocumentManager.getInstance(method.getProject()).commitDocument(document);
      final PsiReference reference = file.findReferenceAt(offset - nameLength + typeParams.length() + 1);
      if (reference instanceof PsiJavaCodeReferenceElement) {
        try {
          CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(JavaCodeStyleManager.getInstance(file.getProject()).shortenClassReferences((PsiElement)reference));
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    }
  }
}
