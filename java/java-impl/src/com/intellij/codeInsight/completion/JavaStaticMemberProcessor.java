// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.VariableLookupItem;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
* @author peter
*/
public class JavaStaticMemberProcessor extends StaticMemberProcessor {
  private final PsiElement myOriginalPosition;
  private final PsiElement myPosition;

  public JavaStaticMemberProcessor(CompletionParameters parameters) {
    super(parameters.getPosition());
    myOriginalPosition = parameters.getOriginalPosition();
    myPosition = parameters.getPosition();
    final PsiFile file = myPosition.getContainingFile();
    if (file instanceof PsiJavaFile) {
      final PsiImportList importList = ((PsiJavaFile)file).getImportList();
      if (importList != null) {
        for (PsiImportStaticStatement statement : importList.getImportStaticStatements()) {
          importMembersOf(statement.resolveTargetClass());
        }
      }
    }
  }

  @Nullable
  @Override
  protected LookupElement createLookupElement(@NotNull PsiMember member, @NotNull final PsiClass containingClass, boolean shouldImport) {
    shouldImport |= myOriginalPosition != null && PsiTreeUtil.isAncestor(containingClass, myOriginalPosition, false);

    if (!PsiNameHelper.getInstance(member.getProject()).isIdentifier(member.getName(), PsiUtil.getLanguageLevel(getPosition()))) {
      return null;
    }

    PsiReference ref = createReferenceToMemberName(member);
    if (ref == null) return null;

    if (ref instanceof PsiReferenceExpression && ((PsiReferenceExpression)ref).multiResolve(true).length > 0) {
      shouldImport = false;
    }

    if (member instanceof PsiMethod) {
      return AutoCompletionPolicy.NEVER_AUTOCOMPLETE.applyPolicy(new GlobalMethodCallElement((PsiMethod)member, shouldImport, false));
    }
    return AutoCompletionPolicy.NEVER_AUTOCOMPLETE.applyPolicy(new VariableLookupItem((PsiField)member, shouldImport) {
      @Override
      public void handleInsert(@NotNull InsertionContext context) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed(JavaCompletionFeatures.GLOBAL_MEMBER_NAME);

        super.handleInsert(context);
      }
    }.qualifyIfNeeded(ObjectUtils.tryCast(myPosition.getParent(), PsiJavaCodeReferenceElement.class)));
  }

  private PsiReference createReferenceToMemberName(@NotNull PsiMember member) {
    String exprText = member.getName() + (member instanceof PsiMethod ? "()" : "");
    return JavaPsiFacade.getElementFactory(member.getProject()).createExpressionFromText(exprText, myOriginalPosition).findReferenceAt(0);
  }

  @Override
  protected LookupElement createLookupElement(@NotNull List<? extends PsiMethod> overloads,
                                              @NotNull PsiClass containingClass,
                                              boolean shouldImport) {
    shouldImport |= myOriginalPosition != null && PsiTreeUtil.isAncestor(containingClass, myOriginalPosition, false);

    final JavaMethodCallElement element = new GlobalMethodCallElement(overloads.get(0), shouldImport, true);
    JavaCompletionUtil.putAllMethods(element, overloads);
    return element;
  }

  private static class GlobalMethodCallElement extends JavaMethodCallElement {
    GlobalMethodCallElement(PsiMethod member, boolean shouldImport, boolean mergedOverloads) {
      super(member, shouldImport, mergedOverloads);
    }

    @Override
    public void handleInsert(@NotNull InsertionContext context) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed(JavaCompletionFeatures.GLOBAL_MEMBER_NAME);

      super.handleInsert(context);
    }
  }
}
