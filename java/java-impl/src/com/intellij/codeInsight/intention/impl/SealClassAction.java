// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.codeInspection.PsiUpdateModCommandAction;
import com.intellij.java.JavaBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.FunctionalExpressionSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.SealedUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SealClassAction extends PsiUpdateModCommandAction<PsiClass> {
  public SealClassAction() {
    super(PsiClass.class);
  }
  
  @Override
  @NotNull
  public String getFamilyName() {
    return JavaBundle.message("intention.family.name.make.sealed");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiClass element) {
    return isAvailable(context, element) ? super.getPresentation(context, element) : null;
  }

  private static boolean isAvailable(@NotNull ActionContext context, @NotNull PsiClass aClass) {
    if (!HighlightingFeature.SEALED_CLASSES.isAvailable(aClass)) return false;
    int offset = context.offset();
    PsiElement lBrace = aClass.getLBrace();
    if (lBrace == null) return false;
    if (offset >= lBrace.getTextRange().getStartOffset()) return false;
    if (aClass.hasModifierProperty(PsiModifier.SEALED)) return false;
    if (aClass.getPermitsList() != null) return false;
    if (aClass.getModifierList() == null) return false;
    if (aClass.hasModifierProperty(PsiModifier.FINAL)) return false;
    if (PsiUtil.isLocalOrAnonymousClass(aClass)) return false;
    if (!(aClass.getContainingFile() instanceof PsiJavaFile)) return false;
    return !aClass.hasAnnotation(CommonClassNames.JAVA_LANG_FUNCTIONAL_INTERFACE);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiClass aClass, @NotNull ModPsiUpdater updater) {
    sealClass(context.project(), updater, aClass);
  }

  static void sealClass(@NotNull Project project, ModPsiUpdater updater, PsiClass psiClass) {
    PsiJavaFile parentFile = (PsiJavaFile)psiClass.getContainingFile();
    if (psiClass.isInterface()) {
      if (FunctionalExpressionSearch.search(psiClass).findFirst() != null) {
        updater.cancel(JavaBundle.message("intention.error.make.sealed.class.is.used.in.functional.expression"));
        return;
      }
    }

    PsiJavaModule module = JavaModuleGraphUtil.findDescriptorByElement(psiClass);

    List<PsiClass> inheritors = new ArrayList<>();
    Ref<String> message = new Ref<>();
    ClassInheritorsSearch.search(psiClass, false).forEach(inheritor -> {
      String errorTitle = SealedUtils.checkInheritor(parentFile, module, inheritor);
      if (errorTitle != null) {
        message.set(errorTitle);
        return false;
      }

      inheritors.add(updater.getWritable(inheritor));
      return true;
    });
    if (!message.isNull()) {
      updater.cancel(JavaBundle.message(message.get()));
      return;
    }
    List<String> names = ContainerUtil.map(inheritors, PsiClass::getQualifiedName);
    @PsiModifier.ModifierConstant String modifier;
    if (!names.isEmpty()) {
      modifier = PsiModifier.SEALED;
      if (shouldCreatePermitsList(inheritors, parentFile)) {
        addPermitsClause(project, psiClass, names);
      }
      setInheritorsModifiers(inheritors);
    }
    else {
      if (psiClass.isInterface()) {
        updater.cancel(JavaBundle.message("intention.error.make.sealed.class.interface.has.no.inheritors"));
        return;
      }
      else {
        modifier = PsiModifier.FINAL;
      }
    }
    PsiModifierList modifierList = Objects.requireNonNull(psiClass.getModifierList());
    modifierList.setModifierProperty(modifier, true);
  }

  private static boolean shouldCreatePermitsList(List<PsiClass> inheritors, PsiFile parentFile) {
    return !ContainerUtil.and(inheritors, psiClass -> psiClass.getContainingFile() == parentFile);
  }

  private static void setInheritorsModifiers(List<PsiClass> inheritors) {
    for (PsiClass inheritor : inheritors) {
      PsiModifierList modifierList = inheritor.getModifierList();
      assert modifierList != null; // ensured by absence of anonymous classes
      if (modifierList.hasModifierProperty(PsiModifier.SEALED) ||
          modifierList.hasModifierProperty(PsiModifier.NON_SEALED) ||
          modifierList.hasModifierProperty(PsiModifier.FINAL)) {
        continue;
      }
      modifierList.setModifierProperty(PsiModifier.NON_SEALED, true);
    }
  }

  private static void addPermitsClause(@NotNull Project project, PsiClass aClass, List<String> nonNullNames) {
    String permitsClause = StreamEx.of(nonNullNames).sorted().joining(",", "permits ", "");
    PsiReferenceList permitsList = createPermitsClause(project, permitsClause);
    PsiReferenceList implementsList = Objects.requireNonNull(aClass.getImplementsList());
    aClass.addAfter(permitsList, implementsList);
  }

  @NotNull
  private static PsiReferenceList createPermitsClause(@NotNull Project project, String permitsClause) {
    PsiFileFactory factory = PsiFileFactory.getInstance(project);
    PsiJavaFile javaFile = (PsiJavaFile)factory.createFileFromText(JavaLanguage.INSTANCE, "class __Dummy " + permitsClause + "{}");
    PsiClass newClass = javaFile.getClasses()[0];
    return Objects.requireNonNull(newClass.getPermitsList());
  }
}
