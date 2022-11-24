// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.blockingCallsDetection;

import com.intellij.analysis.JvmAnalysisBundle;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.*;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.text.StringUtil;
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

  public BlockingMethodInNonBlockingContextInspection() {
    myConsiderUnknownContextBlocking = true;
  }

  public BlockingMethodInNonBlockingContextInspection(boolean considerUnknownContextBlocking) {
    myConsiderUnknownContextBlocking = considerUnknownContextBlocking;
  }

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
  public boolean myConsiderUnknownContextBlocking;

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

    return new BlockingMethodInNonBlockingContextVisitor(holder, blockingMethodCheckers, nonBlockingContextCheckers, getSettings());
  }

  public BlockingCallInspectionSettings getSettings() {
    return new BlockingCallInspectionSettings(myConsiderUnknownContextBlocking);
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

      JCheckBox unknownContextCheckBox = new JCheckBox(
        JvmAnalysisBundle.message("jvm.inspections.blocking.method.consider.unknown.context.blocking"),
        myConsiderUnknownContextBlocking);
      unknownContextCheckBox.addActionListener(e -> myConsiderUnknownContextBlocking = unknownContextCheckBox.isSelected());
      mainPanel.add(unknownContextCheckBox);
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

  private class BlockingMethodInNonBlockingContextVisitor extends PsiElementVisitor {
    private final ProblemsHolder myHolder;
    private final List<BlockingMethodChecker> myBlockingMethodCheckers;
    private final List<NonBlockingContextChecker> myNonBlockingContextCheckers;
    private final BlockingCallInspectionSettings mySettings;

    BlockingMethodInNonBlockingContextVisitor(@NotNull ProblemsHolder holder,
                                              List<BlockingMethodChecker> blockingMethodCheckers,
                                              List<NonBlockingContextChecker> nonBlockingContextCheckers,
                                              BlockingCallInspectionSettings settings) {
      myHolder = holder;
      myBlockingMethodCheckers = blockingMethodCheckers;
      myNonBlockingContextCheckers = nonBlockingContextCheckers;
      mySettings = settings;
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
      super.visitElement(element);
      UCallExpression callExpression = AnalysisUastUtil.getUCallExpression(element);
      if (callExpression == null) return;
      PsiElement elementToHighLight = AnalysisUastUtil.getMethodIdentifierSourcePsi(callExpression);
      if (elementToHighLight == null) return;

      ContextType contextType = isContextNonBlockingFor(element, myNonBlockingContextCheckers, mySettings);
      if (contextType instanceof ContextType.Blocking) {
        return;
      }
      if (contextType instanceof ContextType.Unsure && myConsiderUnknownContextBlocking) {
        myHolder.registerProblem(elementToHighLight,
                                 JvmAnalysisBundle.message("jvm.inspections.blocking.method.consider.unknown.context.nonblocking"),
                                 ProblemHighlightType.INFORMATION,
                                 new ConsiderUnknownContextBlockingFix(false));
        return;
      }
      ProgressIndicatorProvider.checkCanceled();

      PsiMethod referencedMethod = callExpression.resolve();
      if (referencedMethod == null) return;

      if (!isMethodOrSupersBlocking(referencedMethod, myBlockingMethodCheckers, mySettings)) return;

      ElementContext elementContext = new ElementContext(element, mySettings);
      StreamEx<LocalQuickFix> fixesStream = StreamEx.of(myBlockingMethodCheckers)
        .flatArray(checker -> checker.getQuickFixesFor(elementContext));

      if (contextType instanceof ContextType.Unsure && !myConsiderUnknownContextBlocking) {
        fixesStream = fixesStream.append(new ConsiderUnknownContextBlockingFix(true));
      }

      String message;
      if (contextType instanceof ContextType.NonBlocking &&
          StringUtil.isNotEmpty(((ContextType.NonBlocking)contextType).getDescription())) {
        String contextDescription = ((ContextType.NonBlocking)contextType).getDescription();
        message = JvmAnalysisBundle.message("jvm.inspections.blocking.method.problem.wildcard.descriptor", contextDescription);
      }
      else {
        message = JvmAnalysisBundle.message("jvm.inspections.blocking.method.problem.descriptor");
      }
      myHolder.registerProblem(elementToHighLight, message, fixesStream.toArray(LocalQuickFix.EMPTY_ARRAY));
    }
  }

  private static boolean isMethodOrSupersBlocking(PsiMethod referencedMethod,
                                                  List<BlockingMethodChecker> checkers,
                                                  BlockingCallInspectionSettings settings) {
    return StreamEx.of(referencedMethod).append(referencedMethod.findDeepestSuperMethods())
      .anyMatch(method -> isMethodBlocking(referencedMethod, checkers, settings));
  }

  private static boolean isMethodBlocking(PsiMethod referencedMethod,
                                          List<BlockingMethodChecker> checkers,
                                          BlockingCallInspectionSettings settings) {
    for (BlockingMethodChecker extension : checkers) {
      ProgressManager.checkCanceled();

      MethodContext methodContext = new MethodContext(referencedMethod, extension, checkers, settings);
      if (extension.isMethodBlocking(methodContext)) return true;
    }
    return false;
  }

  private static ContextType isContextNonBlockingFor(PsiElement element,
                                                     List<? extends NonBlockingContextChecker> nonBlockingContextCheckers,
                                                     BlockingCallInspectionSettings settings) {
    ContextType effectiveContextType = ContextType.Unsure.INSTANCE;
    ElementContext elementContext = new ElementContext(element, settings);
    for (NonBlockingContextChecker checker : nonBlockingContextCheckers) {
      ProgressIndicatorProvider.checkCanceled();
      ContextType checkResult = checker.computeContextType(elementContext);
      effectiveContextType = chooseType(effectiveContextType, checkResult);
      if (effectiveContextType instanceof ContextType.NonBlocking) return effectiveContextType;
    }
    return effectiveContextType;
  }

  private static ContextType chooseType(ContextType first, ContextType second) {
    return first.getPriority() > second.getPriority() ? first : second;
  }

  private class ConsiderUnknownContextBlockingFix implements LocalQuickFix {
    private final boolean considerUnknownContextBlocking;

    private ConsiderUnknownContextBlockingFix(boolean considerUnknownContextBlocking) {
      this.considerUnknownContextBlocking = considerUnknownContextBlocking;
    }

    @Override
    public @NotNull String getFamilyName() {
      if (considerUnknownContextBlocking) {
        return JvmAnalysisBundle.message("jvm.inspections.blocking.method.consider.unknown.context.blocking");
      }
      else {
        return JvmAnalysisBundle.message("jvm.inspections.blocking.method.consider.unknown.context.nonblocking");
      }
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      BlockingMethodInNonBlockingContextInspection.this.myConsiderUnknownContextBlocking = considerUnknownContextBlocking;
      DaemonCodeAnalyzer.getInstance(project).restart(descriptor.getPsiElement().getContainingFile());
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
      return new IntentionPreviewInfo.Html(JvmAnalysisBundle.message("jvm.inspections.blocking.method.intention.text", getFamilyName()));
    }
  }
}
