/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.intention;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.daemon.impl.analysis.AnnotationsHighlightUtil;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.lang.findUsages.LanguageFindUsages;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.RetentionPolicy;

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

  public static String calcText(PsiModifierListOwner modifierListOwner, @NotNull String annotation) {
    final String shortName = annotation.substring(annotation.lastIndexOf('.') + 1);
    if (modifierListOwner instanceof PsiNamedElement) {
      final String name = ((PsiNamedElement)modifierListOwner).getName();
      if (name != null) {
        FindUsagesProvider provider = LanguageFindUsages.INSTANCE.forLanguage(modifierListOwner.getLanguage());
        return CodeInsightBundle
          .message("inspection.i18n.quickfix.annotate.element.as", provider.getType(modifierListOwner), name, shortName);
      }
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
           && !AnnotationUtil.isAnnotated(modifierListOwner, annotationFQN, false, false, true);
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
    if (modifierList == null || modifierList.findAnnotation(myAnnotation) != null) return;
    PsiClass aClass = JavaPsiFacade.getInstance(project).findClass(myAnnotation, myModifierListOwner.getResolveScope());
    final ExternalAnnotationsManager.AnnotationPlace annotationAnnotationPlace;
    if (aClass != null && aClass.getManager().isInProject(aClass) && AnnotationsHighlightUtil.getRetentionPolicy(aClass) == RetentionPolicy.RUNTIME) {
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
}
