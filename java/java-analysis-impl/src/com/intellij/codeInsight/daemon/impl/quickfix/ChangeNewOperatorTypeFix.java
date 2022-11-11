// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInspection.RemoveRedundantTypeArgumentsUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UnfairTextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
import com.intellij.psi.util.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ChangeNewOperatorTypeFix implements IntentionAction {
  private final PsiType myType;
  private final PsiNewExpression myExpression;

  private ChangeNewOperatorTypeFix(PsiType type, PsiNewExpression expression) {
    myType = type;
    myExpression = expression;
  }

  @Override
  @NotNull
  public String getText() {
    return QuickFixBundle.message("change.new.operator.type.text", new PsiExpressionTrimRenderer.RenderFunction().fun(myExpression),
                                  myType.getPresentableText(), myType instanceof PsiArrayType ? "" : "()");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("change.new.operator.type.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myType.isValid()
           && myExpression.isValid()
           && BaseIntentionAction.canModify(myExpression)
           && !TypeConversionUtil.isPrimitiveAndNotNull(myType)
           && (myType instanceof PsiArrayType || myExpression.getArgumentList() != null)
      ;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    changeNewOperatorType(myExpression, myType, editor);
  }

  private static void changeNewOperatorType(PsiNewExpression originalExpression, PsiType toType, Editor editor) {
    PsiNewExpression newExpression;
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(originalExpression.getProject());
    int caretOffset;
    TextRange selection;
    CommentTracker commentTracker = new CommentTracker();
    if (toType instanceof PsiArrayType) {
      final PsiExpression[] originalExpressionArrayDimensions = originalExpression.getArrayDimensions();
      caretOffset = 0;
      @NonNls StringBuilder text = new StringBuilder("new " + toType.getDeepComponentType().getCanonicalText() + "[");
      if (originalExpressionArrayDimensions.length > 0) {
        text.append(commentTracker.text(originalExpressionArrayDimensions[0]));
      }
      else {
        text.append("0");
        caretOffset = -2;
      }
      text.append("]");
      for (int i = 1; i < toType.getArrayDimensions(); i++) {
        text.append("[");
        if (originalExpressionArrayDimensions.length > i) {
          text.append(commentTracker.text(originalExpressionArrayDimensions[i]));
        }
        text.append("]");
        if (caretOffset < 0) {
          caretOffset -= 2;
        }
      }

      newExpression = (PsiNewExpression)factory.createExpressionFromText(text.toString(), originalExpression);
      selection = caretOffset < 0 ? new UnfairTextRange(caretOffset, caretOffset + 1) : null;
    }
    else {
      final PsiAnonymousClass anonymousClass = originalExpression.getAnonymousClass();
      newExpression = (PsiNewExpression)factory.createExpressionFromText("new " + toType.getCanonicalText() + "()" + (anonymousClass != null ? "{}" : ""), originalExpression);
      PsiExpressionList argumentList = originalExpression.getArgumentList();
      if (argumentList == null) return;
      final PsiExpressionList newArgumentList = newExpression.getArgumentList();
      assert newArgumentList != null;
      newArgumentList.replace(commentTracker.markUnchanged(argumentList));

      if (anonymousClass != null) {
        PsiAnonymousClass newAnonymousClass = newExpression.getAnonymousClass();
        assert newAnonymousClass != null;
        final PsiElement lBrace = anonymousClass.getLBrace();
        assert lBrace != null;
        final PsiElement childInside = lBrace.getNextSibling();
        if (childInside != null) {
          PsiElement element = childInside;
          do {
            commentTracker.markUnchanged(element);
          }
          while ((element = element.getNextSibling()) != null);

          final PsiElement rBrace = anonymousClass.getRBrace();
          assert rBrace != null;
          newAnonymousClass.addRange(childInside, rBrace.getPrevSibling());
        }
      }
      selection = null;
      caretOffset = -1;
    }
    PsiNewExpression element = (PsiNewExpression)commentTracker.replaceAndRestoreComments(originalExpression, newExpression);
    if (PsiDiamondTypeUtil.canCollapseToDiamond(element, element, toType)) {
      final PsiJavaCodeReferenceElement reference = element.getClassOrAnonymousClassReference();
      assert reference != null;
      RemoveRedundantTypeArgumentsUtil.replaceExplicitWithDiamond(reference.getParameterList());
    }
    editor.getCaretModel().moveToOffset(element.getTextRange().getEndOffset() + caretOffset);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    if (selection != null) {
      selection = selection.shiftRight(element.getTextRange().getEndOffset());
      editor.getSelectionModel().setSelection(selection.getStartOffset(), selection.getEndOffset());
    }
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  public static void register(@NotNull HighlightInfo.Builder highlightInfo, PsiExpression expression, PsiType lType) {
    expression = PsiUtil.deparenthesizeExpression(expression);
    if (!(expression instanceof PsiNewExpression)) return;
    final PsiType rType = expression.getType();
    PsiType newType = lType;
    if (rType instanceof PsiClassType && newType instanceof PsiClassType) {
      final PsiClassType.ClassResolveResult rResolveResult = ((PsiClassType)rType).resolveGenerics();
      PsiClass rClass = rResolveResult.getElement();
      if (rClass instanceof PsiAnonymousClass) {
        rClass = ((PsiAnonymousClass)rClass).getBaseClassType().resolve();
      }
      if (rClass != null) {
        final PsiClassType.ClassResolveResult lResolveResult = ((PsiClassType)newType).resolveGenerics();
        final PsiClass lClass = lResolveResult.getElement();
        if (lClass != null) {
          PsiSubstitutor substitutor = getInheritorSubstitutorForNewExpression(lClass, rClass, lResolveResult.getSubstitutor(), expression);
          if (substitutor != null) {
            newType = JavaPsiFacade.getElementFactory(lClass.getProject()).createType(rClass, substitutor);
          }
        }
      }
    }
    final PsiClass aClass = PsiTypesUtil.getPsiClass(newType);
    if (aClass != null && (aClass.isEnum() || aClass.isAnnotationType())) return;
    PsiNewExpression newExpression = (PsiNewExpression)expression;
    final PsiJavaCodeReferenceElement reference = newExpression.getClassReference();
    if (reference != null && reference.getText().equals(newType.getCanonicalText())) return;
    highlightInfo.registerFix(new ChangeNewOperatorTypeFix(newType, newExpression), null, null, null, null);
  }

  /* Guesswork
  */
  @Nullable
  private static PsiSubstitutor getInheritorSubstitutorForNewExpression(PsiClass baseClass, PsiClass inheritor,
                                                                        PsiSubstitutor baseSubstitutor, PsiElement context) {
    final Project project = baseClass.getProject();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    final PsiResolveHelper resolveHelper = facade.getResolveHelper();
    PsiSubstitutor superSubstitutor = TypeConversionUtil.getClassSubstitutor(baseClass, inheritor, PsiSubstitutor.EMPTY);
    if (superSubstitutor == null) return null;
    PsiSubstitutor inheritorSubstitutor = PsiSubstitutor.EMPTY;
    for (PsiTypeParameter inheritorParameter : PsiUtil.typeParametersIterable(inheritor)) {
      for (PsiTypeParameter baseParameter : PsiUtil.typeParametersIterable(baseClass)) {
        final PsiType substituted = superSubstitutor.substitute(baseParameter);
        PsiType arg = baseSubstitutor.substitute(baseParameter);
        if (arg instanceof PsiWildcardType) arg = ((PsiWildcardType)arg).getBound();
        PsiType substitution =
          resolveHelper.getSubstitutionForTypeParameter(inheritorParameter, substituted, arg, true, PsiUtil.getLanguageLevel(context));
        if (PsiType.NULL.equals(substitution)) continue;
        if (substitution == null) {
          return facade.getElementFactory().createRawSubstitutor(inheritor);
        }
        inheritorSubstitutor = inheritorSubstitutor.put(inheritorParameter, substitution);
        break;
      }
    }

    return inheritorSubstitutor;
  }

  @Override
  public @NotNull FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    return new ChangeNewOperatorTypeFix(myType, PsiTreeUtil.findSameElementInCopy(myExpression, target));
  }
}
