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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UCallExpression;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class BlockingMethodInNonBlockingContextInspection extends AbstractBaseUastLocalInspectionTool {

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

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new OptionsPanel();
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    List<BlockingMethodChecker> blockingMethodCheckers =
      ContainerUtil.append(BlockingMethodChecker.EP_NAME.getExtensionList(),
                           new AnnotationBasedBlockingMethodChecker(myBlockingAnnotations));

    List<NonBlockingContextChecker> nonBlockingContextCheckers =
      ContainerUtil.append(NonBlockingContextChecker.EP_NAME.getExtensionList(),
                           new AnnotationBasedNonBlockingContextChecker(myNonBlockingAnnotations));

    if (!isInspectionActive(holder.getFile(), blockingMethodCheckers, nonBlockingContextCheckers)) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new BlockingMethodInNonBlockingContextVisitor(holder, blockingMethodCheckers, nonBlockingContextCheckers);
  }

  private static boolean isInspectionActive(PsiFile file,
                                            List<BlockingMethodChecker> myBlockingMethodCheckers,
                                            List<NonBlockingContextChecker> myNonBlockingContextCheckers) {
    return myBlockingMethodCheckers.stream().anyMatch(extension -> extension.isApplicable(file)) &&
           myNonBlockingContextCheckers.stream().anyMatch(extension -> extension.isApplicable(file));
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
    private final List<? extends BlockingMethodChecker> myBlockingMethodCheckers;
    private final List<? extends NonBlockingContextChecker> myNonBlockingContextCheckers;

    BlockingMethodInNonBlockingContextVisitor(@NotNull ProblemsHolder holder,
                                              List<? extends BlockingMethodChecker> blockingMethodCheckers,
                                              List<? extends NonBlockingContextChecker> nonBlockingContextCheckers) {
      myHolder = holder;
      this.myBlockingMethodCheckers = blockingMethodCheckers;
      this.myNonBlockingContextCheckers = nonBlockingContextCheckers;
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

      if (!isMethodOrSupersBlocking(referencedMethod, myBlockingMethodCheckers)) return;

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

  private static boolean isMethodOrSupersBlocking(PsiMethod referencedMethod,
                                                  List<? extends BlockingMethodChecker> blockingMethodCheckers) {
    return StreamEx.of(referencedMethod).append(referencedMethod.findDeepestSuperMethods())
      .anyMatch(method -> isMethodBlocking(method, blockingMethodCheckers));
  }

  private static boolean isMethodBlocking(PsiMethod method,
                                          List<? extends BlockingMethodChecker> blockingMethodCheckers) {
    return blockingMethodCheckers.stream().anyMatch(extension -> {
      ProgressIndicatorProvider.checkCanceled();
      return extension.isMethodBlocking(method);
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
