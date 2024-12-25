// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.util;

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * The ModifierListUtil class provides utility methods for working with PsiModifierList objects.
 */
public final class ModifierListUtil {
  private ModifierListUtil() {
  }

  /**
   * @param modifierList              the original modifier list to create a sorted modifier list from
   * @param customAnnotationComparator the custom comparator to use for sorting annotations (nullable)
   * @param allowAnnotationTypeBeInAnyAllowedPlace if true annotation type can be in any allowed place
   * @return the sorted modifier (based on {@link  ModifierListUtil#isTypeAnnotationAlwaysUseWithType(PsiElement)} and
   * {@link ModifierComparator}) list as a PsiModifierList object
   */
  public static @Nullable PsiModifierList createSortedModifierList(@NotNull PsiModifierList modifierList,
                                                                   @Nullable Comparator<? super PsiAnnotation> customAnnotationComparator,
                                                                   boolean allowAnnotationTypeBeInAnyAllowedPlace) {
    final @NonNls String text = String.join(" ", getSortedModifiers(modifierList, customAnnotationComparator, allowAnnotationTypeBeInAnyAllowedPlace));
    return createNewModifierList(modifierList, text);
  }


  private static @Nullable PsiModifierList createNewModifierList(@NotNull PsiModifierList oldModifierList, @NotNull String newModifiersText) {
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(oldModifierList.getProject());
    PsiElement parent = oldModifierList.getParent();
    if (parent instanceof PsiRequiresStatement) {
      String text = ("requires " + newModifiersText + " x;").trim();
      PsiRequiresStatement statement = (PsiRequiresStatement)factory.createModuleStatementFromText(text, oldModifierList);
      return statement.getModifierList();
    }
    else if (parent instanceof PsiClass) {
      String text = (newModifiersText + " class X {}").trim();
      PsiDeclarationStatement declarationStatement =
        (PsiDeclarationStatement)factory.createStatementFromText(text, oldModifierList);
      return ((PsiClass)declarationStatement.getDeclaredElements()[0]).getModifierList();
    }
    else {
      String text = (newModifiersText + " void x() {}").trim();
      PsiMethod method = factory.createMethodFromText(text, oldModifierList);
      return method.getModifierList();
    }
  }

  /**
   * @param element the PsiElement to check
   * @return true if the element is a method with void return type, false otherwise
   */
  public static boolean isMethodWithVoidReturnType(@NotNull PsiElement element) {
    return element instanceof PsiMethod && PsiTypes.voidType().equals(((PsiMethod)element).getReturnType());
  }

  /**
   * @param modifierList                           the original modifier list to create a sorted modifier list from
   * @param customAnnotationComparator             the custom comparator to use for sorting annotations (nullable)
   * @param allowAnnotationTypeBeInAnyAllowedPlace if true annotation type can be in any allowed place
   * @return the sorted list of modifiers, represented as a list of strings
   * @see ModifierListUtil#createSortedModifierList(PsiModifierList, Comparator, boolean)
   */
  public static List<String> getSortedModifiers(@NotNull PsiModifierList modifierList,
                                                @Nullable Comparator<? super PsiAnnotation> customAnnotationComparator,
                                                boolean allowAnnotationTypeBeInAnyAllowedPlace) {
    final List<String> modifiers = new SmartList<>();
    final List<PsiAnnotation> typeAnnotations = new SmartList<>();
    final List<PsiAnnotation> annotations = new SmartList<>();
    for (PsiElement child : modifierList.getChildren()) {
      if (child instanceof PsiJavaToken) {
        modifiers.add(child.getText());
      }
      else if (child instanceof PsiAnnotation annotation) {
        if (PsiImplUtil.isTypeAnnotation(child) && !isMethodWithVoidReturnType(modifierList.getParent())) {
          final PsiAnnotation.TargetType[] targets = AnnotationTargetUtil.getTargetsForLocation(annotation.getOwner());
          PsiClass annotationClass = annotation.resolveAnnotationType();
          Set<PsiAnnotation.TargetType> annotationAllowedTypes = EnumSet.noneOf(PsiAnnotation.TargetType.class);
          if (annotationClass != null) {
            Set<PsiAnnotation.TargetType> annotationTargets = AnnotationTargetUtil.getAnnotationTargets(annotationClass);
            if (annotationTargets != null) {
              annotationAllowedTypes.addAll(annotationTargets);
            }
          }
          annotationAllowedTypes.removeAll(Arrays.stream(targets).collect(Collectors.toSet()));
          if (isTypeAnnotationAlwaysUseWithType(annotation) ||
              (allowAnnotationTypeBeInAnyAllowedPlace && !modifiers.isEmpty()) ||
              AnnotationTargetUtil.findAnnotationTarget(annotation, targets[0]) == PsiAnnotation.TargetType.UNKNOWN ||
              (annotationAllowedTypes.size() == 1 && annotationAllowedTypes.contains(PsiAnnotation.TargetType.TYPE_USE))) {
            typeAnnotations.add(annotation);
            continue;
          }
        }
        annotations.add(annotation);
      }
    }
    modifiers.sort(new ModifierComparator());
    final List<String> result = new SmartList<>();
    if (customAnnotationComparator != null) {
      annotations.sort(customAnnotationComparator);
      typeAnnotations.sort(customAnnotationComparator);
    }
    result.addAll(ContainerUtil.map(annotations, a -> a.getText()));
    result.addAll(modifiers);
    result.addAll(ContainerUtil.map(typeAnnotations, a -> a.getText()));
    return result;
  }

  /**
   * @param psiElement the PsiElement to check
   * @return true if the type annotation should always be used direcly before the type, false otherwise
   */
  public static boolean isTypeAnnotationAlwaysUseWithType(@NotNull PsiElement psiElement) {
    return JavaCodeStyleSettings.getInstance(psiElement.getContainingFile()).GENERATE_USE_TYPE_ANNOTATION_BEFORE_TYPE;
  }

  /**
   * A comparator for sorting keyword modifiers.
   */
  public static class ModifierComparator implements Comparator<String> {

    private static final @NonNls String[] s_modifierOrder =
      {
        PsiModifier.PUBLIC,
        PsiModifier.PROTECTED,
        PsiModifier.PRIVATE,
        PsiModifier.ABSTRACT,
        PsiModifier.DEFAULT,
        PsiModifier.STATIC,
        PsiModifier.FINAL,
        PsiModifier.TRANSIENT,
        PsiModifier.VOLATILE,
        PsiModifier.SYNCHRONIZED,
        PsiModifier.NATIVE,
        PsiModifier.STRICTFP,
        PsiModifier.TRANSITIVE,
        PsiModifier.SEALED,
        PsiModifier.NON_SEALED
      };


    @Override
    public int compare(String modifier1, String modifier2) {
      if (modifier1.equals(modifier2)) return 0;
      for (String modifier : s_modifierOrder) {
        if (modifier.equals(modifier1)) {
          return -1;
        }
        else if (modifier.equals(modifier2)) {
          return 1;
        }
      }
      return modifier1.compareTo(modifier2);
    }
  }
}
