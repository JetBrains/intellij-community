// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.JavaProjectCodeInsightSettings;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.VariableLookupItem;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class JavaStaticMemberProcessor extends StaticMemberProcessor {
  private final PsiElement myOriginalPosition;

  public JavaStaticMemberProcessor(@NotNull CompletionParameters parameters) {
    super(parameters.getPosition());
    myOriginalPosition = parameters.getOriginalPosition();
    final PsiFile file = parameters.getPosition().getContainingFile();
    if (file instanceof PsiJavaFile) {
      final PsiImportList importList = ((PsiJavaFile)file).getImportList();
      if (importList != null) {
        for (PsiImportStaticStatement statement : importList.getImportStaticStatements()) {
          PsiClass aClass = statement.resolveTargetClass();
          if (aClass != null) {
            importMembersOf(aClass);
          }
        }
      }
    }
    Project project = parameters.getPosition().getProject();
    JavaProjectCodeInsightSettings codeInsightSettings = JavaProjectCodeInsightSettings.getSettings(project);
    JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(project);
    GlobalSearchScope resolveScope = parameters.getOriginalFile().getResolveScope();
    for (String name : codeInsightSettings.getAllIncludedAutoStaticNames()) {
      PsiClass aClass = javaPsiFacade.findClass(name, resolveScope);
      if (aClass != null && isAccessibleClass(aClass)) {
        importMembersOf(aClass);
      }
      else {
        String shortMemberName = StringUtil.getShortName(name);
        String containingMemberName = StringUtil.getPackageName(name);
        if (containingMemberName.isEmpty() || shortMemberName.isEmpty()) continue;
        PsiClass containingClass = javaPsiFacade.findClass(containingMemberName, resolveScope);
        if (containingClass != null && isAccessibleClass(containingClass)) {
          for (PsiMethod method : containingClass.findMethodsByName(shortMemberName, true)) {
            if (method.hasModifierProperty(PsiModifier.STATIC)) {
              importMember(method);
            }
          }
          PsiField psiField = containingClass.findFieldByName(shortMemberName, true);
          if (psiField != null && psiField.hasModifierProperty(PsiModifier.STATIC)) {
            importMember(psiField);
          }
        }
      }
    }
  }

  private boolean isAccessibleClass(@NotNull PsiClass importFromClass) {
    boolean importFromDefaultPackage = importFromClass.getContainingFile() instanceof PsiJavaFile javaFile && javaFile.getPackageName().isBlank();
    if (importFromDefaultPackage) {
      boolean targetClassInDefaultPackage = myOriginalPosition.getContainingFile() instanceof PsiJavaFile targetClass && targetClass.getPackageName().isBlank();
      if(!targetClassInDefaultPackage) {
        return false;
      }
    }
    return true;
  }

  @Override
  protected @Nullable LookupElement createLookupElement(@NotNull PsiMember member, final @NotNull PsiClass containingClass, boolean shouldImport) {
    shouldImport |= myOriginalPosition != null && PsiTreeUtil.isAncestor(containingClass, myOriginalPosition, false);

    if (!PsiNameHelper.getInstance(member.getProject()).isIdentifier(member.getName(), PsiUtil.getLanguageLevel(getPosition()))) {
      return null;
    }

    PsiReference ref = createReferenceToMemberName(member);
    if (ref == null) return null;

    if (ref instanceof PsiReferenceExpression) {
      JavaResolveResult[] results = ((PsiReferenceExpression)ref).multiResolve(true);
      PsiClass memberContainingClass = member.getContainingClass();
      boolean shouldBeAutoImported = memberContainingClass != null &&
                                     member.hasModifierProperty(PsiModifier.STATIC) &&
                                     member.getName() != null &&
                                     JavaCodeStyleManager.getInstance(member.getProject())
                                       .isStaticAutoImportName(memberContainingClass.getQualifiedName() + "." + member.getName());
      if (shouldBeAutoImported && member.getContainingFile() instanceof PsiJavaFile javaFile &&
          javaFile.getPackageName().isBlank()) {
        shouldImport = false;
      }
      else if (results.length > 0) {
        if (shouldBeAutoImported) {
          shouldImport = !ContainerUtil.exists(results, result -> {
            PsiElement element = result.getElement();
            return element instanceof PsiModifierListOwner modifierListOwner &&
                   modifierListOwner.hasModifierProperty(PsiModifier.STATIC) ||
                   element instanceof PsiMember psiMember &&
                   member.getName().equals(psiMember.getName());
          });
        }
        else {
          shouldImport = false;
        }
      }
    }

    if (member instanceof PsiMethod) {
      return AutoCompletionPolicy.NEVER_AUTOCOMPLETE.applyPolicy(getMethodCallElement(shouldImport, List.of((PsiMethod)member)));
    }
    return AutoCompletionPolicy.NEVER_AUTOCOMPLETE.applyPolicy(new VariableLookupItem((PsiField)member, shouldImport) {
      @Override
      public void handleInsert(@NotNull InsertionContext context) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed(JavaCompletionFeatures.GLOBAL_MEMBER_NAME);

        super.handleInsert(context);
      }
    }.qualifyIfNeeded(ObjectUtils.tryCast(getPosition().getParent(), PsiJavaCodeReferenceElement.class), containingClass));
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

    final JavaMethodCallElement element = getMethodCallElement(shouldImport, overloads);
    JavaCompletionUtil.putAllMethods(element, overloads);
    return element;
  }

  protected @NotNull JavaMethodCallElement getMethodCallElement(boolean shouldImport, List<? extends PsiMethod> members) {
    return new GlobalMethodCallElement(members.get(0), shouldImport, members.size()>1);
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
