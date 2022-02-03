// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.sourceToSink.propagate;

import com.intellij.analysis.JvmAnalysisBundle;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.sourceToSink.MarkAsSafeFix;
import com.intellij.codeInspection.sourceToSink.TaintAnalyzer;
import com.intellij.codeInspection.sourceToSink.TaintValue;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.ui.content.Content;
import com.intellij.usageView.UsageViewContentManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class PropagateFix extends LocalQuickFixAndIntentionActionOnPsiElement {

  private final String myName;

  public PropagateFix(@NotNull PsiElement psiElement, @NotNull String name) {
    super(psiElement);
    myName = name;
  }

  @Override
  public @NotNull String getText() {
    return JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.propagate.safe.text", myName);
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
    UExpression uExpression = UastContextKt.toUElementOfExpectedTypes(startElement, UCallExpression.class, UReferenceExpression.class);
    if (uExpression == null) return;
    PsiElement reportedElement = uExpression.getSourcePsi();
    if (reportedElement == null) return;
    TaintAnalyzer analyzer = new TaintAnalyzer();
    if (analyzer.analyze(uExpression) != TaintValue.UNKNOWN) return;
    PsiElement target = ((UResolvable)uExpression).resolve();
    TaintNode root = new TaintNode(null, target, reportedElement);
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      Set<TaintNode> toAnnotate = new HashSet<>();
      toAnnotate = PropagateAnnotationPanel.getSelectedElements(root, toAnnotate);
      if (toAnnotate == null || root.myTaintValue == TaintValue.TAINTED) return;
      annotate(project, toAnnotate, true);
      return;
    }
    Consumer<Collection<TaintNode>> callback = toAnnotate -> {
      annotate(project, toAnnotate, false);
      ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.FIND);
      if (toolWindow != null) toolWindow.hide();
    };
    PropagateAnnotationPanel panel = new PropagateAnnotationPanel(project, root, callback);
    String title = JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.propagate.safe.toolwindow.title");
    Content content = UsageViewContentManager.getInstance(project).addContent(title, false, panel, true, true);
    panel.setContent(content);
    ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.FIND);
    if (toolWindow != null) toolWindow.activate(null);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public @NotNull String getFamilyName() {
    return JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.propagate.safe.family");
  }

  private static void annotate(@NotNull Project project, @NotNull Collection<TaintNode> toAnnotate, boolean isHeadlessMode) {
    List<TaintNode> nonMarkedNodes = ContainerUtil.filter(toAnnotate, PropagateFix::isNonMarked);
    Set<PsiElement> psiElements = getPsiElements(nonMarkedNodes);
    if (psiElements == null) return;
    MarkAsSafeFix.markAsSafe(project, psiElements, isHeadlessMode);
  }

  private static boolean isNonMarked(@NotNull TaintNode taintNode) {
    if (taintNode.myTaintValue == TaintValue.TAINTED) return false;
    PsiElement psiElement = taintNode.getPsiElement();
    if (psiElement == null) return true;
    return TaintAnalyzer.fromAnnotation(psiElement) != TaintValue.UNTAINTED; 
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
