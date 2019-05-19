// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.daemon.impl.analysis.AnnotationsHighlightUtil;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.lang.findUsages.LanguageFindUsages;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.RetentionPolicy;
import java.util.List;

import static com.intellij.codeInsight.AnnotationUtil.CHECK_EXTERNAL;
import static com.intellij.codeInsight.AnnotationUtil.CHECK_TYPE;

public class AddAnnotationPsiFix extends LocalQuickFixOnPsiElement {
  protected final String myAnnotation;
  private final String[] myAnnotationsToRemove;
  private final PsiNameValuePair[] myPairs; // not used when registering local quick fix
  protected final String myText;

  public AddAnnotationPsiFix(@NotNull String fqn,
                             @NotNull PsiModifierListOwner modifierListOwner,
                             @NotNull PsiNameValuePair[] values,
                             @NotNull String... annotationsToRemove) {
    super(modifierListOwner);
    myAnnotation = fqn;
    ObjectUtils.assertAllElementsNotNull(values);
    myPairs = values;
    ObjectUtils.assertAllElementsNotNull(annotationsToRemove);
    myAnnotationsToRemove = annotationsToRemove;
    myText = calcText(modifierListOwner, myAnnotation);
  }

  public static String calcText(PsiModifierListOwner modifierListOwner, @Nullable String annotation) {
    final String shortName = annotation == null ? null : annotation.substring(annotation.lastIndexOf('.') + 1);
    if (modifierListOwner instanceof PsiNamedElement) {
      final String name = ((PsiNamedElement)modifierListOwner).getName();
      if (name != null) {
        String type = LanguageFindUsages.getType(modifierListOwner);
        if (shortName == null) {
          return CodeInsightBundle.message("inspection.i18n.quickfix.annotate.element", type, name);
        }
        return CodeInsightBundle
          .message("inspection.i18n.quickfix.annotate.element.as", type, name, shortName);
      }
    }
    if (shortName == null) {
      return CodeInsightBundle.message("inspection.i18n.quickfix.annotate");
    }
    return CodeInsightBundle.message("inspection.i18n.quickfix.annotate.as", shortName);
  }

  @Nullable
  public static PsiModifierListOwner getContainer(final PsiFile file, int offset) {
    PsiReference reference = file.findReferenceAt(offset);
    if (reference != null) {
      PsiElement target = reference.resolve();
      if (target instanceof PsiMember) {
        return (PsiMember)target;
      }
    }

    PsiElement element = file.findElementAt(offset);

    PsiModifierListOwner listOwner = PsiTreeUtil.getParentOfType(element, PsiModifierListOwner.class, false);
    if (listOwner instanceof PsiParameter) return listOwner;

    if (listOwner instanceof PsiNameIdentifierOwner) {
      PsiElement id = ((PsiNameIdentifierOwner)listOwner).getNameIdentifier();
      if (id != null && id.getTextRange().containsOffset(offset)) { // Groovy methods will pass this check as well
        return listOwner;
      }
    }

    return null;
  }

  @Override
  @NotNull
  public String getText() {
    return myText;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.add.annotation.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    return isAvailable((PsiModifierListOwner)startElement, myAnnotation);
  }

