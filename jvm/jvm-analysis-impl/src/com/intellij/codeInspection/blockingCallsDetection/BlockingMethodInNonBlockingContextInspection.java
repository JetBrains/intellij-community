// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.blockingCallsDetection;

import com.intellij.analysis.JvmAnalysisBundle;
import com.intellij.codeInspection.AbstractBaseUastLocalInspectionTool;
import com.intellij.codeInspection.AnalysisUastUtil;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.util.ui.UIUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UCallExpression;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;

import static java.util.Collections.emptyList;

public final class BlockingMethodInNonBlockingContextInspection extends AbstractBaseUastLocalInspectionTool {

  public static final List<String> DEFAULT_BLOCKING_ANNOTATIONS = List.of(
    "org.jetbrains.annotations.Blocking",
    "io.micronaut.core.annotation.Blocking",
    "io.smallrye.common.annotation.Blocking"
  );
  public static final List<String> DEFAULT_NONBLOCKING_ANNOTATIONS = List.of(
    "org.jetbrains.annotations.NonBlocking",
    "io.micronaut.core.annotation.NonBlocking",
    "io.smallrye.common.annotation.NonBlocking"
  );

  public List<String> myBlockingAnnotations = new ArrayList<>(DEFAULT_BLOCKING_ANNOTATIONS);
  public List<String> myNonBlockingAnnotations = new ArrayList<>(DEFAULT_NONBLOCKING_ANNOTATIONS);

  @Override
  public JComponent createOptionsPanel() {
    return new OptionsPanel();
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    Collection<String> nonBlockingAnnotations = union(myNonBlockingAnnotations, DEFAULT_NONBLOCKING_ANNOTATIONS);
    Collection<String> blockingAnnotations = union(myBlockingAnnotations, DEFAULT_BLOCKING_ANNOTATIONS);

    List<NonBlockingContextChecker> nonBlockingContextCheckers =
      getNonBlockingContextCheckers(holder.getFile(), blockingAnnotations, nonBlockingAnnotations);
    if (nonBlockingContextCheckers.isEmpty()) return PsiElementVisitor.EMPTY_VISITOR;

    List<BlockingMethodChecker> blockingMethodCheckers =
      getBlockingMethodCheckers(holder.getFile(), blockingAnnotations, nonBlockingAnnotations);
    if (blockingMethodCheckers.isEmpty()) return PsiElementVisitor.EMPTY_VISITOR;

    return new BlockingMethodInNonBlockingContextVisitor(holder, blockingMethodCheckers, nonBlockingContextCheckers);
  }

  private static @NotNull List<NonBlockingContextChecker> getNonBlockingContextCheckers(@NotNull PsiFile file,
                                                                                        @NotNull Collection<String> blockingAnnotations,
                                                                                        @NotNull Collection<String> nonBlockingAnnotations) {
    List<NonBlockingContextChecker> nonBlockingContextCheckers = new ArrayList<>(NonBlockingContextChecker.EP_NAME.getExtensionList());
    nonBlockingContextCheckers.add(new AnnotationBasedNonBlockingContextChecker(blockingAnnotations, nonBlockingAnnotations));
    nonBlockingContextCheckers.removeIf(checker -> !checker.isApplicable(file));
    return nonBlockingContextCheckers;
  }

  private static @NotNull List<BlockingMethodChecker> getBlockingMethodCheckers(@NotNull PsiFile file,
                                                                                @NotNull Collection<String> blockingAnnotations,
                                                                                @NotNull Collection<String> nonBlockingAnnotations) {
    List<BlockingMethodChecker> blockingMethodCheckers = new ArrayList<>(BlockingMethodChecker.EP_NAME.getExtensionList());
    blockingMethodCheckers.add(new AnnotationBasedBlockingMethodChecker(blockingAnnotations, nonBlockingAnnotations));
    blockingMethodCheckers.removeIf(checker -> !checker.isApplicable(file));
    return blockingMethodCheckers;
  }

  private static Collection<String> union(Collection<String> annotations, Collection<String> defaultAnnotations) {
    Set<String> result = new HashSet<>(defaultAnnotations);
    result.addAll(annotations != null ? annotations : emptyList());
    return result;
  }

