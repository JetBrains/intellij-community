/*
 * Copyright 2003-2023 Dave Griffith, Bas Leijdekkers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandBatchQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.IncompleteModelUtil;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class ProtectedMemberInFinalClassInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Override
  protected @NotNull LocalQuickFix buildFix(Object... infos) {
    return new WeakenVisibilityFix();
  }

  @Override
  public @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("protected.member.in.final.class.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ProtectedMemberInFinalClassVisitor();
  }

  public static boolean canBePrivate(@NotNull PsiMember member, @NotNull PsiModifierList modifierList) {
    final PsiModifierList modifierListCopy = (PsiModifierList)modifierList.copy();
    modifierListCopy.setModifierProperty(PsiModifier.PRIVATE, true);
    return ReferencesSearch.search(member, member.getUseScope()).allMatch(
      reference -> JavaResolveUtil.isAccessible(member, member.getContainingClass(), modifierListCopy, reference.getElement(),
                                                WeakenVisibilityFix.findAccessObjectClass(reference, member), null));
  }

  private static class WeakenVisibilityFix extends ModCommandBatchQuickFix {

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("weaken.visibility.quickfix");
    }

    @Override
    public @NotNull ModCommand perform(@NotNull Project project, @NotNull List<ProblemDescriptor> descriptors) {
      return ModCommand.psiUpdate(ActionContext.from(descriptors.get(0)), updater -> {
        List<FixData> elements =
          ContainerUtil.map(descriptors, descriptor -> prepareDataForFix(updater, descriptor.getPsiElement()));
        elements.forEach(FixData::apply);
      });
    }

    private static FixData prepareDataForFix(@NotNull ModPsiUpdater updater, PsiElement element) {
      final PsiElement parent = element.getParent();
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiMember member)) return null;
      final PsiModifierList modifierList = member.getModifierList();
      if (modifierList == null) return null;
      boolean canBePrivate = canBePrivate(member, modifierList);
      return new FixData(canBePrivate, updater.getWritable(modifierList));
    }

    private record FixData(boolean canBePrivate, PsiModifierList modifierList) {
      void apply() {
        modifierList.setModifierProperty(canBePrivate ? PsiModifier.PRIVATE : PsiModifier.PACKAGE_LOCAL, true);
      }
    }

    private static @Nullable PsiClass findAccessObjectClass(@NotNull PsiReference reference, @NotNull PsiMember member) {
      if (!(reference instanceof PsiJavaCodeReferenceElement)) return null;
      PsiElement qualifier = ((PsiJavaCodeReferenceElement)reference).getQualifier();
      if (!(qualifier instanceof PsiExpression)) return null;
      PsiClass accessObjectClass = null;
      JavaResolveResult accessClass = PsiUtil.getAccessObjectClass((PsiExpression)qualifier);
      PsiElement element = accessClass.getElement();
      if (element instanceof PsiTypeParameter) {
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(member.getContainingFile().getProject());
        PsiClassType type = factory.createType((PsiTypeParameter)element);
        PsiType accessType = accessClass.getSubstitutor().substitute(type);
        if (accessType instanceof PsiArrayType) {
          LanguageLevel languageLevel = PsiUtil.getLanguageLevel(member.getContainingFile());
          accessObjectClass = factory.getArrayClass(languageLevel);
        }
        else if (accessType instanceof PsiClassType) {
          accessObjectClass = ((PsiClassType)accessType).resolve();
        }
      }
      else if (element instanceof PsiClass) {
        accessObjectClass = (PsiClass)element;
      }
      return accessObjectClass;
    }
  }

  private static class ProtectedMemberInFinalClassVisitor extends BaseInspectionVisitor {

    private void checkMember(@NotNull PsiMember member) {
      if (!member.hasModifierProperty(PsiModifier.PROTECTED)) {
        return;
      }
      final PsiClass containingClass = member.getContainingClass();
      if (containingClass == null || !containingClass.hasModifierProperty(PsiModifier.FINAL)) {
        return;
      }
      if (containingClass.hasModifierProperty(PsiModifier.PROTECTED)) {
        return;
      }
      if (!IncompleteModelUtil.isHierarchyResolved(containingClass)) {
        return;
      }
      if (member instanceof PsiMethod method && !method.isConstructor() &&
          !PsiSuperMethodImplUtil.getHierarchicalMethodSignature(method).getSuperSignatures().isEmpty()) {
        return;
      }
      registerModifierError(PsiModifier.PROTECTED, member);
    }

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      checkMember(method);
    }

    @Override
    public void visitField(@NotNull PsiField field) {
      checkMember(field);
    }
  }
}