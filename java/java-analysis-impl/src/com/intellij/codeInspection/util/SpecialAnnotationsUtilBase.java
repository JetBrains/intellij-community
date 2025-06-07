// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.util;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.AddToInspectionOptionListFix;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.util.Processor;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class SpecialAnnotationsUtilBase {

  /**
   * Process annotations on a specified {@link PsiModifierListOwner}, skipping common
   * JDK or static analysis annotations
   * 
   * @param owner an annotation owner
   * @param processor a processor, which is executed once per every unknown annotation accepting its fully-qualified name.
   *                  Processing stops if the processor returns false.
   */
  public static void processUnknownAnnotations(@NotNull PsiModifierListOwner owner, @NotNull Processor<? super String> processor) {
    final PsiModifierList modifierList = owner.getModifierList();
    if (modifierList != null) {
      final PsiAnnotation[] psiAnnotations = modifierList.getAnnotations();
      for (PsiAnnotation psiAnnotation : psiAnnotations) {
        final @NonNls String name = psiAnnotation.getQualifiedName();
        if (name == null) continue;
        if (name.startsWith("java.") || //name.startsWith("javax.") ||
            name.startsWith("org.jetbrains.") ||
            NullableNotNullManager.isNullabilityAnnotation(psiAnnotation)) continue;
        if (!processor.process(name)) break;
      }
    }
  }

  /**
   * Creates a list of quick-fixes to add an unknown annotation to 
   * the list of annotations inside the inspection options
   * 
   * @param modifierListOwner an annotation owner
   * @param inspection an instance of the inspection
   * @param listExtractor a function to get the list of annotation fully-qualified names from inspection options
   * @return list of quick-fixes (one per unknown annotation), which modify inspection options
   *          adding the corresponding annotation to the list
   * @param <T> inspection class
   */
  public static <T extends InspectionProfileEntry> List<LocalQuickFix> createAddAnnotationToListFixes(
    @NotNull PsiModifierListOwner modifierListOwner,
    @NotNull T inspection,
    @NotNull Function<@NotNull T, @NotNull List<String>> listExtractor) {
    List<LocalQuickFix> fixes = new ArrayList<>();
    processUnknownAnnotations(modifierListOwner, qualifiedName -> {
      fixes.add(new AddToInspectionOptionListFix<>(
        inspection,
        InspectionGadgetsBundle.message("add.0.to.ignore.if.annotated.by.list.quickfix", qualifiedName),
        qualifiedName,
        listExtractor));
      return true;
    });
    return fixes;
  }
}
