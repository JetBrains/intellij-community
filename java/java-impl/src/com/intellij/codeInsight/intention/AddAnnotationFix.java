/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.lang.findUsages.LanguageFindUsages;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ven
 */
public class AddAnnotationFix extends PsiElementBaseIntentionAction implements LocalQuickFix {
  private final String myAnnotation;
  private final PsiModifierListOwner myModifierListOwner;
  private final String[] myAnnotationsToRemove;
  private final PsiNameValuePair[] myPairs;
  private static final Logger LOG = Logger.getInstance("#" + AddAnnotationFix.class.getName());

  public AddAnnotationFix(String fqn, PsiModifierListOwner modifierListOwner, String... annotationsToRemove) {
    myAnnotation = fqn;
    myModifierListOwner = modifierListOwner;
    myAnnotationsToRemove = annotationsToRemove;
    myPairs = null;
  }

  public AddAnnotationFix(String fqn, PsiModifierListOwner modifierListOwner, PsiNameValuePair[] values, String... annotationsToRemove) {
    myAnnotation = fqn;
    myModifierListOwner = modifierListOwner;
    myAnnotationsToRemove = annotationsToRemove;
    myPairs = values;
  }

  public AddAnnotationFix(@NonNls final String fqn, @NonNls String... annotationsToRemove) {
    this(fqn, null,annotationsToRemove);
  }

  @NotNull
  public String getText() {
    final String shortName = myAnnotation.substring(myAnnotation.lastIndexOf('.') + 1);
    if (myModifierListOwner instanceof PsiNamedElement) {
      final String name = ((PsiNamedElement)myModifierListOwner).getName();
      if (name != null) {
        FindUsagesProvider provider = LanguageFindUsages.INSTANCE.forLanguage(myModifierListOwner.getLanguage());
        return CodeInsightBundle.message("inspection.i18n.quickfix.annotate.element.as", provider.getType(myModifierListOwner), name, shortName);
      }
    }
    return myModifierListOwner != null ?
           CodeInsightBundle.message("inspection.i18n.quickfix.annotate.as", shortName) :
           CodeInsightBundle.message("add.external.annotation.test", shortName);
  }

  @NotNull
  public String getName() {
    return getText();
  }

  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.add.annotation.family");
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    try {
      invoke(project, null, descriptor.getPsiElement().getContainingFile());
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @Nullable
  protected static PsiModifierListOwner getContainer(final PsiElement element) {
    PsiModifierListOwner listOwner = PsiTreeUtil.getParentOfType(element, PsiParameter.class, false);
    if (listOwner == null) {
      final PsiIdentifier psiIdentifier = PsiTreeUtil.getParentOfType(element, PsiIdentifier.class, false);
      if (psiIdentifier != null && psiIdentifier.getParent() instanceof PsiModifierListOwner) {
        listOwner = (PsiModifierListOwner)psiIdentifier.getParent();
      }
    }
    return listOwner;
  }

  public boolean isAvailable(@NotNull final Project project, final Editor editor, @NotNull final PsiElement element) {
    if (!element.isValid()) return false;
    if (!PsiUtil.isLanguageLevel5OrHigher(element)) return false;
    final PsiModifierListOwner owner;
    if (myModifierListOwner != null) {
      if (!myModifierListOwner.isValid()) return false;
      //if (!PsiManager.getInstance(project).isInProject(myModifierListOwner)
      //    || myModifierListOwner.getModifierList() == null) {
      //  if (!myModifierListOwner.isPhysical()) { //we might want to apply fix to just created method
      //    return true;
      //  }
      //}
      
      owner = myModifierListOwner;
    }
    else if (!element.getManager().isInProject(element) || CodeStyleSettingsManager.getSettings(project).USE_EXTERNAL_ANNOTATIONS) {
      owner = getContainer(element);
    }
    else {
      owner = null;
    }
    return owner != null  && !AnnotationUtil.isAnnotated(owner, myAnnotation, false);
  }

  @Override
  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    final PsiElement element;
    if (myModifierListOwner != null) {
      element = myModifierListOwner;
    }
    else {
      final CaretModel caretModel = editor.getCaretModel();
      final int position = caretModel.getOffset();
      element = file.findElementAt(position);
    }
    return element != null && isAvailable(project, editor, element);
  }

  public void invoke(@NotNull final Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final ExternalAnnotationsManager annotationsManager = ExternalAnnotationsManager.getInstance(project);
    if (myModifierListOwner != null) {
      final PsiModifierList modifierList = myModifierListOwner.getModifierList();
      LOG.assertTrue(modifierList != null);
      if (modifierList.findAnnotation(myAnnotation) != null) return;
      final ExternalAnnotationsManager.AnnotationPlace annotationAnnotationPlace = annotationsManager.chooseAnnotationsPlace(myModifierListOwner);
      if (annotationAnnotationPlace == ExternalAnnotationsManager.AnnotationPlace.NOWHERE) return;
      if (annotationAnnotationPlace == ExternalAnnotationsManager.AnnotationPlace.EXTERNAL) {
        for (String fqn : myAnnotationsToRemove) {
          annotationsManager.deannotate(myModifierListOwner, fqn);
        }
        annotationsManager.annotateExternally(myModifierListOwner, myAnnotation, file, myPairs);
      }
      else {
        final PsiFile containingFile = myModifierListOwner.getContainingFile();
        if (!CodeInsightUtilBase.preparePsiElementForWrite(containingFile)) return;
        for (String fqn : myAnnotationsToRemove) {
          PsiAnnotation annotation = AnnotationUtil.findAnnotation(myModifierListOwner, fqn);
          if (annotation != null) {
            annotation.delete();
          }
        }

        PsiAnnotation inserted = modifierList.addAnnotation(myAnnotation);
        if (myPairs != null) {
          for (PsiNameValuePair pair : myPairs) {
            inserted.setDeclaredAttributeValue(pair.getName(), pair.getValue());
          }
        }
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(inserted);
        if (containingFile != file) {
          UndoUtil.markPsiFileForUndo(file);
        }
      }
    }
    else {
      final PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
      annotationsManager.annotateExternally(PsiTreeUtil.getParentOfType(element, PsiModifierListOwner.class, false), myAnnotation, file, null);
    }
  }

  public String[] getAnnotationsToRemove() {
    return myAnnotationsToRemove;
  }
}
