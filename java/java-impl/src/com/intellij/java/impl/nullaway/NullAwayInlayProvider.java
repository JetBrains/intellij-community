// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.nullaway;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hints.presentation.InlayButtonPresentationFactory;
import com.intellij.codeInsight.hints.presentation.PresentationFactory;
import com.intellij.codeInsight.hints.presentation.PresentationRenderer;
import com.intellij.codeInspection.JavaSuppressionUtil;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.impl.InlayProvider;
import com.intellij.java.JavaPluginDisposable;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandExecutor;
import com.intellij.modcommand.ModNavigate;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.RefactorJBundle;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInspection.JavaSuppressionUtil.SUPPRESS_INSPECTIONS_ANNOTATION_NAME;

@NotNullByDefault
class NullAwayInlayProvider extends Filter.ResultItem implements InlayProvider {
  private final NullAwayProblem problem;

  NullAwayInlayProvider(int startOffset, int endOffset, NullAwayProblem problem) {
    super(startOffset, endOffset, null);
    this.problem = problem;
  }

  @Override
  @RequiresReadLock
  public EditorCustomElementRenderer createInlayRenderer(Editor editor) {
    var factory = new InlayButtonPresentationFactory(
      editor,
      new PresentationFactory(editor),
      DefaultLanguageHighlighterColors.INLAY_BUTTON_DEFAULT,
      DefaultLanguageHighlighterColors.INLAY_BUTTON_HOVERED,
      DefaultLanguageHighlighterColors.INLAY_BUTTON_FOCUSED);
    var inlayPresentation = factory.smallText(RefactorJBundle.message("nullaway.suppress.inlay.text"))
      .onClick((event, point) -> scheduleFixAction(editor, problem))
      .build();
    return new PresentationRenderer(inlayPresentation);
  }

  private static void scheduleFixAction(Editor editor, NullAwayProblem problem) {
    Project project = editor.getProject();
    if (project == null) return;
    ReadAction.nonBlocking(() -> findSuppressionTarget(project, problem))
      .expireWith(JavaPluginDisposable.getInstance(project))
      .finishOnUiThread(ModalityState.current(), target -> applyFix(editor, problem, target))
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  private static @Nullable PsiModifierListOwner findSuppressionTarget(Project project, NullAwayProblem problem) {
    var target = problem.findSuppressionTarget(project);
    if (target == null) return null;
    if (!PsiUtil.isAvailable(JavaFeature.ANNOTATIONS, target)) return null;
    return target;
  }

  private static void applyFix(Editor editor, NullAwayProblem problem, @Nullable PsiModifierListOwner target) {
    if (target == null) {
      if (!editor.isDisposed()) {
        HintManager.getInstance().showErrorHint(editor, RefactorJBundle.message("nullaway.suppress.error.no.target"));
      }
    }
    else {
      addSuppression(target, problem.kind().nameToSuppress());
    }
  }

  private static void addSuppression(PsiModifierListOwner modifierListOwner, String namedToSuppress) {
    ModCommandExecutor.executeInteractively(
      ActionContext.from(null, modifierListOwner.getContainingFile()),
      RefactorJBundle.message("nullaway.suppress.command.name"),
      null,
      () -> createNavigateCommand(modifierListOwner).andThen(createSuppressAndHighlightCommand(modifierListOwner, namedToSuppress)));
  }

  private static ModNavigate createNavigateCommand(PsiModifierListOwner modifierListOwner) {
    return new ModNavigate(modifierListOwner.getContainingFile().getVirtualFile(), -1, -1,
                           modifierListOwner.getTextRange().getStartOffset());
  }

  private static ModCommand createSuppressAndHighlightCommand(PsiModifierListOwner modifierListOwner, String namedToSuppress) {
    return ModCommand.psiUpdate(modifierListOwner, (e, updater) -> {
      Project project = e.getProject();
      DumbService.getInstance(project).withAlternativeResolveEnabled(() -> {
        JavaSuppressionUtil.addSuppressAnnotation(project, e, e, namedToSuppress);
        PsiModifierList modifierList = e.getModifierList();
        if (modifierList == null) return;
        PsiAnnotation annotation = modifierList.findAnnotation(SUPPRESS_INSPECTIONS_ANNOTATION_NAME);
        if (annotation == null) return;
        updater.highlight(annotation);
      });
    });
  }
}
