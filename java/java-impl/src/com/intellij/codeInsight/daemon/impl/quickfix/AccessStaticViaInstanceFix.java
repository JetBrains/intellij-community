// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMessageUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiBasedModCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.CodeBlockSurrounder;
import com.siyeh.ig.psiutils.SideEffectChecker;
import com.siyeh.ig.psiutils.StatementExtractor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public class AccessStaticViaInstanceFix extends PsiBasedModCommandAction<PsiReferenceExpression> {
  private final @IntentionName String myText;
  private final @NotNull ThreeState myKeepSideEffects;

  /**
   * @deprecated onTheFly parameter is ignored. Use another constructor.
   */
  @Deprecated
  public AccessStaticViaInstanceFix(@NotNull PsiReferenceExpression expression,
                                    @NotNull JavaResolveResult result,
                                    @SuppressWarnings("unused") boolean onTheFly) {
    this(expression, result);
  }

  public AccessStaticViaInstanceFix(@NotNull PsiReferenceExpression expression, @NotNull JavaResolveResult result) {
    super(expression);
    myText = calcText((PsiMember)Objects.requireNonNull(result.getElement()), result.getSubstitutor());
    myKeepSideEffects = ThreeState.UNSURE;
  }

  private AccessStaticViaInstanceFix(@NotNull PsiReferenceExpression expression, boolean keepSideEffects) {
    super(expression);
    myText = "";
    myKeepSideEffects = ThreeState.fromBoolean(keepSideEffects);
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiReferenceExpression ref) {
    return switch (myKeepSideEffects) {
      case YES -> {
        final PsiExpression qualifierExpression = ref.getQualifierExpression();
        List<PsiExpression> sideEffects = qualifierExpression == null ?
                                          List.of() : SideEffectChecker.extractSideEffectExpressions(qualifierExpression);
        yield Presentation.of(JavaBundle.message("intention.family.name.extract.possible.side.effects"))
          .withHighlighting(ContainerUtil.map2Array(sideEffects, TextRange.EMPTY_ARRAY, expression -> expression.getTextRange()));
      }
      case NO -> Presentation.of(JavaBundle.message("intention.family.name.delete.possible.side.effects"));
      case UNSURE -> Presentation.of(myText);
    };
  }

  private static @IntentionName String calcText(PsiMember member, PsiSubstitutor substitutor) {
    PsiClass aClass = member.getContainingClass();
    if (aClass == null) return "";
    return QuickFixBundle.message("access.static.via.class.reference.text",
                                  HighlightMessageUtil.getSymbolName(member, substitutor, PsiFormatUtilBase.SHOW_TYPE),
                                  HighlightUtil.formatClass(aClass, false),
                                  HighlightUtil.formatClass(aClass, false));
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("access.static.via.class.reference.family");
  }

  @Override
  protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull PsiReferenceExpression ref) {
    final PsiExpression qualifierExpression = ref.getQualifierExpression();
    List<PsiExpression> sideEffects = myKeepSideEffects == ThreeState.NO || qualifierExpression == null ?
                                      List.of() : SideEffectChecker.extractSideEffectExpressions(qualifierExpression);
    if (sideEffects.isEmpty()) {
      return ModCommand.psiUpdate(ref, r -> invoke(r, List.of()));
    } else {
      if (myKeepSideEffects == ThreeState.UNSURE) {
        if (!CodeBlockSurrounder.canSurround(ref)) {
          return ModCommand.psiUpdate(ref, r -> invoke(r, List.of()));
        }
        return ModCommand.chooseAction(QuickFixBundle.message("access.static.via.class.reference.title"), 
                                       new AccessStaticViaInstanceFix(ref, true), new AccessStaticViaInstanceFix(ref, false));
      }
      return ModCommand.psiUpdate(ref, (r, updater) -> invoke(r, ContainerUtil.map(sideEffects, updater::getWritable)));
    }
  }
  
  private static void invoke(@NotNull PsiReferenceExpression ref, @NotNull List<PsiExpression> sideEffects) {
    PsiElement element = ref.resolve();
    if (!(element instanceof PsiMember member)) return;

    PsiClass containingClass = member.getContainingClass();
    if (containingClass == null || containingClass instanceof PsiAnonymousClass || containingClass instanceof PsiImplicitClass) return;
    Project project = member.getProject();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    PsiExpression qualifierExpression = ref.getQualifierExpression();
    if (!sideEffects.isEmpty()) {
      Objects.requireNonNull(qualifierExpression);
      CodeBlockSurrounder surrounder = CodeBlockSurrounder.forExpression(ref);
      if (surrounder != null) {
        PsiStatement[] statements = StatementExtractor.generateStatements(sideEffects, qualifierExpression);
        CodeBlockSurrounder.SurroundResult result = surrounder.surround();
        ref = (PsiReferenceExpression)result.getExpression();
        qualifierExpression = ref.getQualifierExpression();
        PsiStatement anchor = result.getAnchor();
        if (statements.length > 0) {
          BlockUtils.addBefore(anchor, statements);
        }
      }
    }
    PsiElement newQualifier = factory.createReferenceExpression(containingClass);
    if (qualifierExpression != null) {
      newQualifier = qualifierExpression.replace(newQualifier);
    }
    else {
      ref.setQualifierExpression((PsiExpression)newQualifier);
      newQualifier = Objects.requireNonNull(ref.getQualifierExpression());
    }
    PsiElement qualifiedWithClassName = ref.copy();
    if (ref.getTypeParameters().length == 0 &&
        !(containingClass.isInterface() && !containingClass.equals(PsiTreeUtil.getParentOfType(ref, PsiClass.class)))) {
      newQualifier.delete();
      if (ref.resolve() != member) {
        ref.replace(qualifiedWithClassName);
      }
    }
  }
}
