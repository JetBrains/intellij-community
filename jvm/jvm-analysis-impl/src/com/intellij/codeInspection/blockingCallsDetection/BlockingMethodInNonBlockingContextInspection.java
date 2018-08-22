// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.blockingCallsDetection;

import com.intellij.analysis.JvmAnalysisBundle;
import com.intellij.codeInspection.AbstractBaseUastLocalInspectionTool;
import com.intellij.codeInspection.AnalysisUastUtil;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UCallExpression;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class BlockingMethodInNonBlockingContextInspection extends AbstractBaseUastLocalInspectionTool {

  public static final String DEFAULT_BLOCKING_ANNOTATION = "org.jetbrains.annotations.Blocking";
  public static final String DEFAULT_NONBLOCKING_ANNOTATION = "org.jetbrains.annotations.NonBlocking";

  public List<String> myBlockingAnnotations = new SmartList<>();
  public List<String> myNonblockingAnnotations = new SmartList<>();

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new OptionsPanel();
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return JvmAnalysisBundle.message("method.name.contains.blocking.word.display.name");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {

    List<BlockingMethodChecker> blockingMethodCheckers =
      ContainerUtil.append(BlockingMethodChecker.EP_NAME.getExtensionList(),
                           new DefaultBlockingMethodChecker(myBlockingAnnotations));

    List<NonblockingContextChecker> nonblockingContextCheckers =
      ContainerUtil.append(NonblockingContextChecker.EP_NAME.getExtensionList(),
                           new DefaultNonblockingContextChecker(myNonblockingAnnotations));

    if (!isInspectionActive(holder.getProject(), blockingMethodCheckers, nonblockingContextCheckers)) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new BlockingMethodInNonBlockingContextVisitor(holder, blockingMethodCheckers, nonblockingContextCheckers);
  }

  private static boolean isInspectionActive(Project project,
                                            List<BlockingMethodChecker> myBlockingMethodCheckers,
                                            List<NonblockingContextChecker> myNonblockingContextCheckers) {
    return myBlockingMethodCheckers.stream().anyMatch(extension -> extension.isActive(project)) &&
           myNonblockingContextCheckers.stream().anyMatch(extension -> extension.isActive(project));
  }



  private class OptionsPanel extends JPanel {
    private OptionsPanel() {
      super(new BorderLayout());
      final Splitter mainPanel = new Splitter(true);

      Project project = getCurrentProjectOrDefault(this);
      BlockingAnnotationsPanel blockingAnnotationsPanel =
        new BlockingAnnotationsPanel(
          project,
          JvmAnalysisBundle
            .message("inspection.blocking.method.annotation.blocking"),
          DEFAULT_BLOCKING_ANNOTATION,
          myBlockingAnnotations,
          new String[]{DEFAULT_BLOCKING_ANNOTATION},
          JvmAnalysisBundle.message("inspection.blocking.method.annotation.configure.empty.text"),
          JvmAnalysisBundle.message("inspection.blocking.method.annotation.configure.add.blocking.title"));


      BlockingAnnotationsPanel nonblockingAnnotationsPanel =
        new BlockingAnnotationsPanel(
          project,
          JvmAnalysisBundle.message(
            "inspection.blocking.method.annotation.nonblocking"),
          DEFAULT_NONBLOCKING_ANNOTATION,
          myNonblockingAnnotations,
          new String[]{DEFAULT_NONBLOCKING_ANNOTATION},
          JvmAnalysisBundle.message("inspection.blocking.method.annotation.configure.add.nonblocking.title"),
          JvmAnalysisBundle.message("inspection.blocking.method.annotation.configure.add.nonblocking.title"));

      mainPanel.setFirstComponent(blockingAnnotationsPanel.getComponent());
      mainPanel.setSecondComponent(nonblockingAnnotationsPanel.getComponent());

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
    private final List<NonblockingContextChecker> myNonblockingContextCheckers;

    public BlockingMethodInNonBlockingContextVisitor(@NotNull ProblemsHolder holder,
                                                     List<BlockingMethodChecker> blockingMethodCheckers,
                                                     List<NonblockingContextChecker> nonblockingContextCheckers) {
      myHolder = holder;
      this.myBlockingMethodCheckers = blockingMethodCheckers;
      this.myNonblockingContextCheckers = nonblockingContextCheckers;
    }

    @Override
    public void visitElement(PsiElement element) {
      UCallExpression callExpression = AnalysisUastUtil.getUCallExpression(element);

      if (callExpression == null) return;

      if (!isContextNonBlockingFor(element)) return;

      PsiMethod referencedMethod = callExpression.resolve();
      if (referencedMethod == null) return;

      boolean isReferencedMethodBlocking = CachedValuesManager.getCachedValue(referencedMethod, () -> {
        boolean isBlocking =
          StreamEx.of(referencedMethod).append(referencedMethod.findDeepestSuperMethods()).anyMatch(method -> isMethodBlocking(method));
        return CachedValueProvider.Result.create(isBlocking, PsiModificationTracker.MODIFICATION_COUNT);
      });

      if (!isReferencedMethodBlocking) return;

      PsiElement elementToHighLight = AnalysisUastUtil.getMethodIdentifierSourcePsi(callExpression);
      if (elementToHighLight == null) return;
      myHolder.registerProblem(elementToHighLight,
                               JvmAnalysisBundle.message("method.name.contains.blocking.word.problem.descriptor"),
                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
    }

    private boolean isContextNonBlockingFor(PsiElement element) {
      return myNonblockingContextCheckers.stream().anyMatch(extension -> extension.isContextNonBlockingFor(element));
    }

    private boolean isMethodBlocking(PsiMethod method) {
      return myBlockingMethodCheckers.stream().anyMatch(extension -> extension.isMethodBlocking(method));
    }
  }
}
