// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention;

import com.intellij.codeInsight.*;
import com.intellij.codeInsight.ExternalAnnotationsManager.AnnotationPlace;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.java.JavaBundle;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * A common action to add an annotation into Java code (either in code or externally).
 * 
 * @see com.intellij.codeInspection.RemoveAnnotationQuickFix
 */
public class AddAnnotationModCommandAction extends PsiBasedModCommandAction<PsiModifierListOwner> {
  private final @NotNull String myAnnotation;
  private final @NotNull String @NotNull [] myAnnotationsToRemove;
  private final @NotNull PsiNameValuePair @NotNull [] myPairs;
  private final @IntentionName String myText;
  private final @NotNull AnnotationPlace myAnnotationPlace;
  private final boolean myExistsTypeUseTarget;
  private final boolean myHasApplicableAnnotations;

  /**
   * @param fqn                 annotation fully qualified name
   * @param modifierListOwner   annotation owner to add an annotation to
   * @param annotationsToRemove fully qualified names of annotations to remove
   */
  public AddAnnotationModCommandAction(@NotNull String fqn,
                                       @NotNull PsiModifierListOwner modifierListOwner,
                                       String @NotNull ... annotationsToRemove) {
    this(fqn, modifierListOwner, PsiNameValuePair.EMPTY_ARRAY, annotationsToRemove);
  }

  /**
   * @param fqn                 annotation fully qualified name
   * @param modifierListOwner   annotation owner to add an annotation to
   * @param values              annotation attributes; must be non-physical PSI
   * @param annotationsToRemove fully qualified names of annotations to remove
   */
  public AddAnnotationModCommandAction(@NotNull String fqn,
                                       @NotNull PsiModifierListOwner modifierListOwner,
                                       PsiNameValuePair @NotNull [] values,
                                       String @NotNull ... annotationsToRemove) {
    this(fqn, modifierListOwner, values, AddAnnotationPsiFix.choosePlace(fqn, modifierListOwner), annotationsToRemove);
  }

  /**
   * @param fqn                 annotation fully qualified name
   * @param modifierListOwner   annotation owner to add an annotation to
   * @param values              annotation attributes; must be non-physical PSI
   * @param place               place where to add an annotation
   * @param annotationsToRemove fully qualified names of annotations to remove
   */
  public AddAnnotationModCommandAction(@NotNull String fqn,
                                       @NotNull PsiModifierListOwner modifierListOwner,
                                       PsiNameValuePair @NotNull [] values,
                                       @NotNull AnnotationPlace place,
                                       String @NotNull ... annotationsToRemove) {
    this(fqn, modifierListOwner, values, place, AddAnnotationPsiFix.calcText(modifierListOwner, fqn), annotationsToRemove);
  }

  /**
   * @param fqn                 annotation fully qualified name
   * @param modifierListOwner   annotation owner to add an annotation to
   * @param values              annotation attributes
   * @param place               place where to add an annotation
   * @param text                name of the action to display
   * @param annotationsToRemove fully qualified names of annotations to remove
   */
  public AddAnnotationModCommandAction(@NotNull String fqn,
                                       @NotNull PsiModifierListOwner modifierListOwner,
                                       PsiNameValuePair @NotNull [] values,
                                       @NotNull AnnotationPlace place,
                                       @IntentionName String text,
                                       String @NotNull ... annotationsToRemove) {
    super(modifierListOwner);
    myText = text;
    myAnnotation = fqn;
    for (PsiNameValuePair value : values) {
      if (value.isPhysical()) {
        throw new IllegalArgumentException("Annotation attributes must be non-physical PSI");
      }
    }
    myPairs = values;
    ObjectUtils.assertAllElementsNotNull(annotationsToRemove);
    myAnnotationsToRemove = annotationsToRemove;
    myAnnotationPlace = place;

    PsiClass annotationClass = JavaPsiFacade.getInstance(modifierListOwner.getProject())
      .findClass(myAnnotation, modifierListOwner.getResolveScope());
    myExistsTypeUseTarget = annotationClass != null &&
                            AnnotationTargetUtil.findAnnotationTarget(annotationClass, PsiAnnotation.TargetType.TYPE_USE) != null;
    PsiAnnotationOwner target = AnnotationTargetUtil.getTarget(modifierListOwner, myExistsTypeUseTarget);
    myHasApplicableAnnotations =
      target != null && ContainerUtil.exists(target.getApplicableAnnotations(), anno -> anno.hasQualifiedName(myAnnotation));
  }

  @Override
  protected boolean isFileAllowed(@NotNull PsiFile file) {
    return true; // Allowed even in non-modifiable files
  }

