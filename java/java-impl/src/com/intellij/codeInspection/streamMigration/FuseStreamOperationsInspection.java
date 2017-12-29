// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.streamMigration;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.streamMigration.CollectMigration.CollectTerminal;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.ArrayUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Function;

import static com.intellij.util.ObjectUtils.tryCast;

public class FuseStreamOperationsInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final CallMatcher STREAM_COLLECT =
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_STREAM_STREAM, "collect").parameterTypes("java.util.stream.Collector");
  private static final CallMatcher COLLECT_TO_COLLECTION =
    CallMatcher.anyOf(
      CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_STREAM_COLLECTORS, "toList", "toSet").parameterCount(0),
      CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_STREAM_COLLECTORS, "toCollection").parameterCount(1));

  private static class StreamCollectChain extends CollectTerminal {
    final PsiMethodCallExpression myCollector;
    final PsiMethodCallExpression myChain;

    protected StreamCollectChain(PsiLocalVariable variable,
                                 PsiMethodCallExpression chain,
                                 PsiMethodCallExpression collector) {
      super(variable, null, ControlFlowUtils.InitializerUsageStatus.DECLARED_JUST_BEFORE);
      myCollector = collector;
      myChain = chain;
    }

    @Override
    String generateIntermediate(CommentTracker ct) {
      PsiExpression qualifier = myChain.getMethodExpression().getQualifierExpression();
      return ct.text(Objects.requireNonNull(qualifier));
    }

    @Override
    String generateTerminal(CommentTracker ct) {
      return ".collect(" + ct.text(myCollector) + ")";
    }

    private static PsiClass resolveClassCreatedByFunction(PsiExpression function) {
      function = PsiUtil.skipParenthesizedExprDown(function);
      if (function instanceof PsiMethodReferenceExpression && ((PsiMethodReferenceExpression)function).isConstructor()) {
        PsiElement target = ((PsiMethodReferenceExpression)function).resolve();
        return target instanceof PsiClass ? ((PsiClass)target) :
               target instanceof PsiMethod ? ((PsiMethod)target).getContainingClass() :
               null;
      }
      if (function instanceof PsiLambdaExpression) {
        PsiExpression body = LambdaUtil.extractSingleExpressionFromBody(((PsiLambdaExpression)function).getBody());
        PsiNewExpression newExpression = tryCast(PsiUtil.skipParenthesizedExprDown(body), PsiNewExpression.class);
        if (newExpression != null && newExpression.getAnonymousClass() == null && newExpression.getQualifier() == null &&
            newExpression.getArgumentList() != null && newExpression.getArgumentList().getExpressions().length == 0) {
          PsiJavaCodeReferenceElement classReference = newExpression.getClassReference();
          if (classReference != null) {
            return tryCast(classReference.resolve(), PsiClass.class);
          }
        }
      }
      return null;
    }

    @Override
    String getIntermediateStepsFromCollection() {
      String name = myCollector.getMethodExpression().getReferenceName();
      if ("toList".equals(name)) return "";
      if ("toSet".equals(name)) return ".distinct()";
      if ("toCollection".equals(name)) {
        PsiExpression collectionFunction = myCollector.getArgumentList().getExpressions()[0];
        PsiClass psiClass = resolveClassCreatedByFunction(collectionFunction);
        if (psiClass == null) return null;
        return CollectMigration.INTERMEDIATE_STEPS.get(psiClass.getQualifiedName());
      }
      return null;
    }
  }

  private static class StreamCollectChainNoVar extends StreamCollectChain {

    protected StreamCollectChainNoVar(PsiMethodCallExpression chain, PsiMethodCallExpression collector) {
      super(null, chain, collector);
    }

    @Override
    StreamEx<PsiExpression> targetReferences() {
      return StreamEx.of(myChain);
    }

    @Override
    boolean isTargetReference(PsiExpression expression) {
      return expression == myChain;
    }
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel8OrHigher(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression call) {
        if (STREAM_COLLECT.test(call)) {
          PsiMethodCallExpression arg =
            tryCast(PsiUtil.skipParenthesizedExprDown(call.getArgumentList().getExpressions()[0]), PsiMethodCallExpression.class);
          if (COLLECT_TO_COLLECTION.test(arg)) {
            CollectTerminal newTerminal = extractTerminal(call);
            if (newTerminal == null) return;
            PsiElement nameElement = call.getMethodExpression().getReferenceNameElement();
            if (nameElement == null) return;
            String fusedSteps = newTerminal.fusedElements()
              .mapLastOrElse(s -> StreamEx.of(", ", s), s -> StreamEx.of(" and ", s))
              .flatMap(Function.identity()).skip(1).joining();
            holder.registerProblem(nameElement,
                                   InspectionsBundle.message("inspection.fuse.stream.operations.message", fusedSteps),
                                   new FuseStreamOperationsFix(fusedSteps));
          }
        }
      }
    };
  }

  @Nullable
  private static CollectTerminal extractTerminal(PsiMethodCallExpression streamChain) {
    if(streamChain.getMethodExpression().getQualifierExpression() == null) return null;
    PsiMethodCallExpression collector =
      tryCast(PsiUtil.skipParenthesizedExprDown(ArrayUtil.getFirstElement(streamChain.getArgumentList().getExpressions())),
              PsiMethodCallExpression.class);
    PsiLocalVariable var = tryCast(streamChain.getParent(), PsiLocalVariable.class);
    CollectTerminal terminal;
    PsiElement nextElement;
    if (var == null) {
      terminal = new StreamCollectChainNoVar(streamChain, collector);
      nextElement = RefactoringUtil.getParentStatement(streamChain, false);
    }
    else {
      PsiDeclarationStatement declaration = tryCast(var.getParent(), PsiDeclarationStatement.class);
      if (declaration == null || declaration.getDeclaredElements().length != 1) return null;
      terminal = new StreamCollectChain(var, streamChain, collector);
      nextElement = PsiTreeUtil.skipWhitespacesAndCommentsForward(declaration);
    }
    CollectTerminal newTerminal = CollectMigration.includePostStatements(terminal, nextElement);
    if (newTerminal == terminal) return null;
    return newTerminal;
  }

  private static class FuseStreamOperationsFix implements LocalQuickFix {
    private String myFusedSteps;

    public FuseStreamOperationsFix(String fusedSteps) {
      myFusedSteps = fusedSteps;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return InspectionsBundle.message("inspection.fuse.stream.operations.fix.name", myFusedSteps);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.fuse.stream.operations.fix.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiMethodCallExpression chain = PsiTreeUtil.getParentOfType(descriptor.getStartElement(), PsiMethodCallExpression.class);
      if (chain == null) return;
      CollectTerminal terminal = extractTerminal(chain);
      if (terminal == null) return;
      CommentTracker ct = new CommentTracker();
      String stream = terminal.generateIntermediate(ct) + terminal.generateTerminal(ct);
      PsiElement toReplace = terminal.getElementToReplace();
      PsiElement result;
      if (toReplace != null) {
        result = ct.replaceAndRestoreComments(toReplace, stream);
      }
      else {
        PsiVariable variable = Objects.requireNonNull(terminal.getTargetVariable());
        PsiExpression initializer = Objects.requireNonNull(variable.getInitializer());
        result = ct.replaceAndRestoreComments(initializer, stream);
      }
      terminal.cleanUp();
      LambdaCanBeMethodReferenceInspection.replaceAllLambdasWithMethodReferences(result);
    }
  }
}
