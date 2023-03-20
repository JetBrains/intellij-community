// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.intention.impl.RemoveRedundantParameterTypesFix;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static com.intellij.util.ObjectUtils.tryCast;

public class ComparatorCombinatorsInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance(ComparatorCombinatorsInspection.class);


  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel8OrHigher(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitLambdaExpression(@NotNull PsiLambdaExpression lambda) {
        super.visitLambdaExpression(lambda);
        PsiType type = lambda.getFunctionalInterfaceType();
        PsiElement parent = PsiUtil.skipParenthesizedExprUp(lambda.getParent());
        if (parent instanceof PsiTypeCastExpression && ((PsiTypeCastExpression)parent).getType() instanceof PsiIntersectionType) {
          return;
        }
        PsiParameter[] parameters = lambda.getParameterList().getParameters();
        if (parameters.length != 2 || !PsiTypesUtil.classNameEquals(type, CommonClassNames.JAVA_UTIL_COMPARATOR)) {
          return;
        }
        String replacementText = generateSimpleCombinator(lambda, parameters[0], parameters[1]);
        if (replacementText != null) {
          if (!LambdaUtil.isSafeLambdaReplacement(lambda, replacementText)) return;
          String qualifiedName = Objects.requireNonNull(StringUtil.substringBefore(replacementText, "("));
          @NonNls String methodName = "Comparator." + StringUtil.getShortName(qualifiedName);
          final String problemMessage = InspectionGadgetsBundle.message("inspection.comparator.combinators.description2", methodName);
          holder.registerProblem(lambda, problemMessage, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new ReplaceWithComparatorFix(CommonQuickFixBundle.message("fix.replace.with.x", methodName)));
          return;
        }
        if (lambda.getBody() instanceof PsiCodeBlock) {
          PsiStatement[] statements = ((PsiCodeBlock)lambda.getBody()).getStatements();
          List<ComparisonBlock> blocks = extractComparisonChain(statements, parameters[0], parameters[1]);
          if (blocks == null) return;
          String chainCombinator = generateChainCombinator(blocks, parameters[0], parameters[1]);
          if (chainCombinator == null) return;
          if (!LambdaUtil.isSafeLambdaReplacement(lambda, chainCombinator)) return;
          final String problemMessage = InspectionGadgetsBundle.message("inspection.comparator.combinators.description");
          holder.registerProblem(lambda, problemMessage, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new ReplaceWithComparatorFix(InspectionGadgetsBundle.message("inspection.comparator.combinators.fix.chain")));
        }
      }
    };
  }


  @Nullable("when failed to extract")
  private static List<ComparisonBlock> extractComparisonChain(PsiStatement @NotNull [] statements,
                                                              @NotNull PsiVariable first,
                                                              @NotNull PsiVariable second) {
    if (statements.length == 0) return null;
    ComparisonBlock firstBlock = ComparisonBlock.extractBlock(statements[0], first, second, null);
    if (firstBlock == null) return null;
    List<ComparisonBlock> blocks = new ArrayList<>();
    blocks.add(firstBlock);
    PsiVariable lastResult = firstBlock.getResult();
    int index = 1;
    while (index < statements.length - 1) {
      PsiStatement current = statements[index];
      if (isNotZeroCheck(current, lastResult)) {
        int nextIndex = index + 1;
        if (nextIndex >= statements.length) return null;
        PsiStatement next = statements[nextIndex];
        ComparisonBlock block = ComparisonBlock.extractBlock(next, first, second, lastResult);
        if (block == null) {
          if (nextIndex == statements.length - 1) break;
          return null;
        }
        blocks.add(block);
        index += 2;
        continue;
      }
      PsiStatement nextComparisonStmt = extractZeroCheckedWay(current, lastResult);
      if (nextComparisonStmt != null) {
        ComparisonBlock block = ComparisonBlock.extractBlock(nextComparisonStmt, first, second, lastResult);
        if (block == null) return null;
        blocks.add(block);
        if(block.getResult() != lastResult) lastResult = block.getResult();
        index++;
        continue;
      }
      return null;
    }
    PsiStatement lastStmt = statements[statements.length - 1];
    if (lastStmt instanceof PsiReturnStatement) {
      PsiExpression returnExpr = ((PsiReturnStatement)lastStmt).getReturnValue();
      if (returnExpr == null) return null;
      if (ExpressionUtils.isReferenceTo(returnExpr, lastResult)) {
        return blocks;
      }
      ComparisonBlock lastBlock = extractTernaryComparison(first, second, lastResult, returnExpr);
      if (lastBlock == null) {
        lastBlock = ComparisonBlock.extractBlock(returnExpr, first, second, lastResult);
      }
      if (lastBlock == null) return null;
      blocks.add(lastBlock);
      return blocks;
    }
    return null;
  }

  //res == 0 ? first.compareTo(second) : res
  @Nullable
  private static ComparisonBlock extractTernaryComparison(@NotNull PsiVariable first,
                                                          @NotNull PsiVariable second,
                                                          PsiVariable lastResult,
                                                          PsiExpression returnExpr) {
    PsiConditionalExpression ternary = tryCast(returnExpr, PsiConditionalExpression.class);
    if (ternary == null) return null;
    PsiExpression elseExpression = ternary.getElseExpression();
    PsiExpression thenExpression = ternary.getThenExpression();
    if (elseExpression == null || thenExpression == null) return null;
    PsiBinaryExpression binOp = tryCast(ternary.getCondition(), PsiBinaryExpression.class);
    if (binOp == null) return null;
    PsiExpression finalResult = ExpressionUtils.getValueComparedWithZero(binOp);
    boolean inverted = false;
    if (finalResult == null) {
      finalResult = ExpressionUtils.getValueComparedWithZero(binOp, JavaTokenType.NE);
      inverted = true;
    }
    if (!ExpressionUtils.isReferenceTo(finalResult, lastResult)) return null;
    if (!ExpressionUtils.isReferenceTo(inverted ? thenExpression : elseExpression, lastResult)) return null;
    PsiMethodCallExpression lastComparison = tryCast(inverted ? elseExpression : thenExpression, PsiMethodCallExpression.class);
    ComparisonBlock lastBlock = ComparisonBlock.extractBlock(lastComparison, first, second, lastResult);
    if (lastBlock == null) return null;
    return lastBlock;
  }

  /**
   * @param statement statement like:
   *                  if(res == 0) res = o1.second.compareTo(o2.second);
   *                  if(res == 0) {int res = o1.second.compareTo(o2.second);}
   * @return statement of then branch
   */
  @Nullable
  private static PsiStatement extractZeroCheckedWay(@Nullable PsiStatement statement, @NotNull PsiVariable last) {
    PsiIfStatement ifStatement = tryCast(statement, PsiIfStatement.class);
    if (ifStatement == null || ifStatement.getElseBranch() != null) return null;
    PsiBinaryExpression binOp = tryCast(ifStatement.getCondition(), PsiBinaryExpression.class);
    if (binOp == null) return null;
    PsiExpression maybeResult = ExpressionUtils.getValueComparedWithZero(binOp);
    if (!ExpressionUtils.isReferenceTo(maybeResult, last)) return null;
    return ControlFlowUtils.stripBraces(ifStatement.getThenBranch());
  }

  /**
   * @param statement like if(res != 0) return res;
   * @param last      result of last comparison
   */
  private static boolean isNotZeroCheck(@NotNull PsiStatement statement, @NotNull PsiVariable last) {
    PsiIfStatement ifStatement = tryCast(statement, PsiIfStatement.class);
    if (ifStatement == null || ifStatement.getElseBranch() != null) return false;
    PsiBinaryExpression binaryExpression = tryCast(ifStatement.getCondition(), PsiBinaryExpression.class);
    if (binaryExpression == null) return false;
    if (!ExpressionUtils.isReferenceTo(ExpressionUtils.getValueComparedWithZero(binaryExpression, JavaTokenType.NE), last)) return false;
    PsiStatement thenStmt = ControlFlowUtils.stripBraces(ifStatement.getThenBranch());
    if (!(thenStmt instanceof PsiReturnStatement)) return false;
    return ExpressionUtils.isReferenceTo(((PsiReturnStatement)thenStmt).getReturnValue(), last);
  }

  private static final class ComparisonBlock {
    private final PsiExpression myKey; // second operand expression
    private final PsiVariable myResult;

    private ComparisonBlock(PsiExpression key, PsiVariable result) {
      myKey = key;
      myResult = result;
    }

    public PsiExpression getKey() {
      return myKey;
    }

    public PsiVariable getResult() {
      return myResult;
    }

    /**
     * extracts comparison block from statement
     * int res = o1.first.compareTo(o2.first);
     * or
     * res = o1.first.compareTo(o2.first);
     */
    @Nullable
    static ComparisonBlock extractBlock(@NotNull PsiStatement statement,
                                        @NotNull PsiVariable firstParam,
                                        @NotNull PsiVariable secondParam,
                                        @Nullable PsiVariable previousResult) {
      if (statement instanceof PsiDeclarationStatement declaration) {
        PsiElement[] elements = declaration.getDeclaredElements();
        if (elements.length == 0) return null;
        PsiLocalVariable variable = tryCast(elements[0], PsiLocalVariable.class);
        if (variable == null) return null;
        PsiExpression initializer = PsiUtil.skipParenthesizedExprDown(variable.getInitializer());
        return extractBlock(initializer, firstParam, secondParam, variable);
      }
      else if (previousResult != null && statement instanceof PsiExpressionStatement) {
        PsiExpression expr = ExpressionUtils.getAssignmentTo(((PsiExpressionStatement)statement).getExpression(), previousResult);
        return extractBlock(expr, firstParam, secondParam, previousResult);
      }
      return null;
    }

    @Contract("null, _, _, _ -> null")
    @Nullable
    private static ComparisonBlock extractBlock(@Nullable PsiExpression expr,
                                                @NotNull PsiVariable firstParam,
                                                @NotNull PsiVariable secondParam,
                                                @NotNull PsiVariable variable) {
      PsiExpression first = null;
      PsiExpression second = null;
      if (expr instanceof PsiMethodCallExpression call) {
        PsiExpression[] parameters = call.getArgumentList().getExpressions();
        if (PrimitiveComparison.from(call) != null) {
          first = parameters[0];
          second = parameters[1];
        } else if (MethodCallUtils.isCompareToCall(call)) {
          first = call.getMethodExpression().getQualifierExpression();
          second = ArrayUtil.getFirstElement(call.getArgumentList().getExpressions());
        }
      }
      else if (expr instanceof PsiBinaryExpression binOp) {
        if(binOp.getOperationTokenType() != JavaTokenType.MINUS) return null;
        first = binOp.getLOperand();
        if(getComparingMethodName(first.getType(), true) == null) return null;
        second = binOp.getROperand();
      }
      if(first == null || second == null) return null;
      if (!usagesAreAllowed(firstParam, secondParam, first, second)) return null;
      if (!keyAccessEquivalent(firstParam, secondParam, first, second)) return null;
      return new ComparisonBlock(second, variable);
    }

    private static boolean keyAccessEquivalent(@NotNull PsiVariable firstParam,
                                               @NotNull PsiVariable secondParam,
                                               PsiExpression first,
                                               PsiExpression second) {
      String secondParamName = secondParam.getName();
      if (secondParamName == null) return false;
      return areEquivalent(firstParam, first, secondParam, second);
    }

    private static boolean usagesAreAllowed(@NotNull PsiVariable firstParam,
                                            @NotNull PsiVariable secondParam,
                                            @Nullable PsiExpression firstExpr,
                                            @Nullable PsiExpression secondExpr) {
      return VariableAccessUtils.variableIsUsed(firstParam, firstExpr) &&
             VariableAccessUtils.variableIsUsed(secondParam, secondExpr) &&
             !VariableAccessUtils.variableIsUsed(firstParam, secondExpr) &&
             !VariableAccessUtils.variableIsUsed(secondParam, firstExpr);
    }
  }

  @NotNull
  private static String generateComparison(@NotNull String methodName,
                                           @Nullable PsiType type,
                                           @NotNull String varName,
                                           @NotNull PsiExpression expression,
                                           @NotNull PsiVariable exprVariable) {
    String lambdaExpr = getExpressionReplacingReferences(expression, varName, exprVariable);
    String parameter =
      type == null ? varName : "(" + GenericsUtil.getVariableTypeByExpressionType(type).getCanonicalText() + " " + varName + ")";
    return methodName + "(" + parameter + "->" + lambdaExpr + ")";
  }

  @Nullable
  private static String generateSimpleCombinator(PsiLambdaExpression lambda,
                                                 PsiParameter leftVar, PsiParameter rightVar) {
    PsiExpression body = PsiUtil.skipParenthesizedExprDown(LambdaUtil.extractSingleExpressionFromBody(lambda.getBody()));
    PsiExpression left;
    @NonNls String methodName = null;
    if (body instanceof PsiMethodCallExpression methodCall) {
      if (MethodCallUtils.isCompareToCall(methodCall)) {
        left = methodCall.getMethodExpression().getQualifierExpression();
        PsiExpression right = ArrayUtil.getFirstElement(methodCall.getArgumentList().getExpressions());
        if (ExpressionUtils.isReferenceTo(left, leftVar) && ExpressionUtils.isReferenceTo(right, rightVar)) {
          methodName = "naturalOrder";
        }
        else if (ExpressionUtils.isReferenceTo(right, leftVar) && ExpressionUtils.isReferenceTo(left, rightVar)) {
          methodName = "reverseOrder";
        }
        else if (areEquivalent(leftVar, left, rightVar, right)) {
          methodName = "comparing";
        }
      }
      else {
        PrimitiveComparison comparison = PrimitiveComparison.from(methodCall);
        if (comparison == null) return null;
        PsiExpression[] args = methodCall.getArgumentList().getExpressions();
        if (!areEquivalent(leftVar, args[0], rightVar, args[1])) return null;
        methodName = comparison.getMethodName();
        left = comparison.getKeyExtractor();
      }
    }
    else if (body instanceof PsiBinaryExpression binOp) {
      if (!binOp.getOperationTokenType().equals(JavaTokenType.MINUS)) return null;
      left = binOp.getLOperand();
      PsiType type = left.getType();
      if (type == null) return null;
      if (areEquivalent(leftVar, left, rightVar, binOp.getROperand())) {
        methodName = getComparingMethodName(type.getCanonicalText());
      }
    }
    else {
      return null;
    }
    if (methodName == null) return null;
    @NonNls String text;
    if (!methodName.startsWith("comparing")) {
      text = "java.util.Comparator." + methodName + "()";
    }
    else {
      String parameterName = leftVar.getName();
      PsiTypeElement typeElement = leftVar.getTypeElement();
      if (typeElement != null && PsiUtilCore.hasErrorElementChild(typeElement)) return null;
      String typeText = typeElement == null ? GenericsUtil.getVariableTypeByExpressionType(leftVar.getType()).getCanonicalText() :
                        typeElement.getText();
      String parameterDeclaration = "(" + typeText + " " + parameterName + ")";
      text = "java.util.Comparator." + methodName + "(" +
             (parameterDeclaration + " -> " + left.getText()) + ")";
    }
    return text;
  }

  @Nullable
  private static String generateChainCombinator(@NotNull List<ComparisonBlock> blocks,
                                                @NotNull PsiVariable firstVar,
                                                @NotNull PsiVariable secondVar) {
    if (blocks.size() < 2) return null;
    ComparisonBlock first = blocks.get(0);
    StringBuilder builder = new StringBuilder();
    PsiType type = secondVar.getType();
    String name = suggestVarName(firstVar);
    if(name == null) return null;

    PsiExpression firstKey = first.getKey();
    String firstMethodName = getComparingMethodName(firstKey.getType(), true);
    if(firstMethodName == null) return null;
    builder.append(CommonClassNames.JAVA_UTIL_COMPARATOR).append(".")
      .append(generateComparison(firstMethodName, type, name, firstKey, secondVar));
    for (int i = 1; i < blocks.size(); i++) {
      ComparisonBlock block = blocks.get(i);
      PsiExpression blockKey = block.getKey();
      String comparatorMethodName = getComparingMethodName(blockKey.getType(), false);
      if(comparatorMethodName == null) return null;
      builder.append(".").append(generateComparison(comparatorMethodName, null, name, blockKey, secondVar));
    }
    return builder.toString();
  }

  @Contract("null, _ -> null")
  private static @Nullable @NonNls String getComparingMethodName(@Nullable PsiType exprType, boolean first) {
    if(exprType == null) return null;
    String name = getComparingMethodName(exprType.getCanonicalText(), first);
    if(name != null) return name;
    if (InheritanceUtil.isInheritor(exprType, CommonClassNames.JAVA_LANG_COMPARABLE)) {
      return first ?  "comparing" : "thenComparing";
    }
    return null;
  }

  @Nullable
  private static String suggestVarName(@NotNull PsiVariable variable) {
    String name = variable.getName();
    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(variable.getProject());
    SuggestedNameInfo nameCandidate = null;
    if (name != null) {
      if (name.length() > 1 && name.endsWith("1")) {
        nameCandidate = codeStyleManager.suggestVariableName(VariableKind.PARAMETER, name.substring(0, name.length() - 1),
                                                             null, variable.getType(), true);
      } else if (name.equals("first")) {
        nameCandidate =
          codeStyleManager.suggestVariableName(VariableKind.PARAMETER, null, null, variable.getType(), true);
      }
    }
    if(nameCandidate != null) {
      String[] names = codeStyleManager.suggestUniqueVariableName(nameCandidate, variable, true).names;
      if (names.length > 0) {
        return names[0];
      }
    }
    return name;
  }

  private static String getExpressionReplacingReferences(@NotNull PsiExpression expression,
                                                         @NotNull String varName,
                                                         @NotNull PsiVariable exprVariable) {
    PsiExpression copy = (PsiExpression)expression.copy();
    ReferencesSearch.search(exprVariable, new LocalSearchScope(copy))
      .forEach(reference ->{
        PsiReferenceExpression ref = tryCast(reference.getElement(), PsiReferenceExpression.class);
        if(ref == null) return;
        ExpressionUtils.bindReferenceTo(ref, varName);
      });
    return copy.getText();
  }

  @Contract(value = "null -> null", pure = true)
  @Nullable
  private static String getComparingMethodName(String type) {
    return getComparingMethodName(type, true);
  }

  @Contract(value = "null, _ -> null", pure = true)
  private static @Nullable @NonNls String getComparingMethodName(String type, boolean first) {
    if(type == null) return null;
    return switch (PsiTypesUtil.unboxIfPossible(type)) {
      case "int", "short", "byte", "char" -> first ? "comparingInt" : "thenComparingInt";
      case "long" -> first ? "comparingLong" : "thenComparingLong";
      case "double" -> first ? "comparingDouble" : "thenComparingDouble";
      default -> null;
    };
  }

  @Contract("_, null, _, _ -> false; _, !null, _, null -> false")
  private static boolean areEquivalent(PsiVariable leftVar, @Nullable PsiExpression left,
                                       PsiVariable rightVar, @Nullable PsiExpression right) {
    if (left == null || right == null) return false;
    if (VariableAccessUtils.variableIsUsed(rightVar, left) ||
        VariableAccessUtils.variableIsUsed(leftVar, right)) {
      return false;
    }
    PsiExpression copy = (PsiExpression)right.copy();
    PsiElement[] rightRefs = PsiTreeUtil.collectElements(copy, e -> e instanceof PsiReferenceExpression &&
                                                                    ((PsiReferenceExpression)e).resolve() == rightVar);
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(left.getProject());
    String paramName = leftVar.getName();
    if (paramName == null) return false;
    for (PsiElement ref : rightRefs) {
      PsiElement nameElement = ((PsiReferenceExpression)ref).getReferenceNameElement();
      LOG.assertTrue(nameElement != null);
      nameElement.replace(factory.createIdentifier(paramName));
    }
    return EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(left, copy);
  }

  private static final class PrimitiveComparison {
    private final @NotNull PsiExpression myKeyExtractor;
    private final @NotNull String myMethodName;

    private PrimitiveComparison(@NotNull PsiExpression extractor, @NotNull String name) {
      myKeyExtractor = extractor;
      myMethodName = name;
    }

    @NotNull
    public PsiExpression getKeyExtractor() {
      return myKeyExtractor;
    }

    @NotNull
    public String getMethodName() {
      return myMethodName;
    }

    @Nullable
    static private PrimitiveComparison from(PsiMethodCallExpression methodCall) {
      PsiMethod method = methodCall.resolveMethod();
      if (method != null && method.getName().equals("compare")) {
        PsiClass containingClass = method.getContainingClass();
        if (containingClass != null) {
          String className = containingClass.getQualifiedName();
          if (className != null) {
            PsiExpression[] args = methodCall.getArgumentList().getExpressions();
            if (args.length != 2) return null;
            PsiExpression keyExtractor = args[0];
            String methodName = getComparingMethodName(className);
            if(methodName == null) return null;
            return new PrimitiveComparison(keyExtractor, methodName);
          }
        }
      }
      return null;
    }
  }

  static class ReplaceWithComparatorFix implements LocalQuickFix {
    private final @Nls String myMessage;

    ReplaceWithComparatorFix(@Nls String message) {
      myMessage = message;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return myMessage;
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("replace.with.comparator.fix.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getStartElement();
      if (!(element instanceof PsiLambdaExpression lambda)) return;
      PsiParameter[] parameters = lambda.getParameterList().getParameters();
      if (parameters.length != 2) return;
      boolean keepParameterTypes = parameters[0].getTypeElement() != null;
      if (lambda.getBody() instanceof PsiCodeBlock) {
        PsiStatement[] statements = ((PsiCodeBlock)lambda.getBody()).getStatements();
        if(statements.length > 1) {
          List<ComparisonBlock> chain = extractComparisonChain(statements, parameters[0], parameters[1]);
          if (chain == null) return;
          String code = generateChainCombinator(chain, parameters[0], parameters[1]);
          if (code == null) return;
          PsiElement result = new CommentTracker().replaceAndRestoreComments(lambda, code);
          RemoveRedundantTypeArgumentsUtil.removeRedundantTypeArguments(result);
          LambdaCanBeMethodReferenceInspection.replaceAllLambdasWithMethodReferences(result);
          CodeStyleManager.getInstance(project).reformat(JavaCodeStyleManager.getInstance(project).shortenClassReferences(result));
          return;
        }
      }
      String text = generateSimpleCombinator(lambda, parameters[0], parameters[1]);
      if (text == null) return;
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      PsiExpression replacement = factory.createExpressionFromText(text, element);
      PsiMethodCallExpression result = (PsiMethodCallExpression)lambda.replace(replacement);
      normalizeLambda(ArrayUtil.getFirstElement(result.getArgumentList().getExpressions()), factory, keepParameterTypes);
      CodeStyleManager.getInstance(project).reformat(JavaCodeStyleManager.getInstance(project).shortenClassReferences(result));
    }

    private static void normalizeLambda(PsiExpression expression, PsiElementFactory factory, boolean keepParameterTypes) {
      if (!(expression instanceof PsiLambdaExpression lambda)) return;
      PsiParameter[] parameters = lambda.getParameterList().getParameters();
      PsiElement body = lambda.getBody();
      if (body == null) return;
      if (LambdaCanBeMethodReferenceInspection.replaceLambdaWithMethodReference(lambda) == lambda) {
        PsiParameter parameter = parameters[0];
        String name = suggestVarName(parameter);
        if (name != null) {
            Collection<PsiReferenceExpression> references = PsiTreeUtil.collectElementsOfType(body, PsiReferenceExpression.class);
            StreamEx.of(references).filter(ref -> ref.isReferenceTo(parameter)).map(PsiJavaCodeReferenceElement::getReferenceNameElement)
              .nonNull().forEach(nameElement -> nameElement.replace(factory.createIdentifier(name)));
            parameter.setName(name);
          }
        if (!keepParameterTypes) {
          RemoveRedundantParameterTypesFix.removeLambdaParameterTypesIfPossible(lambda);
        }
      }
    }
  }
}
