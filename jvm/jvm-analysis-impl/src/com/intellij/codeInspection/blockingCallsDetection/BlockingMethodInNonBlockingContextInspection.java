// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.blockingCallsDetection;

import com.intellij.analysis.JvmAnalysisBundle;
import com.intellij.codeInsight.options.JavaClassValidator;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.uast.UastHintedVisitorAdapter;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor;

import java.util.*;

import static com.intellij.codeInspection.options.OptPane.*;
import static java.util.Collections.emptyList;

public final class BlockingMethodInNonBlockingContextInspection extends AbstractBaseUastLocalInspectionTool {

  public BlockingMethodInNonBlockingContextInspection() {
    myConsiderUnknownContextBlocking = true;
    myConsiderSuspendContextNonBlocking = true;
  }

  public BlockingMethodInNonBlockingContextInspection(boolean considerUnknownContextBlocking, boolean considerSuspendContextNonBlocking) {
    myConsiderUnknownContextBlocking = considerUnknownContextBlocking;
    myConsiderSuspendContextNonBlocking = considerSuspendContextNonBlocking;
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
  public boolean myConsiderSuspendContextNonBlocking;

  @SuppressWarnings("unchecked")
  private final Class<? extends UElement>[] hints = new Class[]{UMethod.class, UCallExpression.class};

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("myConsiderUnknownContextBlocking",
               JvmAnalysisBundle.message("jvm.inspections.blocking.method.consider.unknown.context.blocking")),
      checkbox("myConsiderSuspendContextNonBlocking",
               JvmAnalysisBundle.message("jvm.inspections.blocking.method.consider.suspend.context.non.blocking")),
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

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    Collection<String> nonBlockingAnnotations = union(myNonBlockingAnnotations, DEFAULT_NONBLOCKING_ANNOTATIONS);
    Collection<String> blockingAnnotations = union(myBlockingAnnotations, DEFAULT_BLOCKING_ANNOTATIONS);

    List<NonBlockingContextChecker> nonBlockingContextCheckers =
      getNonBlockingContextCheckers(holder.getFile(), blockingAnnotations, nonBlockingAnnotations);
    if (nonBlockingContextCheckers.isEmpty()) return PsiElementVisitor.EMPTY_VISITOR;

    List<BlockingMethodChecker> blockingMethodCheckers =
      getBlockingMethodCheckers(holder.getFile(), blockingAnnotations, nonBlockingAnnotations);
    if (blockingMethodCheckers.isEmpty()) return PsiElementVisitor.EMPTY_VISITOR;

    var visitor = new BlockingMethodInNonBlockingContextVisitor(holder, blockingMethodCheckers, nonBlockingContextCheckers, getSettings());

    return UastHintedVisitorAdapter.create(holder.getFile().getLanguage(), new AbstractUastNonRecursiveVisitor() {
      @Override
      public boolean visitMethod(@NotNull UMethod node) {
        visitor.visitMethod(node);
        return super.visitMethod(node);
      }

      @Override
      public boolean visitCallExpression(@NotNull UCallExpression node) {
        visitor.visitCall(node);
        return super.visitCallExpression(node);
      }
    }, hints);
  }

  public BlockingCallInspectionSettings getSettings() {
    return new BlockingCallInspectionSettings(myConsiderUnknownContextBlocking, myConsiderSuspendContextNonBlocking);
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

  private class BlockingMethodInNonBlockingContextVisitor {
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

    public void visitCall(UCallExpression callExpression) {
      if (callExpression == null) return;

      var element = callExpression.getSourcePsi();
      if (element == null) return;

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
          myHolder.problem(elementToHighLight,
                           JvmAnalysisBundle.message("jvm.inspections.blocking.method.consider.unknown.context.nonblocking"))
            .highlight(ProblemHighlightType.INFORMATION)
            .fix(new UpdateInspectionOptionFix(
              BlockingMethodInNonBlockingContextInspection.this, "myConsiderUnknownContextBlocking",
              JvmAnalysisBundle.message("jvm.inspections.blocking.method.consider.unknown.context.nonblocking"), false))
            .register();
        }
        return;
      }

      ElementContext elementContext = new ElementContext(element, mySettings);
      StreamEx<LocalQuickFix> fixesStream = StreamEx.of(myBlockingMethodCheckers)
        .flatArray(checker -> checker.getQuickFixesFor(elementContext));

      if (contextType instanceof ContextType.Unsure && !myConsiderUnknownContextBlocking) {
        fixesStream = fixesStream.append(
          LocalQuickFix.from(new UpdateInspectionOptionFix(
            BlockingMethodInNonBlockingContextInspection.this, "myConsiderUnknownContextBlocking",
            JvmAnalysisBundle.message("jvm.inspections.blocking.method.consider.unknown.context.blocking"), true)));
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

    public void visitMethod(UMethod method) {
      if (method == null || !method.isConstructor()) return;

      var element = method.getSourcePsi();
      if (element == null) return;

      var anchor = method.getUastAnchor();
      if (anchor == null) return;
      var elementToHighlight = anchor.getSourcePsi();
      if (elementToHighlight == null) return;

      if (!(method.getUastParent() instanceof UClass containingClass)) return;
      if (containingClass.getJavaPsi().getSuperClass() == null) return;
      if (!(method.getUastBody() instanceof UBlockExpression body)) return;

      var firstExpression = ContainerUtil.getFirstItem(body.getExpressions());
      if (firstExpression != null && isExplicitSuperCall(firstExpression)) return;

      ContextType contextType = isContextNonBlockingFor(element, myNonBlockingContextCheckers, mySettings);
      if (contextType instanceof ContextType.Blocking) {
        return;
      }

      if (!(contextType instanceof ContextType.NonBlocking nonBlockingContext)) return;

      var implicitlyCalledCtr = findFirstExplicitNoArgConstructor(containingClass.getJavaPsi().getSuperClass());
      if (implicitlyCalledCtr == null) return;
      if (!isMethodBlocking(implicitlyCalledCtr, myBlockingMethodCheckers, mySettings)) return;

      String message;
      if (StringUtil.isNotEmpty(nonBlockingContext.getDescription())) {
        String contextDescription = nonBlockingContext.getDescription();
        message =
          JvmAnalysisBundle.message("jvm.inspections.blocking.method.in.implicit.ctr.problem.wildcard.descriptor", contextDescription);
      }
      else {
        message = JvmAnalysisBundle.message("jvm.inspections.blocking.method.in.implicit.ctr.problem.descriptor");
      }
      myHolder.registerProblem(elementToHighlight, message);
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
          !(expression instanceof UCallExpression call && call.getKind() == UastCallKind.CONSTRUCTOR_CALL)) {
        return true;
      }
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
}
