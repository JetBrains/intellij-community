// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.ExternalAnnotationsManager.AnnotationPlace;
import com.intellij.codeInsight.ExternalAnnotationsManagerImpl;
import com.intellij.codeInsight.externalAnnotation.AnnotationProvider;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.java.JavaBundle;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
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
import com.intellij.util.concurrency.AppExecutorUtil;
import one.util.streamex.MoreCollectors;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public final class AnnotateIntentionAction extends BaseIntentionAction implements LowPriorityAction {
  private String mySingleAnnotationName;

  @Override
  @NotNull
  public String getFamilyName() {
    return JavaAnalysisBundle.message("intention.add.annotation.family");
  }

  private static StreamEx<AnnotationProvider> availableAnnotations(PsiModifierListOwner owner, Project project) {
    return StreamEx.of(AnnotationProvider.KEY.getExtensionList())
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
    if (mySingleAnnotationName != null) {
      throw new IllegalStateException();
    }
    mySingleAnnotationName = annotationShortName;
    final PsiModifierListOwner owner = AddAnnotationPsiFix.getContainer(file, editor.getCaretModel().getOffset());
    if (owner == null || owner.getModifierList() == null || !ExternalAnnotationsManagerImpl.areExternalAnnotationsApplicable(owner)) {
      return false;
    }
    return canAnnotateWith(file, owner, annotationShortName);
  }

  private static boolean canAnnotateWith(PsiFile file, PsiModifierListOwner owner, String annotationShortName) {
    return getProviderFor(file, owner, annotationShortName).isPresent();
  }

  private static Optional<AnnotationProvider> getProviderFor(PsiFile file, PsiModifierListOwner owner, String annotationShortName) {
    return availableAnnotations(owner, file.getProject())
      .filter(p -> StringUtil.getShortName(p.getName(file.getProject())).equals(annotationShortName))
      .collect(MoreCollectors.onlyOne());
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
    List<AnnotationProvider> annotations = availableAnnotations(owner, project).limit(2).toList();
    if (annotations.isEmpty()) return false;
    if (mySingleAnnotationName != null && canAnnotateWith(file, owner, mySingleAnnotationName)) return true;
    if (annotations.size() == 1) {
      String providerName = annotations.get(0).getName(project);
      setText(AddAnnotationPsiFix.calcText(owner, providerName));
    }
    else {
      setText(AddAnnotationPsiFix.calcText(owner, null));
    }
    return true;
  }

  @Override
  public void invoke(@NotNull final Project project, Editor editor, final PsiFile file) throws IncorrectOperationException {
    final PsiModifierListOwner owner = AddAnnotationPsiFix.getContainer(file, editor.getCaretModel().getOffset());
    assert owner != null;
    AnnotationPlace place = ExternalAnnotationsManager.getInstance(project).chooseAnnotationsPlaceNoUi(owner);
    if (mySingleAnnotationName != null) {
      getProviderFor(file, owner, mySingleAnnotationName)
        .ifPresent(provider -> provider.createFix(owner, place).invoke(project, editor, file));
      return;
    }
    List<AnnotationProvider> annotations = availableAnnotations(owner, project).collect(Collectors.toList());
    if (annotations.isEmpty()) return;
    JBPopupFactory.getInstance().createListPopup(
      new BaseListPopupStep<>(JavaBundle.message("annotate.intention.chooser.title"), annotations) {
        @Override
        public PopupStep onChosen(final AnnotationProvider selectedValue, final boolean finalChoice) {
          return doFinalStep(() -> ReadAction.nonBlocking(() -> selectedValue.createFix(owner, place))
                .finishOnUiThread(ModalityState.current(), fix -> fix.invoke(project, editor, file))
                .submit(AppExecutorUtil.getAppExecutorService()));
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

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}