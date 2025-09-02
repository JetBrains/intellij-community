// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.ExternalAnnotationsManager.AnnotationPlace;
import com.intellij.codeInsight.ExternalAnnotationsManagerImpl;
import com.intellij.codeInsight.externalAnnotation.AnnotationProvider;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.java.JavaBundle;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.Presentation;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.util.ArrayUtil;
import one.util.streamex.MoreCollectors;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public final class AnnotateIntentionAction implements ModCommandAction {
  private String mySingleAnnotationName;

  @Override
  public @NotNull String getFamilyName() {
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
  public @Nullable Presentation getPresentation(@NotNull ActionContext context) {
    final PsiModifierListOwner owner = AddAnnotationPsiFix.getContainer(context.file(), context.offset());
    if (owner == null || owner.getModifierList() == null || !ExternalAnnotationsManagerImpl.areExternalAnnotationsApplicable(owner)) {
      return null;
    }
    List<AnnotationProvider> annotations = availableAnnotations(owner, context.project()).limit(2).toList();
    if (annotations.isEmpty()) return null;
    if (mySingleAnnotationName != null && canAnnotateWith(context.file(), owner, mySingleAnnotationName)) {
      return Presentation.of(AddAnnotationPsiFix.calcText(owner, mySingleAnnotationName)).withPriority(PriorityAction.Priority.LOW);
    }
    if (annotations.size() == 1) {
      String providerName = annotations.get(0).getName(context.project());
      return Presentation.of(AddAnnotationPsiFix.calcText(owner, providerName)).withPriority(PriorityAction.Priority.LOW);
    }
    return Presentation.of(AddAnnotationPsiFix.calcText(owner, null)).withPriority(PriorityAction.Priority.LOW);
  }

  @Override
  public @NotNull ModCommand perform(@NotNull ActionContext context) {
    final PsiModifierListOwner owner = AddAnnotationPsiFix.getContainer(context.file(), context.offset());
    assert owner != null;
    AnnotationPlace place = ExternalAnnotationsManager.getInstance(context.project()).chooseAnnotationsPlaceNoUi(owner);
    List<ModCommandAction> actions;
    Stream<AnnotationProvider> providers;
    if (mySingleAnnotationName != null) {
      providers = getProviderFor(context.file(), owner, mySingleAnnotationName).stream();
    } else {
      providers = availableAnnotations(owner, context.project());
    }
    actions = providers.map(provider -> provider.createFix(owner, place)).toList();
    return ModCommand.chooseAction(JavaBundle.message("annotate.intention.chooser.title"), actions);
  }
}