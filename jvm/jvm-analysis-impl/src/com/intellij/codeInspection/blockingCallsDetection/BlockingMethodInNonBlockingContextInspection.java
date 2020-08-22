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
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UCallExpression;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;

public class BlockingMethodInNonBlockingContextInspection extends AbstractBaseUastLocalInspectionTool {

  public static final String DEFAULT_BLOCKING_ANNOTATION = "org.jetbrains.annotations.Blocking";
  public static final String DEFAULT_NONBLOCKING_ANNOTATION = "org.jetbrains.annotations.NonBlocking";

  public List<String> myBlockingAnnotations = new SmartList<>();
  public List<String> myNonBlockingAnnotations = new SmartList<>();

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
      final Splitter mainPanel = new Splitter(true);

      Project project = getCurrentProjectOrDefault(this);
      BlockingAnnotationsPanel blockingAnnotationsPanel =
        new BlockingAnnotationsPanel(
          project,
          JvmAnalysisBundle
            .message("jvm.inspections.blocking.method.annotation.blocking"),
          DEFAULT_BLOCKING_ANNOTATION,
          myBlockingAnnotations,
          Collections.singletonList(DEFAULT_BLOCKING_ANNOTATION),
          JvmAnalysisBundle.message("jvm.inspections.blocking.method.annotation.configure.empty.text"),
          JvmAnalysisBundle.message("jvm.inspections.blocking.method.annotation.configure.add.blocking.title"));


      BlockingAnnotationsPanel nonBlockingAnnotationsPanel =
        new BlockingAnnotationsPanel(
          project,
          JvmAnalysisBundle.message(
            "jvm.inspections.blocking.method.annotation.non-blocking"),
          DEFAULT_NONBLOCKING_ANNOTATION,
          myNonBlockingAnnotations,
          Collections.singletonList(DEFAULT_NONBLOCKING_ANNOTATION),
          JvmAnalysisBundle.message("jvm.inspections.blocking.method.annotation.configure.empty.text"),
          JvmAnalysisBundle.message("jvm.inspections.blocking.method.annotation.configure.add.non-blocking.title"));

      mainPanel.setFirstComponent(blockingAnnotationsPanel.getComponent());
      mainPanel.setSecondComponent(nonBlockingAnnotationsPanel.getComponent());

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
