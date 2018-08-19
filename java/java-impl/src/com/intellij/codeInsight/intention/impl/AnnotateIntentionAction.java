// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.ExternalAnnotationsManagerImpl;
import com.intellij.codeInsight.externalAnnotation.AnnotationProvider;
import com.intellij.codeInsight.intention.AddAnnotationFix;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import one.util.streamex.MoreCollectors;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class AnnotateIntentionAction extends BaseIntentionAction implements LowPriorityAction {
  private static final AnnotationProvider[] PROVIDERS = AnnotationProvider.KEY.getExtensions();
  private AnnotationProvider myAnnotationProvider;
  private boolean mySingleMode;

  @Override
  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.add.annotation.family");
  }

  private static StreamEx<AnnotationProvider> availableAnnotations(PsiModifierListOwner owner, Project project) {
    return StreamEx.of(PROVIDERS)
                   .filter(p -> p.isAvailable(owner))
                   .filter(p -> !alreadyAnnotated(owner, p, project));
  }

  /**
   * Configure the intention to annotate an element at caret in the editor with concrete annotation
   *
   * @param editor an editor
   * @param file a file
   * @param annotationShortName a short name of the annotation to add
   * @return true if specified annotation is provided and could be added to the element under caret, false otherwise
   */
  @TestOnly
  public boolean selectSingle(Editor editor, PsiFile file, String annotationShortName) {
    if (mySingleMode) {
      throw new IllegalStateException();
    }
    mySingleMode = true;
    final PsiModifierListOwner owner = AddAnnotationPsiFix.getContainer(file, editor.getCaretModel().getOffset());
    if (owner == null || owner.getModifierList() == null || !ExternalAnnotationsManagerImpl.areExternalAnnotationsApplicable(owner)) {
      return false;
    }
    Optional<AnnotationProvider> provider = availableAnnotations(owner, file.getProject())
      .filter(p -> StringUtil.getShortName(p.getName(file.getProject())).equals(annotationShortName))
      .collect(MoreCollectors.onlyOne());
    myAnnotationProvider = provider.orElse(null);
    return provider.isPresent();
  }

  private static boolean alreadyAnnotated(PsiModifierListOwner owner, AnnotationProvider p, Project project) {
    PsiAnnotation annotation = AnnotationUtil.findAnnotation(owner, ArrayUtil
      .prepend(p.getName(owner.getProject()), p.getAnnotationsToRemove(project)));
    return annotation != null && !AnnotationUtil.isInferredAnnotation(annotation);
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    final PsiModifierListOwner owner = AddAnnotationPsiFix.getContainer(file, editor.getCaretModel().getOffset());
    if (owner == null || owner.getModifierList() == null || !ExternalAnnotationsManagerImpl.areExternalAnnotationsApplicable(owner)) {
      return false;
    }
    if (mySingleMode) {
      return myAnnotationProvider != null && availableAnnotations(owner, project).has(myAnnotationProvider);
    }
    List<AnnotationProvider> annotations = availableAnnotations(owner, project).limit(2).collect(Collectors.toList());
    if (annotations.isEmpty()) return false;
    if (annotations.size() == 1) {
      myAnnotationProvider = annotations.get(0);
      setText(AddAnnotationPsiFix.calcText(owner, myAnnotationProvider.getName(project)));
    }
    else {
      myAnnotationProvider = null;
      setText(AddAnnotationPsiFix.calcText(owner, null));
    }
    return true;
  }

  @Override
  public void invoke(@NotNull final Project project, Editor editor, final PsiFile file) throws IncorrectOperationException {
    final PsiModifierListOwner owner = AddAnnotationPsiFix.getContainer(file, editor.getCaretModel().getOffset());
    assert owner != null;
    if (myAnnotationProvider != null) {
      if (alreadyAnnotated(owner, myAnnotationProvider, project)) return;
      AddAnnotationFix fix =
        new AddAnnotationFix(myAnnotationProvider.getName(project), owner, myAnnotationProvider.getAnnotationsToRemove(project));
      fix.invoke(project, editor, file);
    }
    else {
      List<AnnotationProvider> annotations = availableAnnotations(owner, project).collect(Collectors.toList());
      if (annotations.isEmpty()) return;
      JBPopupFactory.getInstance().createListPopup(
        new BaseListPopupStep<AnnotationProvider>(CodeInsightBundle.message("annotate.intention.chooser.title"), annotations) {
          @Override
          public PopupStep onChosen(final AnnotationProvider selectedValue, final boolean finalChoice) {
            return doFinalStep(() -> {
              new AddAnnotationFix(selectedValue.getName(project), owner, selectedValue.getAnnotationsToRemove(project)).invoke(project, editor, file);
            });
          }

          @Override
          public boolean isSpeedSearchEnabled() {
            return true;
          }

          @Override
          @NotNull
          public String getTextFor(final AnnotationProvider value) {
            return value.getName(project);
          }
        }).showInBestPositionFor(editor);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}