  public static boolean isAvailable(@NotNull PsiModifierListOwner modifierListOwner, @NotNull String annotationFQN) {
    if (!modifierListOwner.isValid()) return false;
    if (!PsiUtil.isLanguageLevel5OrHigher(modifierListOwner)) return false;

    // e.g. PsiTypeParameterImpl doesn't have modifier list
    PsiModifierList modifierList = modifierListOwner.getModifierList();
    return modifierList != null
           && !(modifierList instanceof LightElement)
           && !(modifierListOwner instanceof LightElement)
           && !AnnotationUtil.isAnnotated(modifierListOwner, annotationFQN, CHECK_EXTERNAL | CHECK_TYPE);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    final PsiModifierListOwner myModifierListOwner = (PsiModifierListOwner)startElement;

    final ExternalAnnotationsManager annotationsManager = ExternalAnnotationsManager.getInstance(project);
    final PsiModifierList modifierList = myModifierListOwner.getModifierList();
    if (modifierList == null || modifierList.hasAnnotation(myAnnotation)) return;
    PsiClass aClass = JavaPsiFacade.getInstance(project).findClass(myAnnotation, myModifierListOwner.getResolveScope());
    final ExternalAnnotationsManager.AnnotationPlace annotationAnnotationPlace;
    if (aClass != null && BaseIntentionAction.canModify(myModifierListOwner) && AnnotationsHighlightUtil.getRetentionPolicy(aClass) == RetentionPolicy.RUNTIME) {
      annotationAnnotationPlace = ExternalAnnotationsManager.AnnotationPlace.IN_CODE;
    }
    else {
      annotationAnnotationPlace = annotationsManager.chooseAnnotationsPlace(myModifierListOwner);
    }
    if (annotationAnnotationPlace == ExternalAnnotationsManager.AnnotationPlace.NOWHERE) return;
    if (annotationAnnotationPlace == ExternalAnnotationsManager.AnnotationPlace.EXTERNAL) {
      for (String fqn : myAnnotationsToRemove) {
        annotationsManager.deannotate(myModifierListOwner, fqn);
      }
      try {
        annotationsManager.annotateExternally(myModifierListOwner, myAnnotation, file, myPairs);
      }
      catch (ExternalAnnotationsManager.CanceledConfigurationException ignored) {}
    }
    else {
      final PsiFile containingFile = myModifierListOwner.getContainingFile();
      WriteCommandAction.runWriteCommandAction(project, null, null, () -> {
        removePhysicalAnnotations(myModifierListOwner, myAnnotationsToRemove);

        PsiAnnotation inserted = addPhysicalAnnotation(myAnnotation, myPairs, modifierList);
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(inserted);
      }, containingFile);

      if (containingFile != file) {
        UndoUtil.markPsiFileForUndo(file);
      }
    }
  }

  public static PsiAnnotation addPhysicalAnnotation(String fqn, PsiNameValuePair[] pairs, PsiModifierList modifierList) {
    PsiAnnotation inserted = modifierList.addAnnotation(fqn);
    for (PsiNameValuePair pair : pairs) {
      inserted.setDeclaredAttributeValue(pair.getName(), pair.getValue());
    }
    return inserted;
  }

  public static void removePhysicalAnnotations(@NotNull PsiModifierListOwner owner, @NotNull String... fqns) {
    for (String fqn : fqns) {
      PsiAnnotation annotation = AnnotationUtil.findAnnotation(owner, true, fqn);
      if (annotation != null && !AnnotationUtil.isInferredAnnotation(annotation)) {
        annotation.delete();
      }
    }
  }

  @NotNull
  protected String[] getAnnotationsToRemove() {
    return myAnnotationsToRemove;
  }

  public static boolean isNullabilityAnnotationApplicable(@NotNull PsiModifierListOwner owner) {
    if (owner instanceof PsiMethod) {
      PsiType returnType = ((PsiMethod)owner).getReturnType();
      return returnType != null && !(returnType instanceof PsiPrimitiveType);
    }
    return !(owner instanceof PsiClass);
  }

  /**
   * Creates a fix which will add default "Nullable" annotation to the given element.
   *
   * @param owner an element to add the annotation
   * @return newly created fix or null if adding nullability annotation is impossible for the specified element.
   */
  @Nullable
  public static AddAnnotationPsiFix createAddNullableFix(PsiModifierListOwner owner) {
    NullableNotNullManager manager = NullableNotNullManager.getInstance(owner.getProject());
    return createAddNullableNotNullFix(owner, manager.getDefaultNullable(), manager.getNotNulls());
  }

  /**
   * Creates a fix which will add default "NotNull" annotation to the given element.
   *
   * @param owner an element to add the annotation
   * @return newly created fix or null if adding nullability annotation is impossible for the specified element.
   */
  @Nullable
  public static AddAnnotationPsiFix createAddNotNullFix(PsiModifierListOwner owner) {
    NullableNotNullManager manager = NullableNotNullManager.getInstance(owner.getProject());
    return createAddNullableNotNullFix(owner, manager.getDefaultNotNull(), manager.getNullables());
  }

  @Nullable
  private static AddAnnotationPsiFix createAddNullableNotNullFix(PsiModifierListOwner owner, String annotationToAdd,
                                                                 List<String> annotationsToRemove) {
    if (!isNullabilityAnnotationApplicable(owner)) return null;
    return new AddAnnotationPsiFix(annotationToAdd, owner, PsiNameValuePair.EMPTY_ARRAY, ArrayUtil.toStringArray(annotationsToRemove));
  }
}
