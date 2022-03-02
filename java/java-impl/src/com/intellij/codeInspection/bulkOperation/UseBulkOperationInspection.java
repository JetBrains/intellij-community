// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.bulkOperation;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.codeInspection.util.IteratorDeclaration;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.callMatcher.CallMatcher;
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

  private static final CallMatcher MAP_ENTRY_SET =
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_MAP, "entrySet").parameterCount(0);
  private static final CallMatcher ENTRY_GET_KEY =
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_MAP_ENTRY, "getKey").parameterCount(0);
  private static final CallMatcher ENTRY_GET_VALUE =
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_MAP_ENTRY, "getValue").parameterCount(0);

  public boolean USE_ARRAYS_AS_LIST = true;

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(JavaBundle.message("inspection.replace.with.bulk.wrap.arrays"), this,
                                          "USE_ARRAYS_AS_LIST");
  }

  @Nullable
  private static BulkMethodInfo findInfo(PsiReferenceExpression ref) {
    return StreamEx.of(BulkMethodInfoProvider.KEY.getExtensions())
      .flatMap(BulkMethodInfoProvider::consumers).findFirst(info -> info.isMyMethod(ref)).orElse(null);
  }

  @Nullable
  private static PsiExpression findIterable(PsiMethodCallExpression expression, BulkMethodInfo info) {
    PsiExpression[] args = expression.getArgumentList().getExpressions();
    if (args.length != info.getSimpleParametersCount()) return null;
    PsiElement parent = expression.getParent();
    if (parent instanceof PsiLambdaExpression) {
      return findIterableForLambda((PsiLambdaExpression)parent, args, info);
    }
    if (parent instanceof PsiExpressionStatement) {
      PsiExpressionStatement expressionStatement = (PsiExpressionStatement)parent;
      if (expressionStatement.getParent() instanceof PsiCodeBlock) {
        PsiCodeBlock codeBlock = (PsiCodeBlock)expressionStatement.getParent();
        PsiStatement[] statements = codeBlock.getStatements();
        if (codeBlock.getParent() instanceof PsiBlockStatement) {
          PsiBlockStatement blockStatement = (PsiBlockStatement)codeBlock.getParent();
          if (statements.length == 1) {
            return findIterableForSingleStatement(blockStatement, args);
          }
          PsiElement blockParent = blockStatement.getParent();
          if (statements.length == 2 && statements[1] == parent && blockParent instanceof PsiLoopStatement) {
            IteratorDeclaration declaration = IteratorDeclaration.fromLoop((PsiLoopStatement)blockParent);
            if (declaration != null) {
              if (args.length == 1 && ExpressionUtils.isReferenceTo(args[0], declaration.getNextElementVariable(statements[0]))) {
                return declaration.getIterable();
              } else if (args.length == 2) {
                if (isGetKeyAndGetValue(args, declaration.getNextElementVariable(statements[0]))) {
                  PsiMethodCallExpression entrySetCandidate = ObjectUtils.tryCast(declaration.getIterable(), PsiMethodCallExpression.class);
                  if (MAP_ENTRY_SET.test(entrySetCandidate)) {
                    return entrySetCandidate.getMethodExpression().getQualifierExpression();
                  }
                }
              }
            }
            if (blockParent instanceof PsiForStatement && statements[0] instanceof PsiDeclarationStatement) {
              PsiElement[] elements = ((PsiDeclarationStatement)statements[0]).getDeclaredElements();
              if (elements.length == 1 && elements[0] instanceof PsiLocalVariable) {
                PsiLocalVariable var = (PsiLocalVariable)elements[0];
                if (ExpressionUtils.isReferenceTo(args[0], var)) {
                  return findIterableForIndexedLoop((PsiForStatement)blockParent, var.getInitializer());
                }
              }
            }
          }
        }
        else if (codeBlock.getParent() instanceof PsiLambdaExpression && statements.length == 1) {
          return findIterableForLambda((PsiLambdaExpression)codeBlock.getParent(), args, info);
        }
      }
      else {
        return findIterableForSingleStatement(expressionStatement, args);
      }
    }
    return null;
  }

  @Nullable
  private static PsiExpression findIterableForLambda(PsiLambdaExpression lambda, PsiExpression[] args, BulkMethodInfo info) {
    PsiParameterList parameterList = lambda.getParameterList();
    PsiParameter[] parameters = parameterList.getParameters();
    int lambdaParametersCount = parameterList.getParametersCount();
    if (info.getClassName().equals(CommonClassNames.JAVA_UTIL_MAP)) {
      if (lambdaParametersCount == 1) {
        if (!isGetKeyAndGetValue(args, parameters[0])) return null;
      } else if (lambdaParametersCount == 2) {
        if (!ExpressionUtils.isReferenceTo(args[0], parameters[0]) ||
            !ExpressionUtils.isReferenceTo(args[1], parameters[1])) return null;
      } else return null;
    } else if (lambdaParametersCount != 1 || !ExpressionUtils.isReferenceTo(args[0], parameters[0])) return null;
    return findIterableForFunction(lambda);
  }

  private static boolean isGetKeyAndGetValue(PsiExpression[] args, PsiVariable variable) {
    PsiMethodCallExpression getKeyCandidate = ObjectUtils.tryCast(args[0], PsiMethodCallExpression.class);
    PsiMethodCallExpression getValueCandidate = ObjectUtils.tryCast(args[1], PsiMethodCallExpression.class);
    if (!ENTRY_GET_KEY.test(getKeyCandidate) || !ENTRY_GET_VALUE.test(getValueCandidate)) return false;
    PsiExpression getKeyQualifier = getKeyCandidate.getMethodExpression().getQualifierExpression();
    PsiExpression getValueQualifier = getValueCandidate.getMethodExpression().getQualifierExpression();
    return ExpressionUtils.isReferenceTo(getKeyQualifier, variable) && ExpressionUtils.isReferenceTo(getValueQualifier, variable);
  }

  @Nullable
  private static PsiExpression findIterableForSingleStatement(PsiStatement statement, PsiExpression[] args) {
    assert args.length == 1 || args.length == 2 : "The number of arguments must be either 1 or 2";
    PsiElement parent = statement.getParent();
    if (parent instanceof PsiForeachStatement) {
      PsiForeachStatement foreachStatement = (PsiForeachStatement)parent;
      PsiExpression iteratedValue = foreachStatement.getIteratedValue();
      if (args.length == 2) {
        if (isGetKeyAndGetValue(args, foreachStatement.getIterationParameter())) {
          PsiMethodCallExpression entrySetCandidate = ObjectUtils.tryCast(iteratedValue, PsiMethodCallExpression.class);
          if (MAP_ENTRY_SET.test(entrySetCandidate)) {
            return entrySetCandidate.getMethodExpression().getQualifierExpression();
          }
        }
      } else if (ExpressionUtils.isReferenceTo(args[0], foreachStatement.getIterationParameter())) {
        return iteratedValue;
      }
    }
    if (parent instanceof PsiLoopStatement) {
      IteratorDeclaration declaration = IteratorDeclaration.fromLoop((PsiLoopStatement)parent);
      if (declaration != null && declaration.isIteratorMethodCall(args[0], "next")) {
        return declaration.getIterable();
      }
    }
    if (parent instanceof PsiForStatement) {
      return findIterableForIndexedLoop((PsiForStatement)parent, args[0]);
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
      PsiMethodCallExpression entrySetCandidate = ObjectUtils.tryCast(parentQualifier, PsiMethodCallExpression.class);
      return MAP_ENTRY_SET.test(entrySetCandidate)
             ? ExpressionUtils.getEffectiveQualifier(entrySetCandidate.getMethodExpression())
             : ExpressionUtils.getEffectiveQualifier(parentCall.getMethodExpression());
    }
    if (MethodCallUtils.isCallToMethod(parentCall, CommonClassNames.JAVA_UTIL_MAP, null, "forEach", new PsiType[]{null})) {
      return ExpressionUtils.getEffectiveQualifier(parentCall.getMethodExpression());
    }
    if (MethodCallUtils.isCallToMethod(parentCall, CommonClassNames.JAVA_UTIL_STREAM_STREAM, null, FOR_EACH_METHOD, new PsiType[]{null}) &&
        parentQualifier instanceof PsiMethodCallExpression) {
      PsiMethodCallExpression grandParentCall = (PsiMethodCallExpression)parentQualifier;
      if (MethodCallUtils.isCallToMethod(grandParentCall, CommonClassNames.JAVA_UTIL_COLLECTION, null, "stream", PsiType.EMPTY_ARRAY)) {
        return ExpressionUtils.getEffectiveQualifier(grandParentCall.getMethodExpression());
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
        PsiExpression iterable = findIterable(call, info);
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
        PsiExpression qualifier = PsiUtil.skipParenthesizedExprDown(ExpressionUtils.getEffectiveQualifier(methodExpression));
        if (qualifier instanceof PsiQualifiedExpression) {
          PsiMethod method = PsiTreeUtil.getParentOfType(iterable, PsiMethod.class);
          // Likely we are inside of the bulk method implementation
          if (method != null && method.getName().equals(info.getBulkName())) return;
        }
        if (isSupportedQualifier(qualifier) && info.isSupportedIterable(qualifier, iterable, USE_ARRAYS_AS_LIST)) {
          holder.registerProblem(methodExpression,
                                 JavaBundle.message("inspection.replace.with.bulk.message", info.getReplacementName()),
                                 new UseBulkOperationFix(info));
        }
      }

      @Contract("null -> false")
      private boolean isSupportedQualifier(PsiExpression qualifier) {
        if (qualifier instanceof PsiQualifiedExpression) return true;
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

    UseBulkOperationFix(BulkMethodInfo info) {
      myInfo = info;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return JavaBundle.message("inspection.replace.with.bulk.fix.name", myInfo.getReplacementName());
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("inspection.replace.with.bulk.fix.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getStartElement();
      if (!(element instanceof PsiReferenceExpression)) return;
      PsiExpression qualifier = ExpressionUtils.getEffectiveQualifier((PsiReferenceExpression)element);
      if (qualifier == null) return;
      PsiExpression iterable;
      if (element instanceof PsiMethodReferenceExpression) {
        iterable = findIterableForFunction((PsiFunctionalExpression)element);
      }
      else {
        PsiElement parent = element.getParent();
        if (!(parent instanceof PsiMethodCallExpression)) return;
        iterable = findIterable((PsiMethodCallExpression)parent, myInfo);
      }
      if (iterable == null) return;
      PsiElement parent = CommonJavaRefactoringUtil.getParentStatement(iterable, false);
      if (parent == null) return;
      CommentTracker ct = new CommentTracker();
      String iterableText = calculateIterableText(iterable, ct);
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

    @NotNull
    private static String calculateIterableText(PsiExpression iterable, CommentTracker ct) {
      if (iterable instanceof PsiSuperExpression) {
        PsiJavaCodeReferenceElement qualifier = ((PsiSuperExpression)iterable).getQualifier();
        return (qualifier != null) ? ct.text(qualifier) + ".this" : "this";
      }
      String iterableText;
      PsiType type = iterable.getType();
      iterableText = ct.text(iterable);
      if (type instanceof PsiArrayType) {
        iterableText = CommonClassNames.JAVA_UTIL_ARRAYS + ".asList(" + iterableText + ")";
      }
      return iterableText;
    }
  }
}
