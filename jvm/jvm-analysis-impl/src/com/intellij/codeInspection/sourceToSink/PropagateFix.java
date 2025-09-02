// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.sourceToSink;

import com.intellij.analysis.JvmAnalysisBundle;
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewToolWindowUtils;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UastContextKt;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class PropagateFix extends LocalQuickFixAndIntentionActionOnPsiElement {

  private final @NotNull TaintValueFactory myTaintValueFactory;

  private final boolean supportRefactoring;

  public PropagateFix(@NotNull PsiElement sourcePsi,
                      @NotNull TaintValueFactory taintValueFactory,
                      boolean supportRefactoring) {
    super(sourcePsi);
    myTaintValueFactory = taintValueFactory;
    this.supportRefactoring = supportRefactoring;
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
    String message = JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.preview.propagate");
    return new IntentionPreviewInfo.Html(message);
  }

  @Override
  public @NotNull String getText() {
    return JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.propagate.safe.text");
  }

  @Override
  public boolean availableInBatchMode() {
    return false;
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile psiFile,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
    List<TaintNode> roots = ActionUtil.underModalProgress(project, CodeInsightBundle.message("progress.title.preparing.result"), ()->{
      UExpression uExpression = UastContextKt.toUElementOfExpectedTypes(startElement, UExpression.class);
      if (uExpression == null) return null;
      PsiElement reportedElement = uExpression.getSourcePsi();
      if (reportedElement == null) return null;
      TaintAnalyzer analyzer = new TaintAnalyzer(myTaintValueFactory);
      try {
        TaintValue value = analyzer.analyzeExpression(uExpression, false, TaintValue.TAINTED);
        if (value != TaintValue.UNKNOWN) return null;
      }
      catch (DeepTaintAnalyzerException e) {
        return null;
      }
      return  ContainerUtil.map(analyzer.getNonMarkedElements(), nonMarkedElement ->
        new TaintNode(null, nonMarkedElement.myNonMarked, nonMarkedElement.myRef, myTaintValueFactory, nonMarkedElement.myNext));
    });
    if (roots == null || roots.isEmpty()) return;
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      for (TaintNode root : roots) {
        Set<TaintNode> toAnnotate = new HashSet<>();
        toAnnotate = PropagateAnnotationPanel.getSelectedElements(root, toAnnotate);
        if (toAnnotate == null || root.myTaintValue == TaintValue.TAINTED) continue;
        annotate(project, toAnnotate);
      }
      return;
    }
    Consumer<Collection<TaintNode>> callback = toAnnotate -> {
      annotate(project, toAnnotate);
      ToolWindow toolWindow = ProblemsViewToolWindowUtils.INSTANCE.getToolWindow(project);
      if (toolWindow != null) toolWindow.hide();
    };
    PropagateAnnotationPanel panel =
      new PropagateAnnotationPanel(project, roots, callback, supportRefactoring);
    String title = JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.propagate.safe.toolwindow.title");
    ToolWindow toolWindow = ProblemsViewToolWindowUtils.INSTANCE.getToolWindow(project);
    if (toolWindow == null) return;
    Content content = ContentFactory.getInstance().createContent(panel, title, true);
    panel.setContent(content);
    ContentManager contentManager = toolWindow.getContentManager();
    contentManager.addContent(content);
    contentManager.setSelectedContent(content);
    toolWindow.activate(null);
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    return new IntentionPreviewInfo.Html(
      JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.propagate.safe.preview")
    );
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public @NotNull String getFamilyName() {
    return JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.propagate.safe.family");
  }

  private void annotate(@NotNull Project project, @NotNull Collection<TaintNode> toAnnotate) {
    List<TaintNode> nonMarkedNodes = ContainerUtil.filter(toAnnotate, this::isNonMarked);
    Set<PsiElement> psiElements = getPsiElements(nonMarkedNodes);
    if (psiElements == null) return;
    MarkAsSafeFix.markAsSafe(project, psiElements, this.myTaintValueFactory);
  }

  private boolean isNonMarked(@NotNull TaintNode taintNode) {
    if (taintNode.myTaintValue == TaintValue.TAINTED) return false;
    PsiElement psiElement = taintNode.getPsiElement();
    if (psiElement == null) return true;
    return myTaintValueFactory.fromElement(psiElement, null) != TaintValue.UNTAINTED;
  }

  private static @Nullable Set<@NotNull PsiElement> getPsiElements(@NotNull Collection<TaintNode> toAnnotate) {
    Set<PsiElement> psiElements = new HashSet<>();
    for (TaintNode node : toAnnotate) {
      PsiElement psiElement = node.getPsiElement();
      if (psiElement == null) return null;
      if (psiElement instanceof PsiLocalVariable) continue;
      psiElements.add(psiElement);
    }
    return psiElements;
  }
}
