/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.streamMigration;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.IntArrayList;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

import static com.intellij.codeInspection.streamMigration.StreamApiMigrationInspection.InitializerUsageStatus.*;

/**
 * User: anna
 */
public class StreamApiMigrationInspection extends BaseJavaBatchLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance("#" + StreamApiMigrationInspection.class.getName());

  static final Map<String, String> COLLECTION_TO_ARRAY = EntryStream.of(
    CommonClassNames.JAVA_UTIL_ARRAY_LIST, "toArray",
    "java.util.LinkedList", "toArray",
    CommonClassNames.JAVA_UTIL_HASH_SET, "distinct().toArray",
    "java.util.LinkedHashSet", "distinct().toArray",
    "java.util.TreeSet", "distinct().sorted().toArray"
  ).toMap();

  public boolean REPLACE_TRIVIAL_FOREACH;
  public boolean SUGGEST_FOREACH;

  private HighlightDisplayKey myKey;

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox("Suggest to replace with forEach or forEachOrdered", "SUGGEST_FOREACH");
    panel.addCheckbox("Replace trivial foreach statements", "REPLACE_TRIVIAL_FOREACH");
    return panel;
  }

  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return GroupNames.LANGUAGE_LEVEL_SPECIFIC_GROUP_NAME;
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "foreach loop can be collapsed with Stream API";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  public String getShortName() {
    return "Convert2streamapi";
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel8OrHigher(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new StreamApiMigrationVisitor(holder, isOnTheFly);
  }

  @Contract("_, null -> false")
  static boolean isVariableReferenced(PsiVariable variable, PsiExpression value) {
    return value != null && ReferencesSearch.search(variable, new LocalSearchScope(value)).findFirst() != null;
  }

  static boolean isReferencedInOperations(PsiElement element, TerminalBlock tb) {
    return ReferencesSearch.search(element, new LocalSearchScope(tb.intermediateExpressions().toArray(PsiElement[]::new)))
             .findFirst() != null;
  }

  @Nullable
  static PsiReturnStatement getNextReturnStatement(PsiStatement statement) {
    PsiElement nextStatement = PsiTreeUtil.skipSiblingsForward(statement, PsiWhiteSpace.class, PsiComment.class);
    if(nextStatement instanceof PsiReturnStatement) return (PsiReturnStatement)nextStatement;
    PsiElement parent = statement.getParent();
    if(parent instanceof PsiCodeBlock) {
      PsiStatement[] statements = ((PsiCodeBlock)parent).getStatements();
      if(statements.length == 0 || statements[statements.length-1] != statement) return null;
      parent = parent.getParent();
      if(!(parent instanceof PsiBlockStatement)) return null;
      parent = parent.getParent();
    }
    if(parent instanceof PsiIfStatement) return getNextReturnStatement((PsiStatement)parent);
    return null;
  }

  @Contract("null, null -> true; null, !null -> false")
  private static boolean sameReference(PsiExpression expr1, PsiExpression expr2) {
    if(expr1 == null && expr2 == null) return true;
    if (!(expr1 instanceof PsiReferenceExpression) || !(expr2 instanceof PsiReferenceExpression)) return false;
    PsiReferenceExpression ref1 = (PsiReferenceExpression)expr1;
    PsiReferenceExpression ref2 = (PsiReferenceExpression)expr2;
    return Objects.equals(ref1.getReferenceName(), ref2.getReferenceName()) && sameReference(ref1.getQualifierExpression(),
                                                                                             ref2.getQualifierExpression());
  }

  @Nullable
  static PsiExpression extractAddend(PsiAssignmentExpression assignment) {
      if(JavaTokenType.PLUSEQ.equals(assignment.getOperationTokenType())) {
        return assignment.getRExpression();
      } else if(JavaTokenType.EQ.equals(assignment.getOperationTokenType())) {
        if (assignment.getRExpression() instanceof PsiBinaryExpression) {
          PsiBinaryExpression binOp = (PsiBinaryExpression)assignment.getRExpression();
          if(JavaTokenType.PLUS.equals(binOp.getOperationTokenType())) {
            if(sameReference(binOp.getLOperand(), assignment.getLExpression())) {
              return binOp.getROperand();
            }
            if(sameReference(binOp.getROperand(), assignment.getLExpression())) {
              return binOp.getLOperand();
            }
          }
        }
      }
      return null;
  }

  @Nullable
  static PsiVariable extractAccumulator(PsiAssignmentExpression assignment) {
    if(!(assignment.getLExpression() instanceof PsiReferenceExpression)) return null;
    PsiReferenceExpression lExpr = (PsiReferenceExpression)assignment.getLExpression();
    PsiElement accumulator = lExpr.resolve();
    if(!(accumulator instanceof PsiVariable)) return null;
    PsiVariable var = (PsiVariable)accumulator;
    if(JavaTokenType.PLUSEQ.equals(assignment.getOperationTokenType())) {
      return var;
    } else if(JavaTokenType.EQ.equals(assignment.getOperationTokenType())) {
      if (assignment.getRExpression() instanceof PsiBinaryExpression) {
        PsiBinaryExpression binOp = (PsiBinaryExpression)assignment.getRExpression();
        if(JavaTokenType.PLUS.equals(binOp.getOperationTokenType())) {
          PsiExpression left = binOp.getLOperand();
          PsiExpression right = binOp.getROperand();
          if (sameReference(left, lExpr) || sameReference(right, lExpr)) {
            return var;
          }
        }
      }
    }
    return null;
  }

  @Contract("null -> null")
  static PsiExpression extractIncrementedLValue(PsiExpression expression) {
    if(expression instanceof PsiPostfixExpression) {
      if(JavaTokenType.PLUSPLUS.equals(((PsiPostfixExpression)expression).getOperationTokenType())) {
        return ((PsiPostfixExpression)expression).getOperand();
      }
    } else if(expression instanceof PsiPrefixExpression) {
      if(JavaTokenType.PLUSPLUS.equals(((PsiPrefixExpression)expression).getOperationTokenType())) {
        return ((PsiPrefixExpression)expression).getOperand();
      }
    } else if(expression instanceof PsiAssignmentExpression) {
      PsiAssignmentExpression assignment = (PsiAssignmentExpression)expression;
      if(ExpressionUtils.isLiteral(extractAddend(assignment), 1)) {
        return assignment.getLExpression();
      }
    }
    return null;
  }

  @Nullable
  private static PsiLocalVariable getIncrementedVariable(TerminalBlock tb, List<PsiVariable> variables) {
    // have only one non-final variable
    if(variables.size() != 1) return null;

    // have single expression which is either ++x or x++ or x+=1 or x=x+1
    PsiExpression operand = extractIncrementedLValue(tb.getSingleExpression(PsiExpression.class));
    if(!(operand instanceof PsiReferenceExpression)) return null;
    PsiElement element = ((PsiReferenceExpression)operand).resolve();

    // the referred variable is the same as non-final variable and not used in intermediate operations
    if(!(element instanceof PsiLocalVariable) || !variables.contains(element) || isReferencedInOperations(element, tb)) return null;

    return (PsiLocalVariable)element;
  }

  @Nullable
  private static PsiVariable getAccumulatedVariable(TerminalBlock tb, List<PsiVariable> variables) {
    // have only one non-final variable
    if(variables.size() != 1) return null;

    PsiAssignmentExpression assignment = tb.getSingleExpression(PsiAssignmentExpression.class);
    if(assignment == null) return null;
    PsiVariable var = extractAccumulator(assignment);

    // the referred variable is the same as non-final variable
    if(var == null || !variables.contains(var)) return null;
    if (!(var.getType() instanceof PsiPrimitiveType) || var.getType().equalsToText("float")) return null;

    // the referred variable is not used in intermediate operations
    if(isReferencedInOperations(var, tb)) return null;
    PsiExpression addend = extractAddend(assignment);
    LOG.assertTrue(addend != null);
    if(ReferencesSearch.search(var, new LocalSearchScope(addend)).findFirst() != null) return null;
    return var;
  }

  static boolean isAddAllCall(TerminalBlock tb) {
    final PsiVariable variable = tb.getVariable();
    final PsiMethodCallExpression methodCallExpression = tb.getSingleMethodCall();
    LOG.assertTrue(methodCallExpression != null);
    return isIdentityMapping(variable, methodCallExpression.getArgumentList().getExpressions()[0]);
  }

  private static boolean isCollectCall(TerminalBlock tb) {
    final PsiMethodCallExpression methodCallExpression = tb.getSingleMethodCall();
    if (methodCallExpression != null) {
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
      PsiClass qualifierClass = null;
      if (qualifierExpression instanceof PsiReferenceExpression) {
        if (ReferencesSearch.search(tb.getVariable(), new LocalSearchScope(qualifierExpression)).findFirst() != null) {
          return false;
        }
        final PsiElement resolve = ((PsiReferenceExpression)qualifierExpression).resolve();
        if (resolve instanceof PsiVariable) {
          if (ReferencesSearch.search(resolve, new LocalSearchScope(methodCallExpression.getArgumentList())).findFirst() != null) {
            return false;
          }
        }
        qualifierClass = PsiUtil.resolveClassInType(qualifierExpression.getType());
      }
      else if (qualifierExpression == null) {
        final PsiClass enclosingClass = PsiTreeUtil.getParentOfType(methodCallExpression, PsiClass.class);
        if (PsiUtil.getEnclosingStaticElement(methodCallExpression, enclosingClass) == null) {
          qualifierClass = enclosingClass;
        }
      }

      if (qualifierClass != null && 
          InheritanceUtil.isInheritor(qualifierClass, false, CommonClassNames.JAVA_UTIL_COLLECTION)) {

        if (tb.intermediateExpressions().anyMatch(expression -> isExpressionDependsOnUpdatedCollections(expression, qualifierExpression))) {
          return false;
        }

        final PsiElement resolve = methodExpression.resolve();
        if (resolve instanceof PsiMethod &&
            "add".equals(((PsiMethod)resolve).getName()) &&
            ((PsiMethod)resolve).getParameterList().getParametersCount() == 1) {
          final PsiExpression[] args = methodCallExpression.getArgumentList().getExpressions();
          if (args.length == 1) {
            if (args[0] instanceof PsiCallExpression) {
              final PsiMethod method = ((PsiCallExpression)args[0]).resolveMethod();
              return method != null && !method.hasTypeParameters() && !isThrowsCompatible(method);
            }
            return true;
          }
        }
      }
    }
    return false;
  }

  private static boolean isExpressionDependsOnUpdatedCollections(PsiExpression condition,
                                                                 PsiExpression qualifierExpression) {
    final PsiElement collection = qualifierExpression instanceof PsiReferenceExpression
                                  ? ((PsiReferenceExpression)qualifierExpression).resolve()
                                  : null;
    if (collection != null) {
      return ReferencesSearch.search(collection, new LocalSearchScope(condition)).findFirst() != null;
    }

    final boolean[] dependsOnCollection = {false};
    condition.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        super.visitMethodCallExpression(expression);
        final PsiExpression callQualifier = expression.getMethodExpression().getQualifierExpression();
        if (callQualifier == null ||
            callQualifier instanceof PsiThisExpression && ((PsiThisExpression)callQualifier).getQualifier() == null ||
            callQualifier instanceof PsiSuperExpression && ((PsiSuperExpression)callQualifier).getQualifier() == null) {
          dependsOnCollection[0] = true;
        }
      }

      @Override
      public void visitThisExpression(PsiThisExpression expression) {
        super.visitThisExpression(expression);
        if (expression.getQualifier() == null && expression.getParent() instanceof PsiExpressionList) {
          dependsOnCollection[0] = true;
        }
      }

      @Override
      public void visitClass(PsiClass aClass) {}

      @Override
      public void visitLambdaExpression(PsiLambdaExpression expression) {}
    });

    return dependsOnCollection[0];
  }

  private static boolean isTrivial(PsiStatement body, PsiParameter parameter) {
    //method reference
    final PsiExpression candidate = new LambdaCanBeMethodReferenceInspection()
      .canBeMethodReferenceProblem(body instanceof PsiBlockStatement ? ((PsiBlockStatement)body).getCodeBlock() : body,
                                   new PsiParameter[]{parameter}, createDefaultConsumerType(parameter.getProject(), parameter), null);
    if (!(candidate instanceof PsiCallExpression)) {
      return true;
    }
    final PsiMethod method = ((PsiCallExpression)candidate).resolveMethod();
    return method != null && isThrowsCompatible(method);
  }

  static boolean isSupported(PsiType type) {
    if(type instanceof PsiPrimitiveType) {
      return type.equals(PsiType.INT) || type.equals(PsiType.LONG) || type.equals(PsiType.DOUBLE);
    }
    return true;
  }

  private static boolean isThrowsCompatible(PsiMethod method) {
    return ContainerUtil.find(method.getThrowsList().getReferencedTypes(), type -> !ExceptionUtil.isUncheckedException(type)) != null;
  }

  @Contract("_, null -> false")
  static boolean isIdentityMapping(PsiVariable variable, PsiExpression mapperCall) {
    return mapperCall instanceof PsiReferenceExpression && ((PsiReferenceExpression)mapperCall).resolve() == variable;
  }

  @Nullable
  private static PsiClassType createDefaultConsumerType(Project project, PsiVariable variable) {
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiClass consumerClass = psiFacade.findClass("java.util.function.Consumer", GlobalSearchScope.allScope(project));
    return consumerClass != null ? psiFacade.getElementFactory().createType(consumerClass, variable.getType()) : null;
  }

  @Contract("null -> null")
  static PsiLocalVariable extractCollectionVariable(PsiExpression qualifierExpression) {
    if (qualifierExpression instanceof PsiReferenceExpression) {
      final PsiElement resolve = ((PsiReferenceExpression)qualifierExpression).resolve();
      if (resolve instanceof PsiLocalVariable) {
        PsiLocalVariable var = (PsiLocalVariable)resolve;
        final PsiExpression initializer = var.getInitializer();
        if (initializer instanceof PsiNewExpression) {
          final PsiExpressionList argumentList = ((PsiNewExpression)initializer).getArgumentList();
          if (argumentList != null && argumentList.getExpressions().length == 0) {
            return var;
          }
        }
      }
    }
    return null;
  }

  enum InitializerUsageStatus {
    // Variable is declared just before the wanted place
    DECLARED_JUST_BEFORE,
    // All initial value usages go through wanted place and at wanted place the variable value is guaranteed to be the initial value
    AT_WANTED_PLACE_ONLY,
    // At wanted place the variable value is guaranteed to be the initial value, but this initial value might be used somewhere else
    AT_WANTED_PLACE,
    // It's not guaranteed that the variable value at wanted place is initial value
    UNKNOWN
  }

  static InitializerUsageStatus getInitializerUsageStatus(PsiVariable var, PsiStatement nextStatement) {
    if(!(var instanceof PsiLocalVariable) || var.getInitializer() == null) return UNKNOWN;
    if(isDeclarationJustBefore(var, nextStatement)) return DECLARED_JUST_BEFORE;
    // Check that variable is declared in the same method or the same lambda expression
    if(PsiTreeUtil.getParentOfType(var, PsiLambdaExpression.class, PsiMethod.class) !=
       PsiTreeUtil.getParentOfType(nextStatement, PsiLambdaExpression.class, PsiMethod.class)) return UNKNOWN;
    PsiElement block = PsiUtil.getVariableCodeBlock(var, null);
    if(block == null) return UNKNOWN;
    final ControlFlow controlFlow;
    try {
      controlFlow = ControlFlowFactory.getInstance(nextStatement.getProject())
        .getControlFlow(block, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance());
    }
    catch (AnalysisCanceledException ignored) {
      return UNKNOWN;
    }
    int start = controlFlow.getEndOffset(var.getInitializer())+1;
    int stop = controlFlow.getStartOffset(nextStatement);
    if(ControlFlowUtil.isVariableReferencedBetween(controlFlow, start, stop, var)) return UNKNOWN;
    if (!ControlFlowUtil.isValueUsedWithoutVisitingStop(controlFlow, start, stop, var)) return AT_WANTED_PLACE_ONLY;
    return var.hasModifierProperty(PsiModifier.FINAL) ? UNKNOWN : AT_WANTED_PLACE;
  }

  static boolean isDeclarationJustBefore(PsiVariable var, PsiStatement nextStatement) {
    PsiElement declaration = var.getParent();
    PsiElement nextStatementParent = nextStatement.getParent();
    if(nextStatementParent instanceof PsiLabeledStatement) {
      nextStatement = (PsiStatement)nextStatementParent;
    }
    if(declaration instanceof PsiDeclarationStatement) {
      PsiElement[] elements = ((PsiDeclarationStatement)declaration).getDeclaredElements();
      if (ArrayUtil.getLastElement(elements) == var && nextStatement.equals(
        PsiTreeUtil.skipSiblingsForward(declaration, PsiWhiteSpace.class, PsiComment.class))) {
        return true;
      }
    }
    return false;
  }

  private class StreamApiMigrationVisitor extends JavaElementVisitor {
    private final ProblemsHolder myHolder;
    private final boolean myIsOnTheFly;

    public StreamApiMigrationVisitor(ProblemsHolder holder, boolean isOnTheFly) {
      myHolder = holder;
      myIsOnTheFly = isOnTheFly;
    }

    @Override
    public void visitForeachStatement(PsiForeachStatement statement) {
      super.visitForeachStatement(statement);
      final PsiExpression iteratedValue = statement.getIteratedValue();
      final PsiStatement body = statement.getBody();
      if (iteratedValue == null || body == null) return;

      final PsiType iteratedValueType = iteratedValue.getType();
      final PsiClass iteratorClass = PsiUtil.resolveClassInClassTypeOnly(iteratedValueType);
      PsiClass collectionClass = null;
      final boolean isArray;
      if(iteratedValueType instanceof PsiArrayType) {
        if(!isSupported(((PsiArrayType)iteratedValueType).getComponentType())) return;
        isArray = true;
      } else {
        collectionClass = JavaPsiFacade.getInstance(body.getProject()).findClass(CommonClassNames.JAVA_UTIL_COLLECTION, statement.getResolveScope());
        if (collectionClass != null && InheritanceUtil.isInheritorOrSelf(iteratorClass, collectionClass, true)) {
          isArray = false;
        } else return;
      }
      if (!ExceptionUtil.getThrownCheckedExceptions(body).isEmpty()) return;

      TerminalBlock tb = TerminalBlock.from(statement.getIterationParameter(), body);
      if(tb.isEmpty()) return;

      final ControlFlow controlFlow;
      try {
        controlFlow = ControlFlowFactory.getInstance(myHolder.getProject())
          .getControlFlow(body, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance());
      }
      catch (AnalysisCanceledException ignored) {
        return;
      }
      final Collection<PsiStatement> exitPoints = ControlFlowUtil
        .findExitPointsAndStatements(controlFlow, tb.getStartOffset(controlFlow), tb.getEndOffset(controlFlow),
                                     new IntArrayList(), PsiContinueStatement.class,
                                     PsiBreakStatement.class, PsiReturnStatement.class, PsiThrowStatement.class);
      int startOffset = controlFlow.getStartOffset(body);
      int endOffset = controlFlow.getEndOffset(body);
      final List<PsiVariable> nonFinalVariables = StreamEx.of(ControlFlowUtil.getUsedVariables(controlFlow, startOffset, endOffset))
        .remove(variable -> HighlightControlFlowUtil.isEffectivelyFinal(variable, body, null)).toList();

      if (exitPoints.isEmpty()) {
        if(getIncrementedVariable(tb, nonFinalVariables) != null) {
          registerProblem(statement, "count", new ReplaceWithCountFix());
        }
        if(getAccumulatedVariable(tb, nonFinalVariables) != null) {
          registerProblem(statement, "sum", new ReplaceWithSumFix());
        }
        if(!nonFinalVariables.isEmpty()) {
          return;
        }
        if ((isArray || !isRawSubstitution(iteratedValueType, collectionClass)) && isCollectCall(tb)) {
          boolean addAll = !tb.hasOperations() && isAddAllCall(tb);
          String methodName;
          if(addAll) {
            methodName = "addAll";
          } else {
            PsiMethodCallExpression methodCallExpression = tb.getSingleMethodCall();
            if(canCollect(statement, methodCallExpression)) {
              if(extractToArrayExpression(statement, methodCallExpression) != null)
                methodName = "toArray";
              else
                methodName = "collect";
            } else {
              if (!SUGGEST_FOREACH) return;
              methodName = "forEach";
            }
          }
          registerProblem(statement, methodName, new ReplaceWithCollectFix(methodName));
        }
        // do not replace for(T e : arr) {} with Arrays.stream(arr).forEach(e -> {}) even if flag is set
        else if (SUGGEST_FOREACH &&
                 (tb.hasOperations() || (!isArray && (REPLACE_TRIVIAL_FOREACH || !isTrivial(body, statement.getIterationParameter()))))) {
          ReplaceWithForeachCallFix forEachFix = new ReplaceWithForeachCallFix("forEach");
          LocalQuickFix[] fixes = {forEachFix};
          if (tb.hasOperations()) { //for .stream()
            fixes = new LocalQuickFix[] {forEachFix, new ReplaceWithForeachCallFix("forEachOrdered")};
          }
          registerProblem(statement, "forEach", fixes);
        }
      } else {
        if (!tb.hasOperations() && !REPLACE_TRIVIAL_FOREACH) return;
        if (nonFinalVariables.isEmpty() && tb.getSingleStatement() instanceof PsiReturnStatement) {
          handleSingleReturn(statement, tb);
        }
        // Intermediate ops should not refer to non-final variables
        if (tb.intermediateExpressions()
          .flatCollection(expr -> PsiTreeUtil.collectElementsOfType(expr, PsiReferenceExpression.class))
          .map(PsiReferenceExpression::resolve).anyMatch(nonFinalVariables::contains)) {
          return;
        }
        PsiStatement[] statements = tb.getStatements();
        if (statements.length == 2) {
          PsiStatement breakStatement = statements[1];
          if (!ControlFlowUtils.statementBreaksLoop(breakStatement, statement)) {
            return;
          }
          if (ReferencesSearch.search(tb.getVariable(), new LocalSearchScope(statements)).findFirst() == null
            && exitPoints.size() == 1 && exitPoints.contains(breakStatement)) {
            registerProblem(statement, "anyMatch", new ReplaceWithMatchFix("anyMatch"));
            return;
          }
          if (nonFinalVariables.isEmpty() && statements[0] instanceof PsiExpressionStatement) {
            registerProblem(statement, "findFirst", new ReplaceWithFindFirstFix());
          } else if (nonFinalVariables.size() == 1) {
            PsiAssignmentExpression assignment = ExpressionUtils.getAssignment(statements[0]);
            if(assignment == null) return;
            PsiExpression lValue = assignment.getLExpression();
            if (!(lValue instanceof PsiReferenceExpression)) return;
            PsiElement var = ((PsiReferenceExpression)lValue).resolve();
            if(!(var instanceof PsiVariable) || !nonFinalVariables.contains(var)) return;
            PsiExpression rValue = assignment.getRExpression();
            if(rValue == null || isVariableReferenced((PsiVariable)var, rValue)) return;
            if(tb.getVariable().getType() instanceof PsiPrimitiveType && !isIdentityMapping(tb.getVariable(), rValue)) return;
            registerProblem(statement, "findFirst", new ReplaceWithFindFirstFix());
          }
        }
      }
    }

    boolean canCollect(PsiForeachStatement statement, PsiMethodCallExpression methodCallExpression) {
      if(methodCallExpression == null) return false;
      PsiLocalVariable variable = extractCollectionVariable(methodCallExpression.getMethodExpression().getQualifierExpression());
      if(variable == null) return false;
      return getInitializerUsageStatus(variable, statement) != UNKNOWN;
    }

    void handleSingleReturn(PsiForeachStatement statement, TerminalBlock tb) {
      PsiReturnStatement returnStatement = (PsiReturnStatement)tb.getSingleStatement();
      PsiExpression value = returnStatement.getReturnValue();
      PsiReturnStatement nextReturnStatement = getNextReturnStatement(statement);
      if (nextReturnStatement != null &&
          (ExpressionUtils.isLiteral(value, Boolean.TRUE) || ExpressionUtils.isLiteral(value, Boolean.FALSE))) {
        boolean foundResult = (boolean)((PsiLiteralExpression)value).getValue();
        String methodName;
        if (foundResult) {
          methodName = "anyMatch";
        }
        else {
          methodName = "noneMatch";
          Operation lastOp = tb.getLastOperation();
          if(lastOp instanceof FilterOp && (((FilterOp)lastOp).isNegated() ^ BoolUtils.isNegation(lastOp.getExpression()))) {
            methodName = "allMatch";
          }
        }
        if(nextReturnStatement.getParent() == statement.getParent() ||
           ExpressionUtils.isLiteral(nextReturnStatement.getReturnValue(), !foundResult)) {
          registerProblem(statement, methodName, new ReplaceWithMatchFix(methodName));
          return;
        }
      }
      if (!isVariableReferenced(tb.getVariable(), value)) {
        Operation lastOp = tb.getLastOperation();
        if(!REPLACE_TRIVIAL_FOREACH && lastOp == null || (lastOp instanceof FilterOp && lastOp.getPreviousOp() == null)) return;
        registerProblem(statement, "anyMatch", new ReplaceWithMatchFix("anyMatch"));
      }
      if(nextReturnStatement != null && ExpressionUtils.isSimpleExpression(nextReturnStatement.getReturnValue())
         && (!(tb.getVariable().getType() instanceof PsiPrimitiveType) || isIdentityMapping(tb.getVariable(), value))) {
        registerProblem(statement, "findFirst", new ReplaceWithFindFirstFix());
      }
    }

    private boolean isRawSubstitution(PsiType iteratedValueType, PsiClass collectionClass) {
      return iteratedValueType instanceof PsiClassType && PsiUtil
        .isRawSubstitutor(collectionClass, TypeConversionUtil.getSuperClassSubstitutor(collectionClass, (PsiClassType)iteratedValueType));
    }

    @NotNull
    private TextRange getRange(PsiForeachStatement statement) {
      boolean wholeStatement = false;
      if(myIsOnTheFly) {
        if (myKey == null) {
          myKey = HighlightDisplayKey.find(getShortName());
        }
        if (myKey != null) {
          InspectionProfile profile = InspectionProjectProfileManager.getInstance(statement.getProject()).getCurrentProfile();
          HighlightDisplayLevel level = profile.getErrorLevel(myKey, statement);
          wholeStatement = HighlightDisplayLevel.DO_NOT_SHOW.equals(level);
        }
      }
      PsiExpression iteratedValue = statement.getIteratedValue();
      LOG.assertTrue(iteratedValue != null);
      PsiJavaToken rParenth = statement.getRParenth();
      if(wholeStatement && rParenth != null) {
        return new TextRange(statement.getTextOffset(), rParenth.getTextOffset() + 1);
      }
      return iteratedValue.getTextRange();
    }

    private void registerProblem(PsiForeachStatement statement, String methodName, LocalQuickFix... fixes) {
      PsiExpression iteratedValue = statement.getIteratedValue();
      LOG.assertTrue(iteratedValue != null);
      myHolder.registerProblem(statement, getRange(statement).shiftRight(-statement.getTextOffset()),
                               "Can be replaced with '" + methodName + "' call", fixes);
    }
  }

  /**
   *
   * @param element sort statement candidate (must be PsiExpressionStatement)
   * @param list list which should be sorted
   * @return comparator string representation, empty string if natural order is used or null if given statement is not sort statement
   */
  @Contract(value = "null, _ -> null")
  static String tryExtractSortComparatorText(PsiElement element, PsiVariable list) {
    if(!(element instanceof PsiExpressionStatement)) return null;
    PsiExpression expression = ((PsiExpressionStatement)element).getExpression();
    if(!(expression instanceof PsiMethodCallExpression)) return null;
    PsiMethodCallExpression methodCall = (PsiMethodCallExpression)expression;
    PsiReferenceExpression methodExpression = methodCall.getMethodExpression();
    if(!"sort".equals(methodExpression.getReferenceName())) return null;
    PsiMethod method = methodCall.resolveMethod();
    if(method == null) return null;
    PsiClass containingClass = method.getContainingClass();
    if(containingClass == null) return null;
    PsiExpression listExpression = null;
    PsiExpression comparatorExpression = null;
    if(CommonClassNames.JAVA_UTIL_COLLECTIONS.equals(containingClass.getQualifiedName())) {
      PsiExpression[] args = methodCall.getArgumentList().getExpressions();
      if(args.length == 1) {
        listExpression = args[0];
      } else if(args.length == 2) {
        listExpression = args[0];
        comparatorExpression = args[1];
      } else return null;
    } else if(InheritanceUtil.isInheritor(containingClass, CommonClassNames.JAVA_UTIL_LIST)) {
      listExpression = methodExpression.getQualifierExpression();
      PsiExpression[] args = methodCall.getArgumentList().getExpressions();
      if(args.length != 1) return null;
      comparatorExpression = args[0];
    }
    if(!(listExpression instanceof PsiReferenceExpression) || ((PsiReferenceExpression)listExpression).resolve() != list) return null;
    if(comparatorExpression == null || ExpressionUtils.isNullLiteral(comparatorExpression)) return "";
    return comparatorExpression.getText();
  }

  @Nullable
  static PsiMethodCallExpression extractToArrayExpression(PsiForeachStatement statement, PsiMethodCallExpression expression) {
    // return collection.toArray() or collection.toArray(new Type[0]) or collection.toArray(new Type[collection.size()]);
    PsiElement nextElement = PsiTreeUtil.skipSiblingsForward(statement, PsiComment.class, PsiWhiteSpace.class);
    PsiExpression toArrayCandidate;
    if (nextElement instanceof PsiReturnStatement) {
      toArrayCandidate = ((PsiReturnStatement)nextElement).getReturnValue();
    }
    else {
      PsiAssignmentExpression assignment = ExpressionUtils.getAssignment(nextElement);
      if (assignment != null) {
        toArrayCandidate = assignment.getRExpression();
      }
      else if (nextElement instanceof PsiDeclarationStatement) {
        PsiElement[] elements = ((PsiDeclarationStatement)nextElement).getDeclaredElements();
        if (elements.length == 1 && elements[0] instanceof PsiLocalVariable) {
          toArrayCandidate = ((PsiLocalVariable)elements[0]).getInitializer();
        }
        else {
          return null;
        }
      }
      else {
        return null;
      }
    }
    if (!(toArrayCandidate instanceof PsiMethodCallExpression)) return null;
    PsiMethodCallExpression call = (PsiMethodCallExpression)toArrayCandidate;
    PsiReferenceExpression methodExpression = call.getMethodExpression();
    if (!"toArray".equals(methodExpression.getReferenceName())) return null;
    PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
    if (!(qualifierExpression instanceof PsiReferenceExpression)) return null;
    PsiLocalVariable collectionVariable = extractCollectionVariable(expression.getMethodExpression().getQualifierExpression());
    if (collectionVariable == null || ((PsiReferenceExpression)qualifierExpression).resolve() != collectionVariable) return null;
    PsiExpression initializer = collectionVariable.getInitializer();
    if (initializer == null) return null;
    PsiType type = initializer.getType();
    if (!(type instanceof PsiClassType) || !COLLECTION_TO_ARRAY.containsKey(((PsiClassType)type).rawType().getCanonicalText())) {
      return null;
    }

    if (!(nextElement instanceof PsiReturnStatement) && !ReferencesSearch.search(collectionVariable, collectionVariable.getUseScope())
      .forEach(ref ->
                 ref.getElement() == collectionVariable || PsiTreeUtil.isAncestor(statement, ref.getElement(), false) ||
                 PsiTreeUtil.isAncestor(toArrayCandidate, ref.getElement(), false)
      )) {
      return null;
    }

    PsiExpression[] args = call.getArgumentList().getExpressions();
    if (args.length == 0) return call;
    if (args.length != 1 || !(args[0] instanceof PsiNewExpression)) return null;
    PsiNewExpression newArray = (PsiNewExpression)args[0];
    PsiExpression[] dimensions = newArray.getArrayDimensions();
    if (dimensions.length != 1) return null;
    if (ExpressionUtils.isLiteral(dimensions[0], 0)) return call;
    if (!(dimensions[0] instanceof PsiMethodCallExpression)) return null;
    PsiMethodCallExpression maybeSizeCall = (PsiMethodCallExpression)dimensions[0];
    if (maybeSizeCall.getArgumentList().getExpressions().length != 0) return null;
    PsiReferenceExpression maybeSizeExpression = maybeSizeCall.getMethodExpression();
    PsiExpression sizeQualifier = maybeSizeExpression.getQualifierExpression();
    if (sizeQualifier != null &&
        !("size".equals(maybeSizeExpression.getReferenceName()) &&
          PsiEquivalenceUtil.areElementsEquivalent(qualifierExpression, sizeQualifier))) {
      return null;
    }
    return call;
  }

  /**
   * Intermediate stream operation representation
   */
  static abstract class Operation {
    final PsiExpression myExpression;
    final PsiVariable myVariable;
    final @Nullable Operation myPreviousOp;

    protected Operation(@Nullable Operation previousOp, PsiExpression expression, PsiVariable variable) {
      myExpression = expression;
      myVariable = variable;
      myPreviousOp = previousOp;
    }

    public PsiVariable getVariable() {
      return myVariable;
    }

    @Nullable
    public Operation getPreviousOp() {
      return myPreviousOp;
    }

    PsiExpression getExpression() {
      return myExpression;
    }

    StreamEx<PsiExpression> expressions() {
      return StreamEx.of(myExpression);
    }

    abstract String createReplacement(PsiElementFactory factory);
  }

  static class FilterOp extends Operation {
    private final boolean myNegated;

    FilterOp(@Nullable Operation previousOp, PsiExpression condition, PsiVariable variable, boolean negated) {
      super(previousOp, condition, variable);
      myNegated = negated;
    }

    public boolean isNegated() {
      return myNegated;
    }

    @Override
    public String createReplacement(PsiElementFactory factory) {
      PsiExpression intermediate = makeIntermediateExpression(factory);
      PsiExpression expression =
        myNegated ? factory.createExpressionFromText(BoolUtils.getNegatedExpressionText(intermediate), myExpression) : intermediate;
      return ".filter(" + LambdaUtil.createLambda(myVariable, expression) + ")";
    }

    PsiExpression makeIntermediateExpression(PsiElementFactory factory) {
      return myExpression;
    }
  }

  static class CompoundFilterOp extends FilterOp {
    private final FlatMapOp myFlatMapOp;
    private final PsiVariable myMatchVariable;

    CompoundFilterOp(FilterOp source, FlatMapOp flatMapOp) {
      super(flatMapOp.myPreviousOp, source.getExpression(), flatMapOp.myVariable, source.myNegated);
      myMatchVariable = source.myVariable;
      myFlatMapOp = flatMapOp;
    }

    @Override
    PsiExpression makeIntermediateExpression(PsiElementFactory factory) {
      return factory.createExpressionFromText(myFlatMapOp.getStreamExpression()+".anyMatch("+
        LambdaUtil.createLambda(myMatchVariable, myExpression)+")", myExpression);
    }

    @Override
    StreamEx<PsiExpression> expressions() {
      return StreamEx.of(myExpression, myFlatMapOp.myExpression);
    }
  }

  static class MapOp extends Operation {
    private final @Nullable PsiType myType;

    MapOp(@Nullable Operation previousOp, PsiExpression expression, PsiVariable variable, @Nullable PsiType targetType) {
      super(previousOp, expression, variable);
      myType = targetType;
    }

    @Override
    public String createReplacement(PsiElementFactory factory) {
      if (isIdentityMapping(myVariable, myExpression)) {
        if (!(myType instanceof PsiPrimitiveType)) {
          return myVariable.getType() instanceof PsiPrimitiveType ? ".boxed()" : "";
        }
        if(myType.equals(myVariable.getType())) {
          return "";
        }
        if (PsiType.LONG.equals(myType) && PsiType.INT.equals(myVariable.getType())) {
          return ".asLongStream()";
        }
        if (PsiType.DOUBLE.equals(myType) && (PsiType.LONG.equals(myVariable.getType()) || PsiType.INT.equals(myVariable.getType()))) {
          return ".asDoubleStream()";
        }
      }
      String operationName = "map";
      if(myType instanceof PsiPrimitiveType) {
        if(!myType.equals(myVariable.getType())) {
          if(PsiType.INT.equals(myType)) {
            operationName = "mapToInt";
          } else if(PsiType.LONG.equals(myType)) {
            operationName = "mapToLong";
          } else if(PsiType.DOUBLE.equals(myType)) {
            operationName = "mapToDouble";
          }
        }
      } else if(myVariable.getType() instanceof PsiPrimitiveType) {
        operationName = "mapToObj";
      }
      PsiExpression expression = myType == null ? myExpression : RefactoringUtil.convertInitializerToNormalExpression(myExpression, myType);
      return "." + operationName + "(" + LambdaUtil.createLambda(myVariable, expression) + ")";
    }
  }

  static class FlatMapOp extends Operation {
    private final PsiLoopStatement myLoop;

    FlatMapOp(@Nullable Operation previousOp, PsiExpression expression, PsiVariable variable, PsiLoopStatement loop) {
      super(previousOp, expression, variable);
      myLoop = loop;
    }

    @Override
    public String createReplacement(PsiElementFactory factory) {
      return ".flatMap(" + myVariable.getName() + " -> " + getStreamExpression() + ")";
    }

    @NotNull
    String getStreamExpression() {
      return myExpression.getText() + ".stream()";
    }

    boolean breaksMe(PsiBreakStatement statement) {
      return statement.findExitedStatement() == myLoop;
    }
  }

  static class ArrayFlatMapOp extends FlatMapOp {
    ArrayFlatMapOp(@Nullable Operation previousOp, PsiExpression expression, PsiVariable variable, PsiLoopStatement loop) {
      super(previousOp, expression, variable, loop);
    }

    @Override
    public String createReplacement(PsiElementFactory factory) {
      String operation = "flatMap";
      PsiType type = myExpression.getType();
      if(type instanceof PsiArrayType) {
        PsiType componentType = ((PsiArrayType)type).getComponentType();
        if(componentType instanceof PsiPrimitiveType) {
          if(componentType.equals(PsiType.INT)) {
            operation = "flatMapToInt";
          } else if(componentType.equals(PsiType.LONG)) {
            operation = "flatMapToLong";
          } else if(componentType.equals(PsiType.DOUBLE)) {
            operation = "flatMapToDouble";
          }
        }
      }
      return "." + operation + "(" + myVariable.getName() + " -> " + getStreamExpression() + ")";
    }

    @Override
    @NotNull
    String getStreamExpression() {
      return "java.util.Arrays.stream("+myExpression.getText() + ")";
    }
  }

  /**
   * This immutable class represents the code which should be performed
   * as a part of forEach operation of resulting stream possibly with
   * some intermediate operations extracted.
   */
  static class TerminalBlock {
    private final @Nullable Operation myPreviousOp;
    private final @NotNull PsiVariable myVariable;
    private final @NotNull PsiStatement[] myStatements;

    private TerminalBlock(@Nullable Operation previousOp, @NotNull PsiVariable variable, @NotNull PsiStatement... statements) {
      myVariable = variable;
      while(true) {
        if(statements.length == 1 && statements[0] instanceof PsiBlockStatement) {
          statements = ((PsiBlockStatement)statements[0]).getCodeBlock().getStatements();
        } else if(statements.length == 1 && statements[0] instanceof PsiLabeledStatement) {
          statements = new PsiStatement[] {((PsiLabeledStatement)statements[0]).getStatement()};
        } else break;
      }
      myStatements = statements;
      myPreviousOp = previousOp;
    }

    int getStartOffset(ControlFlow cf) {
      return cf.getStartOffset(myStatements[0]);
    }

    int getEndOffset(ControlFlow cf) {
      return cf.getEndOffset(myStatements[myStatements.length-1]);
    }

    PsiStatement getSingleStatement() {
      return myStatements.length == 1 ? myStatements[0] : null;
    }

    @NotNull
    PsiStatement[] getStatements() {
      return myStatements;
    }

    @Nullable
    <T extends PsiExpression> T getSingleExpression(Class<T> wantedType) {
      PsiStatement statement = getSingleStatement();
      if(statement instanceof PsiExpressionStatement) {
        PsiExpression expression = ((PsiExpressionStatement)statement).getExpression();
        if(wantedType.isInstance(expression))
          return wantedType.cast(expression);
      }
      return null;
    }

    /**
     * @return PsiMethodCallExpression if this TerminalBlock contains single method call, null otherwise
     */
    @Nullable
    PsiMethodCallExpression getSingleMethodCall() {
      return getSingleExpression(PsiMethodCallExpression.class);
    }

    @Nullable
    private TerminalBlock extractFilter() {
      if(getSingleStatement() instanceof PsiIfStatement) {
        PsiIfStatement ifStatement = (PsiIfStatement)getSingleStatement();
        if(ifStatement.getElseBranch() == null && ifStatement.getCondition() != null) {
          return new TerminalBlock(new FilterOp(myPreviousOp, ifStatement.getCondition(), myVariable, false),
                                   myVariable, ifStatement.getThenBranch());
        }
      }
      if(myStatements.length >= 1) {
        PsiStatement first = myStatements[0];
        // extract filter with negation
        if(first instanceof PsiIfStatement) {
          PsiIfStatement ifStatement = (PsiIfStatement)first;
          if(ifStatement.getCondition() == null) return null;
          PsiStatement branch = ifStatement.getThenBranch();
          if(branch instanceof PsiBlockStatement) {
            PsiStatement[] statements = ((PsiBlockStatement)branch).getCodeBlock().getStatements();
            if(statements.length == 1)
              branch = statements[0];
          }
          if(!(branch instanceof PsiContinueStatement) || ((PsiContinueStatement)branch).getLabelIdentifier() != null) return null;
          PsiStatement[] statements;
          if(ifStatement.getElseBranch() != null) {
            statements = myStatements.clone();
            statements[0] = ifStatement.getElseBranch();
          } else {
            statements = Arrays.copyOfRange(myStatements, 1, myStatements.length);
          }
          return new TerminalBlock(new FilterOp(myPreviousOp, ifStatement.getCondition(), myVariable, true),
                                   myVariable, statements);
        }
      }
      return null;
    }

    /**
     * Returns an equivalent {@code TerminalBlock} with one more intermediate operation extracted
     * or null if extraction is not possible.
     *
     * @return extracted operation or null if extraction is not possible
     */
    @Nullable
    TerminalBlock extractOperation() {
      TerminalBlock withFilter = extractFilter();
      if(withFilter != null) return withFilter;
      // extract flatMap
      if(getSingleStatement() instanceof PsiForeachStatement) {
        // flatMapping of primitive variable is not supported yet
        if(myVariable.getType() instanceof PsiPrimitiveType) return null;
        PsiForeachStatement foreachStatement = (PsiForeachStatement)getSingleStatement();
        final PsiExpression iteratedValue = foreachStatement.getIteratedValue();
        final PsiStatement body = foreachStatement.getBody();
        if (iteratedValue != null && body != null) {
          final PsiType iteratedValueType = iteratedValue.getType();
          FlatMapOp op = null;
          if(iteratedValueType instanceof PsiArrayType) {
            if (!isSupported(((PsiArrayType)iteratedValueType).getComponentType())) return null;
            op = new ArrayFlatMapOp(myPreviousOp, iteratedValue, myVariable, foreachStatement);
          } else {
            final PsiClass iteratorClass = PsiUtil.resolveClassInClassTypeOnly(iteratedValueType);
            final PsiClass collectionClass =
              JavaPsiFacade.getInstance(body.getProject())
                .findClass(CommonClassNames.JAVA_UTIL_COLLECTION, foreachStatement.getResolveScope());
            if (collectionClass != null && InheritanceUtil.isInheritorOrSelf(iteratorClass, collectionClass, true)) {
              op = new FlatMapOp(myPreviousOp, iteratedValue, myVariable, foreachStatement);
            }
          }
          if(op != null) {
            TerminalBlock withFlatMap = new TerminalBlock(op, foreachStatement.getIterationParameter(), body);
            if(ReferencesSearch.search(myVariable, new LocalSearchScope(body)).findFirst() == null) {
              return withFlatMap;
            } else {
              // Try extract nested filter like this:
              // for(List subList : list) for(T t : subList) if(condition.test(t)) { ...; break; }
              // if t is not used in "...", then this could be converted to
              // list.stream().filter(subList -> subList.stream().anyMatch(condition)).forEach(subList -> ...)
              TerminalBlock withFlatMapFilter = withFlatMap.extractFilter();
              if(withFlatMapFilter != null && !withFlatMapFilter.isEmpty()) {
                PsiStatement[] statements = withFlatMapFilter.getStatements();
                PsiStatement lastStatement = statements[statements.length-1];
                if (lastStatement instanceof PsiBreakStatement && op.breaksMe((PsiBreakStatement)lastStatement) &&
                    ReferencesSearch.search(withFlatMapFilter.getVariable(), new LocalSearchScope(statements)).findFirst() == null) {
                  return new TerminalBlock(new CompoundFilterOp((FilterOp)withFlatMapFilter.getLastOperation(), op),
                                           myVariable, Arrays.copyOfRange(statements, 0, statements.length-1));
                }
              }
            }
          }
        }
      }
      if(myStatements.length >= 1) {
        PsiStatement first = myStatements[0];
        // extract map
        if(first instanceof PsiDeclarationStatement) {
          PsiDeclarationStatement decl = (PsiDeclarationStatement)first;
          PsiElement[] elements = decl.getDeclaredElements();
          if(elements.length == 1) {
            PsiElement element = elements[0];
            if(element instanceof PsiLocalVariable) {
              PsiLocalVariable declaredVar = (PsiLocalVariable)element;
              if(isSupported(declaredVar.getType())) {
                PsiExpression initializer = declaredVar.getInitializer();
                PsiStatement[] leftOver = Arrays.copyOfRange(myStatements, 1, myStatements.length);
                if (initializer != null && ReferencesSearch.search(myVariable, new LocalSearchScope(leftOver)).findFirst() == null) {
                  MapOp op = new MapOp(myPreviousOp, initializer, myVariable, declaredVar.getType());
                  return new TerminalBlock(op, declaredVar, leftOver);
                }
              }
            }
          }
        }
      }
      return null;
    }

    @Nullable
    public Operation getLastOperation() {
      return myPreviousOp;
    }

    /**
     * Extract all possible intermediate operations
     * @return the terminal block with all possible terminal operations extracted (may return this if no operations could be extracted)
     */
    @NotNull
    TerminalBlock extractOperations() {
      return StreamEx.iterate(this, Objects::nonNull, TerminalBlock::extractOperation).reduce((a, b) -> b).orElse(this);
    }

    @NotNull
    public PsiVariable getVariable() {
      return myVariable;
    }

    public boolean hasOperations() {
      return myPreviousOp != null;
    }

    public boolean isEmpty() {
      return myStatements.length == 0;
    }

    @NotNull
    private StreamEx<Operation> operations() {
      return StreamEx.iterate(myPreviousOp, Objects::nonNull, Operation::getPreviousOp);
    }

    public Collection<Operation> getOperations() {
      ArrayDeque<Operation> ops = new ArrayDeque<>();
      operations().forEach(ops::addFirst);
      return ops;
    }

    /**
     * @return stream of physical expressions used in intermediate operations in arbitrary order
     */
    public StreamEx<PsiExpression> intermediateExpressions() {
      return operations().flatMap(Operation::expressions);
    }

    /**
     * Converts this TerminalBlock to PsiElement (either PsiStatement or PsiCodeBlock)
     *
     * @param factory factory to use to create new element if necessary
     * @return the PsiElement
     */
    public PsiElement convertToElement(PsiElementFactory factory) {
      if (myStatements.length == 1) {
        return myStatements[0];
      }
      PsiCodeBlock block = factory.createCodeBlock();
      for (PsiStatement statement : myStatements) {
        block.add(statement);
      }
      return block;
    }

    @NotNull
    public static TerminalBlock from(PsiVariable variable, PsiStatement statement) {
      return new TerminalBlock(null, variable, statement).extractOperations();
    }
  }
}
