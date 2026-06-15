// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.daemon.impl.analysis.DefaultJavaErrorFixProvider;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiDeconstructionPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceCodeFragment;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Will be eventually removed. Do not add new code here.
 * The new fixes should be registered either in {@link DefaultJavaErrorFixProvider} or in 
 * {@link com.intellij.codeInspection.AdditionalJavaErrorFixProvider} (if you need more dependencies).
 */
@ApiStatus.Obsolete
public class DefaultQuickFixProvider extends UnresolvedReferenceQuickFixProvider<PsiJavaCodeReferenceElement> {
  @Override
  public void registerFixes(@NotNull PsiJavaCodeReferenceElement ref, @NotNull QuickFixActionRegistrar registrar) {
    PsiFile containingFile = ref.getContainingFile();
    if (containingFile instanceof PsiJavaCodeReferenceCodeFragment fragment && !fragment.isClassesAccepted()) {
      return;
    }
    List<IntentionAction> fixes = new ArrayList<>();
    OrderEntryFix.registerFixes(ref, fixes);
    for (IntentionAction fix : fixes) {
      registrar.register(fix);
    }
    if (PsiUtil.isModuleFile(containingFile)) {
      registrar.register(new CreateServiceImplementationClassFix(ref));
      registrar.register(new CreateServiceInterfaceOrClassFix(ref));
      return;
    }

    PsiElement refParent = ref.getParent();
    if (!(refParent instanceof PsiMethodCallExpression)) {
      registrar.register(new StaticImportConstantFix(containingFile, ref));
      registrar.register(new QualifyStaticConstantFix(containingFile, ref));
    }

    MoveClassToModuleFix.registerFixes(registrar, ref);

    if (ref instanceof PsiReferenceExpression refExpr) {
      TextRange fixRange = getFixRange(ref);
      registrar.register(fixRange, new RenameWrongRefFix(refExpr), null);
      PsiExpression qualifier = refExpr.getQualifierExpression();
      if (qualifier != null) {
        AddTypeCastFix.registerFix(registrar, qualifier, ref, fixRange);
      }
      BringVariableIntoScopeFix bringToScope = BringVariableIntoScopeFix.fromReference(refExpr);
      if (bringToScope != null) {
        registrar.register(fixRange, bringToScope.asIntention(), null);
      }
    }

    for (IntentionAction action : createClassActions(ref)) {
      registrar.register(action);
    }

    SurroundWithQuotesAnnotationParameterValueFix.register(registrar, ref);

    if (PsiUtil.isAvailable(JavaFeature.GENERICS, ref)) {
      registrar.register(new CreateTypeParameterFromUsageFix(ref).asIntention());
    }
  }

  private static @NotNull Collection<IntentionAction> createClassActions(@NotNull PsiJavaCodeReferenceElement ref) {
    Collection<IntentionAction> result = new ArrayList<>();
    PsiElement refParent = ref.getParent();
    if (refParent != null && refParent.getParent() instanceof PsiDeconstructionPattern) {
      result.add(new CreateClassFromUsageFix(ref, CreateClassKind.RECORD));
      result.add(new CreateInnerClassFromUsageFix(ref, CreateClassKind.RECORD));
    }
    else {
      PsiElement parent = PsiTreeUtil.getParentOfType(ref, PsiNewExpression.class, PsiMethod.class);
      PsiExpressionList expressionList = PsiTreeUtil.getParentOfType(ref, PsiExpressionList.class);

      boolean isNewExpression =
        parent instanceof PsiNewExpression &&
        !(refParent instanceof PsiTypeElement) &&
        (expressionList == null || !PsiTreeUtil.isAncestor(parent, expressionList, false));

      if (isNewExpression) {
        result.add(new CreateClassFromNewFix((PsiNewExpression)parent));
      }
      else {
        result.add(new CreateClassFromUsageFix(ref, CreateClassKind.CLASS));
      }

      result.add(new CreateClassFromUsageFix(ref, CreateClassKind.INTERFACE));
      if (PsiUtil.isAvailable(JavaFeature.ENUMS, ref)) {
        result.add(new CreateClassFromUsageFix(ref, CreateClassKind.ENUM));
      }
      if (PsiUtil.isAvailable(JavaFeature.ANNOTATIONS, ref)) {
        result.add(new CreateClassFromUsageFix(ref, CreateClassKind.ANNOTATION));
      }
      if (PsiUtil.isAvailable(JavaFeature.RECORDS, ref)) {
        if (isNewExpression) {
          result.add(new CreateRecordFromNewFix((PsiNewExpression)parent));
        }
        else {
          result.add(new CreateClassFromUsageFix(ref, CreateClassKind.RECORD));
        }
      }

      if (isNewExpression) {
        result.add(new CreateInnerClassFromNewFix((PsiNewExpression)parent));
        if (PsiUtil.isAvailable(JavaFeature.RECORDS, ref) && ((PsiNewExpression)parent).getQualifier() == null) {
          result.add(new CreateInnerRecordFromNewFix((PsiNewExpression)parent));
        }
      }
      else {
        result.add(new CreateInnerClassFromUsageFix(ref, CreateClassKind.CLASS));
      }
    }
    return result;
  }

  @Override
  public @NotNull Class<PsiJavaCodeReferenceElement> getReferenceClass() {
    return PsiJavaCodeReferenceElement.class;
  }

  private static @NotNull TextRange getFixRange(@NotNull PsiElement element) {
    PsiElement nextSibling = element.getNextSibling();
    TextRange range = element.getTextRange();
    if (PsiUtil.isJavaToken(nextSibling, JavaTokenType.SEMICOLON)) {
      return range.grown(1);
    }
    return range;
  }
}