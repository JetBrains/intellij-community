// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.sourceToSink.propagate;

import com.intellij.analysis.JvmAnalysisBundle;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.sourceToSink.MarkAsSafeFix;
import com.intellij.codeInspection.sourceToSink.TaintAnalyzer;
import com.intellij.codeInspection.sourceToSink.TaintValue;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.ui.content.Content;
import com.intellij.usageView.UsageViewContentManager;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
    PsiModifierListOwner target = ObjectUtils.tryCast(((UResolvable)uExpression).resolve(), PsiModifierListOwner.class);
    if (target == null) return;
    // TODO: won't work if we start from kotlin
    PsiMethod method = PsiTreeUtil.getParentOfType(reportedElement, PsiMethod.class);
    if (method == null) return;
    TaintNode root = getTree(project, method, target, reportedElement);
    if (root == null) return;
    String title = JvmAnalysisBundle.message(root.myTaintValue == TaintValue.TAINTED ?
                                             "jvm.inspections.source.unsafe.to.sink.flow.propagate.safe.toolwindow.conflicts.title" :
                                             "jvm.inspections.source.unsafe.to.sink.flow.propagate.safe.toolwindow.title");
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      if (root.myTaintValue == TaintValue.TAINTED) return;
      Set<PsiModifierListOwner> toAnnotate = new HashSet<>();
      toAnnotate = PropagateAnnotationPanel.getSelectedElements(root, root, toAnnotate);
      if (toAnnotate == null) return;
      if (!CommonRefactoringUtil.checkReadOnlyStatusRecursively(project, toAnnotate, false)) return;
      annotate(project, title, toAnnotate);
      return;
    }
    Consumer<Collection<PsiModifierListOwner>> callback = toAnnotate -> annotate(project, title, toAnnotate); 
    PropagateAnnotationPanel panel = new PropagateAnnotationPanel(project, root, callback);
    Content content = UsageViewContentManager.getInstance(project).addContent(title, false, panel, true, true);
    panel.setContent(content);
    ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.FIND);
    if (toolWindow != null) toolWindow.activate(null);
  }
  
  private static void annotate(Project project, @NlsSafe String actionTitle, @NotNull Collection<PsiModifierListOwner> toAnnotate) {
    WriteCommandAction.runWriteCommandAction(project, actionTitle, null,
                                             () -> toAnnotate.forEach(owner -> MarkAsSafeFix.markAsSafe(project, owner)));
  }
  
  private static @Nullable TaintNode getTree(Project project, PsiMethod method, PsiModifierListOwner target, PsiElement reportedElement) {
    String title = JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.propagate.safe.progress.title");
    return ProgressManager.getInstance().runProcessWithProgressSynchronously(
      () -> ReadAction.compute(() -> skipLocal(setRoot(method, buildTree(target, reportedElement)))), 
      title, true, project);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public @NotNull String getFamilyName() {
    return JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.propagate.safe.family");
  }

  private static @NotNull TaintNode buildTree(@NotNull PsiModifierListOwner target, @NotNull PsiElement ref) {
    Deque<TaintNode> elements = new ArrayDeque<>();
    TaintNode root = new TaintNode(target, ref, null, null);
    for (TaintNode taintNode = root; taintNode != null; taintNode = elements.poll()) {
      if (taintNode.myTaintValue != null) continue;
      PsiModifierListOwner element = taintNode.getElement();
      if (element == null) continue;
      PsiElement elementRef = taintNode.getRef();
      if (elementRef == null) continue;
      TaintAnalyzer taintAnalyzer = new TaintAnalyzer();
      TaintValue taintValue = taintAnalyzer.fromElement(element, elementRef, true);
      taintNode.myTaintValue = taintValue;
      if (taintValue == TaintValue.UNTAINTED) continue;
      if (taintValue == TaintValue.TAINTED) {
        propagateTaintedUp(taintNode.myParent);
        continue;
      }
      Set<PsiModifierListOwner> parents = collectParents(taintNode);
      TaintNode parentNode = taintNode;
      Set<TaintNode> children = taintAnalyzer.getNonMarkedElements().stream()
        .filter(c -> !parents.contains(c.myNonMarked))
        .map(c -> new TaintNode(c.myNonMarked, c.myRef, parentNode, null))
        .collect(Collectors.toSet());
      
      elements.addAll(children);
      parentNode.myChildren = children;
    }
    if (root.myTaintValue == TaintValue.TAINTED) skipNonTainted(root);
    return root;
  }
  
  private static @NotNull Set<PsiModifierListOwner> collectParents(@NotNull TaintNode taintNode) {
    Set<PsiModifierListOwner> parents = new HashSet<>();
    while (taintNode != null) {
      PsiModifierListOwner parent = taintNode.getElement();
      if (parent != null) parents.add(parent);
      taintNode = taintNode.myParent;
    }
    return parents;
  }

  private static void skipNonTainted(@NotNull TaintNode root) {
    Set<TaintNode> children = root.myChildren;
    if (children == null) return;
    children.removeIf(c -> c.myTaintValue != TaintValue.TAINTED);
    children.forEach(c -> skipNonTainted(c));
  }

  private static @Nullable TaintNode skipLocal(@NotNull TaintNode root) {
    Deque<TaintNode> taintNodes = new ArrayDeque<>();
    for (TaintNode taintNode = root; taintNode != null; taintNode = taintNodes.poll()) {
      if (taintNode.myChildren == null) continue;
      Set<TaintNode> children = new HashSet<>();
      Deque<TaintNode> workList = new ArrayDeque<>(taintNode.myChildren);
      for (TaintNode childElement = workList.poll(); childElement != null; childElement = workList.poll()) {
        PsiModifierListOwner child = childElement.getElement();
        if (child == null) return null;
        if (child instanceof PsiLocalVariable) {
          if (childElement.myChildren != null) workList.addAll(childElement.myChildren);
        }
        else {
          childElement.myParent = taintNode;
          childElement.setParent(taintNode);
          children.add(childElement);
          taintNodes.add(childElement);
        }
      }
      taintNode.myChildren = children;
    }
    return root;
  }

  private static @NotNull TaintNode setRoot(@NotNull PsiModifierListOwner element, @NotNull TaintNode root) {
    TaintNode newRoot = new TaintNode(element, null, null, root.myTaintValue.join(TaintValue.UNKNOWN));
    root.myParent = newRoot;
    newRoot.myChildren = new HashSet<>();
    newRoot.myChildren.add(root);
    return newRoot;
  }

  private static void propagateTaintedUp(@Nullable TaintNode taintNode) {
    while (taintNode != null) {
      taintNode.myTaintValue = TaintValue.TAINTED;
      taintNode = taintNode.myParent;
    }
  }
}
