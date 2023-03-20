// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.blockingCallsDetection;

import com.intellij.analysis.JvmAnalysisBundle;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.options.JavaClassValidator;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;

import java.util.*;

import static com.intellij.codeInspection.options.OptPane.*;
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
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("myConsiderUnknownContextBlocking",
               JvmAnalysisBundle.message("jvm.inspections.blocking.method.consider.unknown.context.blocking")),
      stringList("myBlockingAnnotations", JvmAnalysisBundle.message("jvm.inspections.blocking.method.annotation.blocking"),
                 new JavaClassValidator().withTitle(
                    JvmAnalysisBundle.message("jvm.inspections.blocking.method.annotation.configure.add.blocking.title"))
                  .annotationsOnly()),
      stringList("myNonBlockingAnnotations", JvmAnalysisBundle.message("jvm.inspections.blocking.method.annotation.non-blocking"),
                 new JavaClassValidator().withTitle(
                    JvmAnalysisBundle.message("jvm.inspections.blocking.method.annotation.configure.add.non-blocking.title"))
                  .annotationsOnly())
    );
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
      if (visitConstructor(element)) return;

      UCallExpression callExpression = AnalysisUastUtil.getUCallExpression(element);
      if (callExpression == null) return;
      PsiElement elementToHighLight = AnalysisUastUtil.getMethodIdentifierSourcePsi(callExpression);
      if (elementToHighLight == null) return;

      // implicit delegating constructor, check it in visitConstructor
      if (callExpression.getKind() == UastCallKind.CONSTRUCTOR_CALL && elementToHighLight.getTextRange().isEmpty()) {
        return;
      }

      ContextType contextType = isContextNonBlockingFor(element, myNonBlockingContextCheckers, mySettings);
      if (contextType instanceof ContextType.Blocking) {
        return;
      }
      ProgressIndicatorProvider.checkCanceled();

      PsiMethod referencedMethod = callExpression.resolve();
      if (referencedMethod == null) return;

      if (!isMethodOrSupersBlocking(referencedMethod, myBlockingMethodCheckers, mySettings)) return;

      if (contextType instanceof ContextType.Unsure && myConsiderUnknownContextBlocking) {
        if (myHolder.isOnTheFly()) {
          myHolder.registerProblem(
            elementToHighLight,
            JvmAnalysisBundle.message("jvm.inspections.blocking.method.consider.unknown.context.nonblocking"),
            ProblemHighlightType.INFORMATION,
            new ConsiderUnknownContextBlockingFix(false));
        }
        return;
      }

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

    private boolean visitConstructor(@NotNull PsiElement element) {
      var method = UastContextKt.toUElement(element, UMethod.class);
      if (method == null || !method.isConstructor()) return false;

      var anchor = method.getUastAnchor();
      if (anchor == null) return false;
      var elementToHighlight = anchor.getSourcePsi();
      if (elementToHighlight == null) return false;

      if (!(method.getUastParent() instanceof UClass containingClass)) return false;
      if (containingClass.getJavaPsi().getSuperClass() == null) return false;
      if (!(method.getUastBody() instanceof UBlockExpression body)) return false;

      var firstExpression = ContainerUtil.getFirstItem(body.getExpressions());
      if (firstExpression != null && isExplicitSuperCall(firstExpression)) return false;

      ContextType contextType = isContextNonBlockingFor(element, myNonBlockingContextCheckers, mySettings);
      if (contextType instanceof ContextType.Blocking) {
        return true;
      }

      if (!(contextType instanceof ContextType.NonBlocking nonBlockingContext)) return true;

      var implicitlyCalledCtr = findFirstExplicitNoArgConstructor(containingClass.getJavaPsi().getSuperClass());
      if (implicitlyCalledCtr == null) return true;
      if (!isMethodBlocking(implicitlyCalledCtr, myBlockingMethodCheckers, mySettings)) return true;

      String message;
      if (StringUtil.isNotEmpty(nonBlockingContext.getDescription())) {
        String contextDescription = nonBlockingContext.getDescription();
        message = JvmAnalysisBundle.message("jvm.inspections.blocking.method.in.implicit.ctr.problem.wildcard.descriptor", contextDescription);
      }
      else {
        message = JvmAnalysisBundle.message("jvm.inspections.blocking.method.in.implicit.ctr.problem.descriptor");
      }
      myHolder.registerProblem(elementToHighlight, message);

      return true;
    }

    private static @Nullable PsiMethod findFirstExplicitNoArgConstructor(@NotNull PsiClass currentClass) {
      while (currentClass != null) {
        var explicitEmptyArgCtr = ContainerUtil.find(currentClass.getConstructors(), ctr -> !ctr.hasParameters());
        if (explicitEmptyArgCtr != null) {
          return explicitEmptyArgCtr;
        }
        currentClass = currentClass.getSuperClass();
      }

      return null;
    }

    private static boolean isExplicitSuperCall(@NotNull UExpression expression) {
      if (!(expression instanceof USuperExpression) &&
          !(expression instanceof UCallExpression call && call.getKind() == UastCallKind.CONSTRUCTOR_CALL)) return true;
      var sourcePsi = expression.getSourcePsi();
      if (sourcePsi == null) return false;
      return !sourcePsi.getTextRange().isEmpty();
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

  private class ConsiderUnknownContextBlockingFix implements LocalQuickFix, LowPriorityAction {
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