  private final class OptionsPanel extends JPanel {
    private OptionsPanel() {
      super(new BorderLayout());
      JPanel mainPanel = new JPanel(new VerticalLayout(UIUtil.DEFAULT_VGAP));

      Project project = getCurrentProjectOrDefault(this);
      BlockingAnnotationsPanel blockingAnnotationsPanel =
        new BlockingAnnotationsPanel(
          project,
          JvmAnalysisBundle
            .message("jvm.inspections.blocking.method.annotation.blocking"),
          myBlockingAnnotations,
          DEFAULT_BLOCKING_ANNOTATIONS,
          JvmAnalysisBundle.message("jvm.inspections.blocking.method.annotation.configure.empty.text"),
          JvmAnalysisBundle.message("jvm.inspections.blocking.method.annotation.configure.add.blocking.title"));

      BlockingAnnotationsPanel nonBlockingAnnotationsPanel =
        new BlockingAnnotationsPanel(
          project,
          JvmAnalysisBundle.message(
            "jvm.inspections.blocking.method.annotation.non-blocking"),
          myNonBlockingAnnotations,
          DEFAULT_NONBLOCKING_ANNOTATIONS,
          JvmAnalysisBundle.message("jvm.inspections.blocking.method.annotation.configure.empty.text"),
          JvmAnalysisBundle.message("jvm.inspections.blocking.method.annotation.configure.add.non-blocking.title"));

      mainPanel.add(blockingAnnotationsPanel.getComponent());
      mainPanel.add(nonBlockingAnnotationsPanel.getComponent());

      add(mainPanel, BorderLayout.CENTER);
    }
  }

  @NotNull
  private static Project getCurrentProjectOrDefault(Component context) {
    Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(context));
    if (project == null) {
      IdeFrame lastFocusedFrame = IdeFocusManager.getGlobalInstance().getLastFocusedFrame();
      if (lastFocusedFrame != null) project = lastFocusedFrame.getProject();
      if (project == null) project = ProjectManager.getInstance().getDefaultProject();
    }
    return project;
  }

  private static class BlockingMethodInNonBlockingContextVisitor extends PsiElementVisitor {
    private final ProblemsHolder myHolder;
    private final List<BlockingMethodChecker> myBlockingMethodCheckers;
    private final List<NonBlockingContextChecker> myNonBlockingContextCheckers;

    BlockingMethodInNonBlockingContextVisitor(@NotNull ProblemsHolder holder,
                                              List<BlockingMethodChecker> blockingMethodCheckers,
                                              List<NonBlockingContextChecker> nonBlockingContextCheckers) {
      myHolder = holder;
      myBlockingMethodCheckers = blockingMethodCheckers;
      myNonBlockingContextCheckers = nonBlockingContextCheckers;
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
      super.visitElement(element);
      UCallExpression callExpression = AnalysisUastUtil.getUCallExpression(element);
      if (callExpression == null) return;
      if (!isContextNonBlockingFor(element, myNonBlockingContextCheckers)) return;
      ProgressIndicatorProvider.checkCanceled();

      PsiMethod referencedMethod = callExpression.resolve();
      if (referencedMethod == null) return;

      MethodContext methodContext = new MethodContext(referencedMethod, myBlockingMethodCheckers);
      if (!isMethodOrSupersBlocking(methodContext)) return;

      PsiElement elementToHighLight = AnalysisUastUtil.getMethodIdentifierSourcePsi(callExpression);
      if (elementToHighLight == null) return;

      LocalQuickFix[] quickFixes = StreamEx.of(myBlockingMethodCheckers)
        .flatArray(checker -> checker.getQuickFixesFor(element))
        .toArray(LocalQuickFix.EMPTY_ARRAY);
      myHolder.registerProblem(elementToHighLight,
                               JvmAnalysisBundle.message("jvm.inspections.blocking.method.problem.descriptor"),
                               quickFixes);
    }
  }

  private static boolean isMethodOrSupersBlocking(MethodContext methodContext) {
    return StreamEx.of(methodContext.getMethod()).append(methodContext.getMethod().findDeepestSuperMethods())
      .anyMatch(method -> isMethodBlocking(methodContext));
  }

  private static boolean isMethodBlocking(MethodContext methodContext) {
    return methodContext.getCheckers().stream().anyMatch(extension -> {
      ProgressManager.checkCanceled();
      return extension.isMethodBlocking(methodContext);
    });
  }

  private static boolean isContextNonBlockingFor(PsiElement element,
                                                 List<? extends NonBlockingContextChecker> nonBlockingContextCheckers) {
    return nonBlockingContextCheckers.stream().anyMatch(extension -> {
      ProgressIndicatorProvider.checkCanceled();
      return extension.isContextNonBlockingFor(element);
    });
  }
}
