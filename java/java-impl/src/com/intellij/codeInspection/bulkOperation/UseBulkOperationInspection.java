// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.bulkOperation;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.codeInspection.util.IteratorDeclaration;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.regex.Pattern;

public class UseBulkOperationInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final Pattern FOR_EACH_METHOD = Pattern.compile("forEach(Ordered)?");

  public boolean USE_ARRAYS_AS_LIST = true;

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionsBundle.message("inspection.replace.with.bulk.wrap.arrays"), this,
                                          "USE_ARRAYS_AS_LIST");
  }

  @Nullable
  private static BulkMethodInfo findInfo(PsiReferenceExpression ref) {
    return StreamEx.of(BulkMethodInfoProvider.KEY.getExtensions())
      .flatMap(BulkMethodInfoProvider::consumers).findFirst(info -> info.isMyMethod(ref)).orElse(null);
  }

  @Nullable
  private static PsiExpression findIterable(PsiMethodCallExpression expression) {
    PsiExpression[] args = expression.getArgumentList().getExpressions();
    if (args.length != 1) return null;
    PsiExpression arg = args[0];
    PsiElement parent = expression.getParent();
    if (parent instanceof PsiLambdaExpression) {
      return findIterableForLambda((PsiLambdaExpression)parent, arg);
    }
    if (parent instanceof PsiExpressionStatement) {
      PsiExpressionStatement expressionStatement = (PsiExpressionStatement)parent;
      if (expressionStatement.getParent() instanceof PsiCodeBlock) {
        PsiCodeBlock codeBlock = (PsiCodeBlock)expressionStatement.getParent();
        PsiStatement[] statements = codeBlock.getStatements();
        if (codeBlock.getParent() instanceof PsiBlockStatement) {
          PsiBlockStatement blockStatement = (PsiBlockStatement)codeBlock.getParent();
          if (statements.length == 1) {
            return findIterableForSingleStatement(blockStatement, arg);
          }
          PsiElement blockParent = blockStatement.getParent();
          if (statements.length == 2 && statements[1] == parent && blockParent instanceof PsiLoopStatement) {
            IteratorDeclaration declaration = IteratorDeclaration.fromLoop((PsiLoopStatement)blockParent);
            if (declaration != null && ExpressionUtils.isReferenceTo(arg, declaration.getNextElementVariable(statements[0]))) {
              return declaration.getIterable();
            }
            if(blockParent instanceof PsiForStatement && statements[0] instanceof PsiDeclarationStatement) {
              PsiElement[] elements = ((PsiDeclarationStatement)statements[0]).getDeclaredElements();
              if(elements.length == 1 && elements[0] instanceof PsiLocalVariable) {
                PsiLocalVariable var = (PsiLocalVariable)elements[0];
                if (ExpressionUtils.isReferenceTo(arg, var)) {
                  return findIterableForIndexedLoop((PsiForStatement)blockParent, var.getInitializer());
                }
              }
            }
          }
        }
        else if (codeBlock.getParent() instanceof PsiLambdaExpression && statements.length == 1) {
          return findIterableForLambda((PsiLambdaExpression)codeBlock.getParent(), arg);
        }
      }
      else {
        return findIterableForSingleStatement(expressionStatement, arg);
      }
    }
    return null;
  }

  @Nullable
  private static PsiExpression findIterableForLambda(PsiLambdaExpression lambda, PsiExpression arg) {
    PsiParameterList parameters = lambda.getParameterList();
    if (parameters.getParametersCount() != 1) return null;
    PsiParameter parameter = parameters.getParameters()[0];
    if (ExpressionUtils.isReferenceTo(arg, parameter)) {
      return findIterableForFunction(lambda);
    }
    return null;
  }

  @Nullable
  private static PsiExpression findIterableForSingleStatement(PsiStatement statement, PsiExpression arg) {
    PsiElement parent = statement.getParent();
    if (parent instanceof PsiForeachStatement) {
      PsiForeachStatement foreachStatement = (PsiForeachStatement)parent;
      if (ExpressionUtils.isReferenceTo(arg, foreachStatement.getIterationParameter())) {
        return foreachStatement.getIteratedValue();
      }
    }
    if (parent instanceof PsiLoopStatement) {
      IteratorDeclaration declaration = IteratorDeclaration.fromLoop((PsiLoopStatement)parent);
      if (declaration != null && declaration.isIteratorMethodCall(arg, "next")) {
        return declaration.getIterable();
      }
    }
    if (parent instanceof PsiForStatement) {
      return findIterableForIndexedLoop((PsiForStatement)parent, arg);
    }
    return null;
  }

  @Nullable
  private static PsiExpression findIterableForIndexedLoop(PsiForStatement loop, PsiExpression getElementExpression) {
    CountingLoop countingLoop = CountingLoop.from(loop);
    if (countingLoop == null ||
        countingLoop.isIncluding() ||
        countingLoop.isDescending() ||
        !ExpressionUtils.isZero(countingLoop.getInitializer())) {
      return null;
    }
    IndexedContainer container = IndexedContainer.fromLengthExpression(countingLoop.getBound());
    if (container == null) return null;
    PsiExpression index = container.extractIndexFromGetExpression(getElementExpression);
    if (!ExpressionUtils.isReferenceTo(index, countingLoop.getCounter())) return null;
    return container.getQualifier();
  }

  @Nullable
  private static PsiExpression findIterableForFunction(PsiFunctionalExpression function) {
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(function.getParent());
    if (parent instanceof PsiTypeCastExpression) parent = PsiUtil.skipParenthesizedExprUp(parent.getParent());
    if (!(parent instanceof PsiExpressionList)) return null;
    PsiExpression[] args = ((PsiExpressionList)parent).getExpressions();
    if (args.length != 1) return null;
    parent = parent.getParent();
    if (!(parent instanceof PsiMethodCallExpression)) return null;
    PsiMethodCallExpression parentCall = (PsiMethodCallExpression)parent;
    PsiExpression parentQualifier = PsiUtil.skipParenthesizedExprDown(parentCall.getMethodExpression().getQualifierExpression());
    if (MethodCallUtils.isCallToMethod(parentCall, CommonClassNames.JAVA_LANG_ITERABLE, null, "forEach", new PsiType[]{null})) {
      return ExpressionUtils.getQualifierOrThis(parentCall.getMethodExpression());
    }
    if (MethodCallUtils.isCallToMethod(parentCall, CommonClassNames.JAVA_UTIL_STREAM_STREAM, null, FOR_EACH_METHOD, new PsiType[]{null}) &&
        parentQualifier instanceof PsiMethodCallExpression) {
      PsiMethodCallExpression grandParentCall = (PsiMethodCallExpression)parentQualifier;
      if (MethodCallUtils.isCallToMethod(grandParentCall, CommonClassNames.JAVA_UTIL_COLLECTION, null, "stream", PsiType.EMPTY_ARRAY)) {
        return ExpressionUtils.getQualifierOrThis(grandParentCall.getMethodExpression());
      }
      PsiExpression[] grandParentArgs = grandParentCall.getArgumentList().getExpressions();
      if (grandParentArgs.length == 1) {
        PsiExpression maybeArray = grandParentArgs[0];
        if (MethodCallUtils.isCallToStaticMethod(grandParentCall, CommonClassNames.JAVA_UTIL_ARRAYS, "stream", 1)) {
          return maybeArray;
        }
        if (MethodCallUtils.isCallToStaticMethod(grandParentCall, CommonClassNames.JAVA_UTIL_STREAM_STREAM, "of", 1)) {
          PsiType type = maybeArray.getType();
          if (type instanceof PsiArrayType) {
            return maybeArray;
          }
        }
      }
    }
    return null;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression call) {
        super.visitMethodCallExpression(call);
        BulkMethodInfo info = findInfo(call.getMethodExpression());
        if (info == null) return;
        PsiExpression iterable = findIterable(call);
        if (iterable != null) {
          register(iterable, info, call.getMethodExpression());
        }
      }

      @Override
      public void visitMethodReferenceExpression(PsiMethodReferenceExpression methodReference) {
        super.visitMethodReferenceExpression(methodReference);
        BulkMethodInfo info = findInfo(methodReference);
        if (info == null) return;
        PsiExpression iterable = findIterableForFunction(methodReference);
        if (iterable != null) {
          register(iterable, info, methodReference);
        }
      }

      private void register(@NotNull PsiExpression iterable,
                            @NotNull BulkMethodInfo info,
                            @NotNull PsiReferenceExpression methodExpression) {
        PsiExpression qualifier = ExpressionUtils.getQualifierOrThis(methodExpression);
        if (qualifier instanceof PsiThisExpression) {
          PsiMethod method = PsiTreeUtil.getParentOfType(iterable, PsiMethod.class);
          // Likely we are inside of the bulk method implementation
          if (method != null && method.getName().equals(info.getBulkName())) return;
        }
        if (isSupportedQualifier(qualifier) && info.isSupportedIterable(qualifier, iterable, USE_ARRAYS_AS_LIST)) {
          holder.registerProblem(methodExpression,
                                 InspectionsBundle.message("inspection.replace.with.bulk.message", info.getReplacementName()),
                                 new UseBulkOperationFix(info));
        }
      }

      @Contract("null -> false")
      private boolean isSupportedQualifier(PsiExpression qualifier) {
        if (qualifier instanceof PsiThisExpression) return true;
        if (qualifier instanceof PsiReferenceExpression) {
          PsiExpression subQualifier = ((PsiReferenceExpression)qualifier).getQualifierExpression();
          return subQualifier == null || isSupportedQualifier(subQualifier);
        }
        return false;
      }
    };
  }

  private static class UseBulkOperationFix implements LocalQuickFix {
    private final BulkMethodInfo myInfo;

    public UseBulkOperationFix(BulkMethodInfo info) {
      myInfo = info;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return InspectionsBundle.message("inspection.replace.with.bulk.fix.name", myInfo.getReplacementName());
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.replace.with.bulk.fix.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getStartElement();
      if (!(element instanceof PsiReferenceExpression)) return;
      PsiExpression qualifier = ExpressionUtils.getQualifierOrThis((PsiReferenceExpression)element);
      PsiExpression iterable;
      if (element instanceof PsiMethodReferenceExpression) {
        iterable = findIterableForFunction((PsiFunctionalExpression)element);
      }
      else {
        PsiElement parent = element.getParent();
        if (!(parent instanceof PsiMethodCallExpression)) return;
        iterable = findIterable((PsiMethodCallExpression)parent);
      }
      if (iterable == null) return;
      PsiElement parent = RefactoringUtil.getParentStatement(iterable, false);
      if (parent == null) return;
      CommentTracker ct = new CommentTracker();
      PsiType type = iterable.getType();
      String iterableText = ct.text(iterable);
      if (type instanceof PsiArrayType) {
        iterableText = CommonClassNames.JAVA_UTIL_ARRAYS + ".asList(" + iterableText + ")";
      }
      if (parent instanceof PsiDeclarationStatement) {
        PsiLoopStatement loop = PsiTreeUtil.getParentOfType(element, PsiLoopStatement.class);
        if (loop != null && loop.getParent() == parent.getParent()) {
          ct.delete(loop);
        }
      }
      PsiElement result = ct.replaceAndRestoreComments(parent, ct.text(qualifier) + "." + myInfo.getBulkName() + "(" + iterableText + ")"
                                                               + (parent instanceof PsiStatement ? ";" : ""));
      result = JavaCodeStyleManager.getInstance(project).shortenClassReferences(result);
      CodeStyleManager.getInstance(project).reformat(result);
    }
  }
}