  public static @Nullable PsiModifierListOwner getContainer(PsiFile file, int offset) {
    return getContainer(file, offset, false);
  }

  public static @Nullable PsiModifierListOwner getContainer(PsiFile file, int offset, boolean availableOnReference) {
    return AddAnnotationPsiFix.getContainer(file, offset, availableOnReference);
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaAnalysisBundle.message("intention.add.annotation.family");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiModifierListOwner element) {
    return isAvailable(element, myAnnotation) ? Presentation.of(myText) : null;
  }

  public static boolean isAvailable(@NotNull PsiModifierListOwner modifierListOwner, @NotNull String annotationFQN) {
    return AddAnnotationPsiFix.isAvailable(modifierListOwner, annotationFQN);
  }

  @Override
  protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull PsiModifierListOwner listOwner) {
    if (myHasApplicableAnnotations) return ModCommand.nop();
    Project project = context.project();
    final var annotationsManager = ModCommandAwareExternalAnnotationsManager.getInstance(project);
    return switch (myAnnotationPlace) {
      case EXTERNAL -> annotationsManager.annotateExternallyModCommand(
        listOwner, myAnnotation, myPairs, Arrays.asList(myAnnotationsToRemove));
      case IN_CODE -> ModCommand.psiUpdate(listOwner, (owner, updater) -> {
        final PsiAnnotationOwner target = AnnotationTargetUtil.getTarget(owner, myExistsTypeUseTarget);
        if (target == null) return;
        AddAnnotationPsiFix.removePhysicalAnnotations(owner, myAnnotationsToRemove);
        PsiAnnotation inserted = AddAnnotationPsiFix.addPhysicalAnnotationTo(addAnnotation(target, myAnnotation), myPairs);
        if (inserted != null) {
          inserted = (PsiAnnotation)JavaCodeStyleManager.getInstance(project).shortenClassReferences(inserted);
          postProcess(inserted, updater);
        }
      });
      case NEED_ASK_USER -> ModCommand.chooseAction(
        JavaBundle.message("external.annotation.place"),
        new AddAnnotationModCommandAction(myAnnotation, listOwner, myPairs, AnnotationPlace.IN_CODE,
                                          JavaBundle.message("external.annotation.place.in.code"), myAnnotationsToRemove),
        new AddAnnotationModCommandAction(myAnnotation, listOwner, myPairs, AnnotationPlace.EXTERNAL,
                                          JavaBundle.message("external.annotation.place.external"), myAnnotationsToRemove)
      );
      case NOWHERE -> ModCommand.nop();
    };
  }

  /**
   * Postprocess inserted (non-physical) annotation in the background read-action.
   * 
   * @param annotation annotation
   * @param updater updater that could be used
   */
  protected void postProcess(@NotNull PsiAnnotation annotation, @NotNull ModPsiUpdater updater) {
    
  }

  protected @Nullable PsiAnnotation addAnnotation(PsiAnnotationOwner annotationOwner, String fqn) {
    return AddAnnotationPsiFix.expandParameterAndAddAnnotation(annotationOwner, fqn);
  }

  /**
   * Creates a fix which will add the default "Nullable" annotation to the given element.
   *
   * @param owner an element to add the annotation
   * @return newly created fix or null if adding nullability annotation is impossible for the specified element.
   */
  public static @Nullable ModCommandAction createAddNullableFix(PsiModifierListOwner owner) {
    NullableNotNullManager manager = NullableNotNullManager.getInstance(owner.getProject());
    return createAddNullableNotNullFix(owner, manager.getDefaultAnnotation(Nullability.NULLABLE, owner), manager.getNotNulls());
  }

  /**
   * Creates a fix which will add the default "NotNull" annotation to the given element.
   *
   * @param owner an element to add the annotation
   * @return newly created fix or null if adding nullability annotation is impossible for the specified element.
   */
  public static @Nullable ModCommandAction createAddNotNullFix(PsiModifierListOwner owner) {
    NullableNotNullManager manager = NullableNotNullManager.getInstance(owner.getProject());
    return createAddNullableNotNullFix(owner, manager.getDefaultAnnotation(Nullability.NOT_NULL, owner), manager.getNullables());
  }

  private static @Nullable ModCommandAction createAddNullableNotNullFix(PsiModifierListOwner owner, String annotationToAdd,
                                                                        List<String> annotationsToRemove) {
    if (!AddAnnotationPsiFix.isNullabilityAnnotationApplicable(owner)) return null;
    if (!AnnotationUtil.isAnnotatingApplicable(owner, annotationToAdd)) return null;
    return new AddAnnotationModCommandAction(annotationToAdd, owner, ArrayUtilRt.toStringArray(annotationsToRemove));
  }
}
