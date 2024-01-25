// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.RemoveRedundantTypeArgumentsUtil;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UnfairTextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
import com.intellij.psi.util.PsiExpressionTrimRenderer;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ChangeNewOperatorTypeFix extends PsiUpdateModCommandAction<PsiNewExpression> {
  private final PsiType myType;

  private ChangeNewOperatorTypeFix(PsiType type, PsiNewExpression expression) {
    super(expression);
    myType = type;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("change.new.operator.type.family");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiNewExpression expression) {
    if (!myType.isValid() || TypeConversionUtil.isPrimitiveAndNotNull(myType)) return null;
    String message = QuickFixBundle.message("change.new.operator.type.text", PsiExpressionTrimRenderer.render(expression),
                                            myType.getPresentableText(), myType instanceof PsiArrayType ? "" : "()");
    return Presentation.of(message);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiNewExpression element, @NotNull ModPsiUpdater updater) {
    PsiNewExpression newExpression;
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(element.getProject());
    int caretOffset;
    TextRange selection;
    CommentTracker commentTracker = new CommentTracker();
    if (myType instanceof PsiArrayType) {
      final PsiExpression[] originalExpressionArrayDimensions = element.getArrayDimensions();
      caretOffset = 0;
      @NonNls StringBuilder text = new StringBuilder("new " + myType.getDeepComponentType().getCanonicalText() + "[");
      if (originalExpressionArrayDimensions.length > 0) {
        text.append(commentTracker.text(originalExpressionArrayDimensions[0]));
      }
      else {
        text.append("0");
        caretOffset = -2;
      }
      text.append("]");
      for (int i = 1; i < myType.getArrayDimensions(); i++) {
        text.append("[");
        if (originalExpressionArrayDimensions.length > i) {
          text.append(commentTracker.text(originalExpressionArrayDimensions[i]));
        }
        text.append("]");
        if (caretOffset < 0) {
          caretOffset -= 2;
        }
      }

      newExpression = (PsiNewExpression)factory.createExpressionFromText(text.toString(), element);
      selection = caretOffset < 0 ? new UnfairTextRange(caretOffset, caretOffset + 1) : null;
    }
    else {
      final PsiAnonymousClass anonymousClass = element.getAnonymousClass();
      newExpression = (PsiNewExpression)factory.createExpressionFromText("new " + myType.getCanonicalText() + "()" + (anonymousClass != null ? "{}" : ""),
                                                                         element);
      PsiExpressionList argumentList = element.getArgumentList();
      if (argumentList != null) {
        final PsiExpressionList newArgumentList = newExpression.getArgumentList();
        assert newArgumentList != null;
        newArgumentList.replace(commentTracker.markUnchanged(argumentList));
      }

      if (anonymousClass != null) {
        PsiAnonymousClass newAnonymousClass = newExpression.getAnonymousClass();
        assert newAnonymousClass != null;
        final PsiElement lBrace = anonymousClass.getLBrace();
        assert lBrace != null;
        final PsiElement childInside = lBrace.getNextSibling();
        if (childInside != null) {
          PsiElement element1 = childInside;
          do {
            commentTracker.markUnchanged(element1);
          }
          while ((element1 = element1.getNextSibling()) != null);

          final PsiElement rBrace = anonymousClass.getRBrace();
          assert rBrace != null;
          newAnonymousClass.addRange(childInside, rBrace.getPrevSibling());
        }
      }
      selection = null;
      caretOffset = -1;
    }
    newExpression = (PsiNewExpression)commentTracker.replaceAndRestoreComments(element, newExpression);
    if (PsiDiamondTypeUtil.canCollapseToDiamond(newExpression, newExpression, myType)) {
      final PsiJavaCodeReferenceElement reference = newExpression.getClassOrAnonymousClassReference();
      assert reference != null;
      RemoveRedundantTypeArgumentsUtil.replaceExplicitWithDiamond(reference.getParameterList());
    }
    if (selection != null) {
      selection = selection.shiftRight(newExpression.getTextRange().getEndOffset());
      updater.select(selection);
    }
    updater.moveCaretTo(newExpression.getTextRange().getEndOffset() + caretOffset);
  }

  public static void register(@NotNull HighlightInfo.Builder highlightInfo, PsiExpression expression, PsiType lType) {
    if (lType instanceof PsiClassType lClassType && lClassType.resolve() instanceof PsiAnonymousClass) return;
    expression = PsiUtil.deparenthesizeExpression(expression);
    if (!(expression instanceof PsiNewExpression newExpression)) return;
    final PsiType rType = expression.getType();
    PsiType newType = lType;
    if (rType instanceof PsiClassType rClassType && newType instanceof PsiClassType newClassType) {
      final PsiClassType.ClassResolveResult rResolveResult = rClassType.resolveGenerics();
      PsiClass rClass = rResolveResult.getElement();
      if (rClass instanceof PsiAnonymousClass) {
        rClass = ((PsiAnonymousClass)rClass).getBaseClassType().resolve();
      }
      if (rClass != null) {
        final PsiClassType.ClassResolveResult lResolveResult = newClassType.resolveGenerics();
        final PsiClass lClass = lResolveResult.getElement();
        if (lClass != null) {
          PsiSubstitutor substitutor = getInheritorSubstitutorForNewExpression(lClass, rClass, lResolveResult.getSubstitutor(), expression);
          if (substitutor != null) {
            newType = JavaPsiFacade.getElementFactory(lClass.getProject()).createType(rClass, substitutor);
          }
        }
      }
    }
    if (rType == null || newType.getCanonicalText().equals(rType.getCanonicalText())) return;
    final PsiClass aClass = PsiTypesUtil.getPsiClass(newType);
    if (aClass != null && (aClass.isEnum() || aClass.isAnnotationType())) return;
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
        if (arg instanceof PsiWildcardType wildcardType) arg = wildcardType.getBound();
        PsiType substitution =
          resolveHelper.getSubstitutionForTypeParameter(inheritorParameter, substituted, arg, true, PsiUtil.getLanguageLevel(context));
        if (PsiTypes.nullType().equals(substitution)) continue;
        if (substitution == null) {
          return facade.getElementFactory().createRawSubstitutor(inheritor);
        }
        inheritorSubstitutor = inheritorSubstitutor.put(inheritorParameter, substitution);
        break;
      }
    }

    return inheritorSubstitutor;
  }
}
