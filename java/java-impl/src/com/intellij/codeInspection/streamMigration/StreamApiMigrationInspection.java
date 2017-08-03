/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.intention.impl.StreamRefactoringUtil;
import com.intellij.codeInspection.BaseJavaBatchLocalInspectionTool;
import com.intellij.codeInspection.LambdaCanBeMethodReferenceInspection;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static com.intellij.codeInspection.streamMigration.OperationReductionMigration.SUM_OPERATION;
import static com.intellij.util.ObjectUtils.tryCast;
import static com.siyeh.ig.psiutils.ControlFlowUtils.InitializerUsageStatus.UNKNOWN;

public class StreamApiMigrationInspection extends BaseJavaBatchLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance(StreamApiMigrationInspection.class);

  public boolean REPLACE_TRIVIAL_FOREACH;
  public boolean SUGGEST_FOREACH;

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox("Warn if only 'forEach' replacement is available", "SUGGEST_FOREACH");
    panel.addCheckbox("Warn if the loop is trivial", "REPLACE_TRIVIAL_FOREACH");
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
    PsiFile file = holder.getFile();
    VirtualFile virtualFile = file.getVirtualFile();
    if (!PsiUtil.isLanguageLevel8OrHigher(file) || virtualFile == null ||
        !FileIndexFacade.getInstance(holder.getProject()).isInSourceContent(virtualFile)) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new StreamApiMigrationVisitor(holder, isOnTheFly);
  }

  @Nullable
  static PsiReturnStatement getNextReturnStatement(PsiStatement statement) {
    PsiElement nextStatement = PsiTreeUtil.skipWhitespacesAndCommentsForward(statement);
    if (nextStatement instanceof PsiReturnStatement) return (PsiReturnStatement)nextStatement;
    PsiElement parent = statement.getParent();
    if (parent instanceof PsiCodeBlock) {
      PsiStatement[] statements = ((PsiCodeBlock)parent).getStatements();
      if (statements.length == 0 || statements[statements.length - 1] != statement) return null;
      parent = parent.getParent();
      if (!(parent instanceof PsiBlockStatement)) return null;
      parent = parent.getParent();
    }
    if (parent instanceof PsiIfStatement) return getNextReturnStatement((PsiStatement)parent);
    return null;
  }

  @Contract("null, null -> true; null, !null -> false")
  private static boolean sameReference(PsiExpression expr1, PsiExpression expr2) {
    if (expr1 == null && expr2 == null) return true;
    if (!(expr1 instanceof PsiReferenceExpression) || !(expr2 instanceof PsiReferenceExpression)) return false;
    PsiReferenceExpression ref1 = (PsiReferenceExpression)expr1;
    PsiReferenceExpression ref2 = (PsiReferenceExpression)expr2;
    return Objects.equals(ref1.getReferenceName(), ref2.getReferenceName()) && sameReference(ref1.getQualifierExpression(),
                                                                                             ref2.getQualifierExpression());
  }

  /**
   * Extracts an addend from assignment expression like {@code x+=addend} or {@code x = x+addend}
   *
   * @param assignment assignment expression to extract an addend from
   * @return extracted addend expression or null if supplied assignment statement is not an addition
   */
  @Nullable
  static PsiExpression extractAddend(PsiAssignmentExpression assignment) {
    return extractOperand(assignment, JavaTokenType.PLUSEQ);
  }


  @Nullable
  static PsiExpression extractOperand(PsiAssignmentExpression assignment, IElementType compoundAssignmentOp) {
    if (compoundAssignmentOp.equals(assignment.getOperationTokenType())) {
      return assignment.getRExpression();
    }
    else if (JavaTokenType.EQ.equals(assignment.getOperationTokenType())) {
      if (assignment.getRExpression() instanceof PsiBinaryExpression) {
        PsiBinaryExpression binOp = (PsiBinaryExpression)assignment.getRExpression();
        IElementType op = TypeConversionUtil.convertEQtoOperation(compoundAssignmentOp);
        if (op.equals(binOp.getOperationTokenType())) {
          if (sameReference(binOp.getLOperand(), assignment.getLExpression())) {
            return binOp.getROperand();
          }
          if (sameReference(binOp.getROperand(), assignment.getLExpression())) {
            return binOp.getLOperand();
          }
        }
      }
    }
    return null;
  }


  @Nullable
  static PsiVariable extractSumAccumulator(PsiAssignmentExpression assignment) {
    return extractAccumulator(assignment, JavaTokenType.PLUSEQ);
  }


  @Nullable
  static PsiVariable extractAccumulator(PsiAssignmentExpression assignment, IElementType compoundAssignmentOp) {
    PsiReferenceExpression lExpr = tryCast(assignment.getLExpression(), PsiReferenceExpression.class);
    if (lExpr == null) return null;
    PsiVariable var = tryCast(lExpr.resolve(), PsiVariable.class);
    if (var == null) return null;
    if (compoundAssignmentOp.equals(assignment.getOperationTokenType())) {
      return var;
    }
    else if (JavaTokenType.EQ.equals(assignment.getOperationTokenType())) {
      if (assignment.getRExpression() instanceof PsiBinaryExpression) {
        PsiBinaryExpression binOp = (PsiBinaryExpression)assignment.getRExpression();
        IElementType op = TypeConversionUtil.convertEQtoOperation(compoundAssignmentOp);
        if (op.equals(binOp.getOperationTokenType())) {
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

  /**
   * Extract incremented value from expression which looks like {@code x++}, {@code ++x}, {@code x = x + 1} or {@code x += 1}
   *
   * @param expression expression to extract the incremented value
   * @return an extracted incremented value or null if increment pattern is not detected in the supplied expression
   */
  @Contract("null -> null")
  static PsiExpression extractIncrementedLValue(PsiExpression expression) {
    if (expression instanceof PsiPostfixExpression) {
      if (JavaTokenType.PLUSPLUS.equals(((PsiPostfixExpression)expression).getOperationTokenType())) {
        return ((PsiPostfixExpression)expression).getOperand();
      }
    }
    else if (expression instanceof PsiPrefixExpression) {
      if (JavaTokenType.PLUSPLUS.equals(((PsiPrefixExpression)expression).getOperationTokenType())) {
        return ((PsiPrefixExpression)expression).getOperand();
      }
    }
    else if (expression instanceof PsiAssignmentExpression) {
      PsiAssignmentExpression assignment = (PsiAssignmentExpression)expression;
      if (ExpressionUtils.isLiteral(extractAddend(assignment), 1)) {
        return assignment.getLExpression();
      }
    }
    return null;
  }

  @Nullable
  private static PsiLocalVariable getIncrementedVariable(PsiExpression expression, TerminalBlock tb, List<PsiVariable> variables) {
    // have only one non-final variable
    if (variables.size() != 1) return null;

    // have single expression which is either ++x or x++ or x+=1 or x=x+1
    PsiReferenceExpression operand = tryCast(extractIncrementedLValue(expression), PsiReferenceExpression.class);
    if (operand == null) return null;
    PsiLocalVariable variable = tryCast(operand.resolve(), PsiLocalVariable.class);

    // the referred variable is the same as non-final variable and not used in intermediate operations
    if (variable != null && variables.contains(variable) && !tb.isReferencedInOperations(variable)) {
      return variable;
    }
    return null;
  }

  @Nullable
  private static PsiVariable getAccumulatedVariable(TerminalBlock tb,
                                                    List<PsiVariable> variables,
                                                    OperationReductionMigration.ReductionOperation operation) {
    IElementType compoundAssignmentOp = operation.getCompoundAssignmentOp();
    // have only one non-final variable
    if (variables.size() != 1) return null;

    PsiAssignmentExpression assignment = tb.getSingleExpression(PsiAssignmentExpression.class);
    if (assignment == null) return null;
    PsiVariable var = extractAccumulator(assignment, compoundAssignmentOp);

    // the referred variable is the same as non-final variable
    if (var == null || !variables.contains(var)) return null;
    if (!operation.getAccumulatorRestriction().test(var)) return null;

    // the referred variable is not used in intermediate operations
    if (tb.isReferencedInOperations(var)) return null;
    PsiExpression operand = extractOperand(assignment, compoundAssignmentOp);
    LOG.assertTrue(operand != null);
    if (VariableAccessUtils.variableIsUsed(var, operand)) return null;
    return var;
  }

  static boolean isAddAllCall(TerminalBlock tb) {
    PsiMethodCallExpression call = tb.getSingleMethodCall();
    if (call == null || tb.getVariable().getType() instanceof PsiPrimitiveType) return false;
    if (!ExpressionUtils.isReferenceTo(call.getArgumentList().getExpressions()[0], tb.getVariable())) return false;
    if (!"add".equals(call.getMethodExpression().getReferenceName())) return false;
    PsiExpression qualifierExpression = call.getMethodExpression().getQualifierExpression();
    if (qualifierExpression == null || qualifierExpression instanceof PsiThisExpression) {
      PsiMethod method = PsiTreeUtil.getParentOfType(call, PsiMethod.class);
      return method == null || !method.getName().equals("addAll");
    }
    return !(qualifierExpression instanceof PsiMethodCallExpression);
  }

  @Contract("null, _, _ -> false")
  static boolean isCallOf(PsiMethodCallExpression call, String className, String... methodNames) {
    if (call == null) return false;
    PsiReferenceExpression methodExpression = call.getMethodExpression();
    String name = methodExpression.getReferenceName();
    if (!ArrayUtil.contains(name, methodNames)) return false;
    PsiMethod maybeMapMethod = call.resolveMethod();
    if (maybeMapMethod == null ||
        maybeMapMethod.getParameterList().getParametersCount() != call.getArgumentList().getExpressions().length) {
      return false;
    }
    PsiClass containingClass = maybeMapMethod.getContainingClass();
    if (containingClass == null) return false;
    if (className.equals(containingClass.getQualifiedName())) return true;
    PsiMethod[] superMethods = maybeMapMethod.findDeepestSuperMethods();
    return StreamEx.of(superMethods).map(PsiMember::getContainingClass).nonNull().map(PsiClass::getQualifiedName).has(className);
  }

  private static boolean isCountOperation(List<PsiVariable> nonFinalVariables, TerminalBlock tb) {
    PsiLocalVariable variable = getIncrementedVariable(tb.getSingleExpression(PsiExpression.class), tb, nonFinalVariables);
    PsiExpression counter = tb.getCountExpression();
    if (counter == null) {
      return variable != null;
    }
    if (tb.isEmpty()) {
      // like "if(++count == limit) break"
      variable = getIncrementedVariable(counter, tb, nonFinalVariables);
    }
    else if (!ExpressionUtils.isReferenceTo(counter, variable)) {
      return false;
    }
    return variable != null &&
           ExpressionUtils.isZero(variable.getInitializer()) &&
           ControlFlowUtils.getInitializerUsageStatus(variable, tb.getMainLoop()) != UNKNOWN;
  }

  private static boolean isTrivial(TerminalBlock tb) {
    PsiVariable variable = tb.getVariable();
    final PsiExpression candidate = LambdaCanBeMethodReferenceInspection
      .canBeMethodReferenceProblem(tb.getSingleStatement(),
                                   new PsiVariable[]{variable}, createDefaultConsumerType(variable.getProject(), variable), null);
    if (!(candidate instanceof PsiCallExpression)) return true;
    final PsiMethod method = ((PsiCallExpression)candidate).resolveMethod();
    return method == null;
  }

  @Nullable
  private static PsiClassType createDefaultConsumerType(Project project, PsiVariable variable) {
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiClass consumerClass = psiFacade.findClass(CommonClassNames.JAVA_UTIL_FUNCTION_CONSUMER, variable.getResolveScope());
    return consumerClass != null ? psiFacade.getElementFactory().createType(consumerClass, variable.getType()) : null;
  }

  static boolean isVariableSuitableForStream(PsiVariable variable, PsiStatement statement, TerminalBlock tb) {
    if (ReferencesSearch.search(variable).forEach(ref -> {
      PsiExpression expression = tryCast(ref.getElement(), PsiExpression.class);
      return expression == null ||
             !PsiUtil.isAccessedForWriting(expression) ||
             tb.operations().anyMatch(op -> op.isWriteAllowed(variable, expression));
    })) {
      return true;
    }
    return HighlightControlFlowUtil.isEffectivelyFinal(variable, statement, null);
  }

  static String tryUnbox(PsiVariable variable) {
    PsiType type = variable.getType();
    String mapOp = null;
    if (type.equals(PsiType.INT)) {
      mapOp = "mapToInt";
    }
    else if (type.equals(PsiType.LONG)) {
      mapOp = "mapToLong";
    }
    else if (type.equals(PsiType.DOUBLE)) {
      mapOp = "mapToDouble";
    }
    return mapOp == null ? "" : "." + mapOp + "(" + variable.getName() + " -> " + variable.getName() + ")";
  }

  static boolean isExpressionDependsOnUpdatedCollections(PsiExpression condition,
                                                         PsiExpression qualifierExpression) {
    final PsiElement collection = qualifierExpression instanceof PsiReferenceExpression
                                  ? ((PsiReferenceExpression)qualifierExpression).resolve()
                                  : null;
    if (collection != null) {
      return collection instanceof PsiVariable && VariableAccessUtils.variableIsUsed((PsiVariable)collection, condition);
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
      processLoop(statement);
    }

    @Override
    public void visitWhileStatement(PsiWhileStatement statement) {
      super.visitWhileStatement(statement);
      processLoop(statement);
    }

    @Override
    public void visitForStatement(PsiForStatement statement) {
      super.visitForStatement(statement);
      processLoop(statement);
    }

    void processLoop(PsiLoopStatement statement) {
      final PsiStatement body = statement.getBody();
      if (body == null) return;
      StreamSource source = StreamSource.tryCreate(statement);
      if (source == null) return;
      if (!ExceptionUtil.getThrownCheckedExceptions(body).isEmpty()) return;
      TerminalBlock tb = TerminalBlock.from(source, body);

      BaseStreamApiMigration migration = findMigration(statement, body, tb);
      if (migration != null && (myIsOnTheFly || migration.isShouldWarn())) {
        MigrateToStreamFix[] fixes = {new MigrateToStreamFix(migration)};
        if (migration instanceof ForEachMigration && !(tb.getLastOperation() instanceof CollectionStream)) { //for .stream()
          fixes = ArrayUtil.append(fixes, new MigrateToStreamFix(new ForEachMigration(migration.isShouldWarn(), "forEachOrdered")));
        }
        ProblemHighlightType highlightType =
          migration.isShouldWarn() ? ProblemHighlightType.GENERIC_ERROR_OR_WARNING : ProblemHighlightType.INFORMATION;
        myHolder.registerProblem(statement, "Can be replaced with '" + migration.getReplacement() + "' call",
                                 highlightType, getRange(migration.isShouldWarn(), statement).shiftRight(-statement.getTextOffset()),
                                 fixes);
      }
    }

    @Nullable
    private BaseStreamApiMigration findMigration(PsiLoopStatement loop, PsiStatement body, TerminalBlock tb) {
      final ControlFlow controlFlow;
      try {
        controlFlow = ControlFlowFactory.getInstance(myHolder.getProject())
          .getControlFlow(body, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance());
      }
      catch (AnalysisCanceledException ignored) {
        return null;
      }
      int startOffset = controlFlow.getStartOffset(body);
      int endOffset = controlFlow.getEndOffset(body);
      if (startOffset < 0 || endOffset < 0) return null;
      PsiElement surrounder = PsiTreeUtil.getParentOfType(loop, PsiLambdaExpression.class, PsiClass.class);
      final List<PsiVariable> nonFinalVariables = StreamEx.of(ControlFlowUtil.getUsedVariables(controlFlow, startOffset, endOffset))
        .remove(variable -> variable instanceof PsiField)
        .remove(variable -> PsiTreeUtil.getParentOfType(variable, PsiLambdaExpression.class, PsiClass.class) != surrounder)
        .remove(variable -> isVariableSuitableForStream(variable, loop, tb)).toList();

      if (isCountOperation(nonFinalVariables, tb)) {
        return new CountMigration(true);
      }
      if (nonFinalVariables.isEmpty()) {
        CollectMigration.CollectTerminal terminal = CollectMigration.extractCollectTerminal(tb);
        if (terminal != null) {
          boolean addAll = loop instanceof PsiForeachStatement && !tb.hasOperations() && isAddAllCall(tb);
          // Don't suggest to convert the loop which can be trivially replaced via addAll:
          // this is covered by UseBulkOperationInspection and ManualArrayToCollectionCopyInspection
          if (addAll) return null;
          boolean shouldWarn = REPLACE_TRIVIAL_FOREACH ||
                               tb.hasOperations() ||
                               tb.getLastOperation() instanceof BufferedReaderLines ||
                               !terminal.isTrivial();
          return new CollectMigration(shouldWarn, terminal.getMethodName());
        }
      }
      if (tb.getCountExpression() != null || tb.isEmpty()) return null;
      if (nonFinalVariables.isEmpty() && extractArray(tb) != null) {
        return new ToArrayMigration(true);
      }
      if (getAccumulatedVariable(tb, nonFinalVariables, SUM_OPERATION) != null) {
        return new SumMigration(true);
      }
      FindExtremumMigration.ExtremumTerminal extremumTerminal = FindExtremumMigration.extract(tb, nonFinalVariables);
      if(extremumTerminal != null) {
        return new FindExtremumMigration(true, FindExtremumMigration.getOperation(extremumTerminal.isMax()) + "()");
      }
      for (OperationReductionMigration.ReductionOperation reductionOperation : OperationReductionMigration.OPERATIONS) {
        if (getAccumulatedVariable(tb, nonFinalVariables, reductionOperation) != null) {
          return new OperationReductionMigration(true, reductionOperation);
        }
      }
      Collection<PsiStatement> exitPoints = tb.findExitPoints(controlFlow);
      if (exitPoints == null) return null;
      if (exitPoints.isEmpty() && nonFinalVariables.isEmpty()) {
        boolean shouldWarn = SUGGEST_FOREACH &&
                             (REPLACE_TRIVIAL_FOREACH ||
                              tb.hasOperations() ||
                              ForEachMigration.tryExtractMapExpression(tb) != null ||
                              !isTrivial(tb));
        return new ForEachMigration(shouldWarn, "forEach");
      }
      if (nonFinalVariables.isEmpty() && tb.getSingleStatement() instanceof PsiReturnStatement) {
        return findMigrationForReturn(loop, tb);
      }
      // Source and intermediate ops should not refer to non-final variables
      if (tb.intermediateAndSourceExpressions()
        .flatCollection(expr -> PsiTreeUtil.collectElementsOfType(expr, PsiReferenceExpression.class))
        .map(PsiReferenceExpression::resolve).select(PsiVariable.class).anyMatch(nonFinalVariables::contains)) {
        return null;
      }
      PsiStatement[] statements = tb.getStatements();
      if (statements.length == 2) {
        PsiStatement breakStatement = statements[1];
        if (ControlFlowUtils.statementBreaksLoop(breakStatement, loop) &&
            exitPoints.size() == 1 &&
            exitPoints.contains(breakStatement)) {
          return findMigrationForBreak(tb, nonFinalVariables, statements[0]);
        }
      }
      return null;
    }

    @Nullable
    private BaseStreamApiMigration findMigrationForBreak(TerminalBlock tb, List<PsiVariable> nonFinalVariables, PsiStatement statement) {
      boolean shouldWarn = REPLACE_TRIVIAL_FOREACH || tb.hasOperations();
      if (ReferencesSearch.search(tb.getVariable(), new LocalSearchScope(statement)).findFirst() == null) {
        return new MatchMigration(shouldWarn, "anyMatch");
      }
      if (nonFinalVariables.isEmpty() && statement instanceof PsiExpressionStatement) {
        return new FindFirstMigration(shouldWarn);
      }
      if (nonFinalVariables.size() == 1) {
        PsiAssignmentExpression assignment = ExpressionUtils.getAssignment(statement);
        if (assignment == null) return null;
        PsiReferenceExpression lValue = tryCast(assignment.getLExpression(), PsiReferenceExpression.class);
        if (lValue == null) return null;
        PsiVariable var = tryCast(lValue.resolve(), PsiVariable.class);
        if (var == null || !nonFinalVariables.contains(var)) return null;
        PsiExpression rValue = assignment.getRExpression();
        if (rValue == null || VariableAccessUtils.variableIsUsed(var, rValue)) return null;
        if (tb.getVariable().getType() instanceof PsiPrimitiveType && !ExpressionUtils.isReferenceTo(rValue, tb.getVariable())) return null;
        return new FindFirstMigration(shouldWarn);
      }
      return null;
    }

    @Nullable
    private BaseStreamApiMigration findMigrationForReturn(PsiLoopStatement statement, TerminalBlock tb) {
      boolean shouldWarn = REPLACE_TRIVIAL_FOREACH || tb.hasOperations();
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
          FilterOp lastFilter = tb.getLastOperation(FilterOp.class);
          if (lastFilter != null && (lastFilter.isNegated() ^ BoolUtils.isNegation(lastFilter.getExpression()))) {
            methodName = "allMatch";
          }
        }
        if (nextReturnStatement.getParent() == statement.getParent() ||
            ExpressionUtils.isLiteral(nextReturnStatement.getReturnValue(), !foundResult)) {
          return new MatchMigration(shouldWarn, methodName);
        }
      }
      if (!VariableAccessUtils.variableIsUsed(tb.getVariable(), value)) {
        if (!REPLACE_TRIVIAL_FOREACH && !tb.hasOperations() ||
            (tb.getLastOperation() instanceof FilterOp && tb.operations().count() == 2)) {
          return null;
        }
        return new MatchMigration(shouldWarn, "anyMatch");
      }
      if (nextReturnStatement != null && ExpressionUtils.isSimpleExpression(nextReturnStatement.getReturnValue())
          && (!(tb.getVariable().getType() instanceof PsiPrimitiveType) || ExpressionUtils.isReferenceTo(value, tb.getVariable()))) {
        return new FindFirstMigration(shouldWarn);
      }
      return null;
    }

    @NotNull
    private TextRange getRange(boolean shouldWarn, PsiLoopStatement statement) {
      boolean wholeStatement =
        myIsOnTheFly && (!shouldWarn || InspectionProjectProfileManager.isInformationLevel(getShortName(), statement));
      if (statement instanceof PsiForeachStatement) {
        PsiJavaToken rParenth = ((PsiForeachStatement)statement).getRParenth();
        if (wholeStatement && rParenth != null) {
          return new TextRange(statement.getTextOffset(), rParenth.getTextOffset() + 1);
        }
        PsiExpression iteratedValue = ((PsiForeachStatement)statement).getIteratedValue();
        LOG.assertTrue(iteratedValue != null);
        return iteratedValue.getTextRange();
      }
      else if (statement instanceof PsiForStatement) {
        PsiJavaToken rParenth = ((PsiForStatement)statement).getRParenth();
        if (wholeStatement && rParenth != null) {
          return new TextRange(statement.getTextOffset(), rParenth.getTextOffset() + 1);
        }
        PsiStatement initialization = ((PsiForStatement)statement).getInitialization();
        LOG.assertTrue(initialization != null);
        return initialization.getTextRange();
      }
      else if (statement instanceof PsiWhileStatement) {
        PsiJavaToken rParenth = ((PsiWhileStatement)statement).getRParenth();
        if (wholeStatement && rParenth != null) {
          return new TextRange(statement.getTextOffset(), rParenth.getTextOffset() + 1);
        }
        return statement.getFirstChild().getTextRange();
      }
      else {
        throw new IllegalStateException("Unexpected statement type: " + statement);
      }
    }
  }

  @Nullable
  static PsiLocalVariable extractArray(TerminalBlock tb) {
    CountingLoopSource loop = tb.getLastOperation(CountingLoopSource.class);
    if (loop == null || loop.myIncluding) return null;
    PsiAssignmentExpression assignment = tb.getSingleExpression(PsiAssignmentExpression.class);
    if (assignment == null || !assignment.getOperationTokenType().equals(JavaTokenType.EQ)) return null;
    PsiArrayAccessExpression arrayAccess = tryCast(assignment.getLExpression(), PsiArrayAccessExpression.class);
    if (arrayAccess == null) return null;
    if (!ExpressionUtils.isReferenceTo(arrayAccess.getIndexExpression(), loop.getVariable())) return null;
    PsiReferenceExpression arrayReference = tryCast(arrayAccess.getArrayExpression(), PsiReferenceExpression.class);
    if (arrayReference == null) return null;
    PsiLocalVariable arrayVariable = tryCast(arrayReference.resolve(), PsiLocalVariable.class);
    if (arrayVariable == null || ControlFlowUtils.getInitializerUsageStatus(arrayVariable, tb.getMainLoop()) == UNKNOWN) return null;
    PsiNewExpression initializer = tryCast(arrayVariable.getInitializer(), PsiNewExpression.class);
    if (initializer == null) return null;
    PsiArrayType arrayType = tryCast(initializer.getType(), PsiArrayType.class);
    if (arrayType == null || !StreamApiUtil.isSupportedStreamElement(arrayType.getComponentType())) return null;
    PsiExpression dimension = ArrayUtil.getFirstElement(initializer.getArrayDimensions());
    if (dimension == null) return null;
    PsiExpression bound = loop.myBound;
    if (!PsiEquivalenceUtil.areElementsEquivalent(dimension, bound) &&
        !ExpressionUtils.isReferenceTo(ExpressionUtils.getArrayFromLengthExpression(bound), arrayVariable)) {
      return null;
    }
    if (VariableAccessUtils.variableIsUsed(arrayVariable, assignment.getRExpression())) return null;
    return arrayVariable;
  }

  /**
   * Intermediate stream operation representation
   */
  static abstract class Operation {
    final PsiExpression myExpression;
    final PsiVariable myVariable;

    protected Operation(PsiExpression expression, PsiVariable variable) {
      myExpression = expression;
      myVariable = variable;
    }

    void cleanUp() {}

    public PsiVariable getVariable() {
      return myVariable;
    }

    PsiExpression getExpression() {
      return myExpression;
    }

    StreamEx<PsiExpression> expressions() {
      return StreamEx.ofNullable(myExpression);
    }

    abstract String createReplacement();

    boolean isWriteAllowed(PsiVariable variable, PsiExpression reference) {
      return false;
    }

    boolean canReassignVariable(PsiVariable variable) {
      return true;
    }
  }

  static class FilterOp extends Operation {
    private final boolean myNegated;

    FilterOp(PsiExpression condition, PsiVariable variable, boolean negated) {
      super(condition, variable);
      myNegated = negated;
    }

    public boolean isNegated() {
      return myNegated;
    }

    @Override
    public String createReplacement() {
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(myExpression.getProject());
      PsiExpression intermediate = makeIntermediateExpression(factory);
      PsiExpression expression =
        myNegated ? factory.createExpressionFromText(BoolUtils.getNegatedExpressionText(intermediate), myExpression) : intermediate;
      return "." + getOpName() + "(" + LambdaUtil.createLambda(myVariable, expression) + ")";
    }

    @NotNull
    String getOpName() {
      return "filter";
    }

    PsiExpression makeIntermediateExpression(PsiElementFactory factory) {
      return myExpression;
    }
  }

  static class TakeWhileOp extends FilterOp {
    TakeWhileOp(PsiExpression condition, PsiVariable variable, boolean negated) {
      super(condition, variable, negated);
    }

    @NotNull
    @Override
    String getOpName() {
      return "takeWhile";
    }
  }

  static class CompoundFilterOp extends FilterOp {
    private final FlatMapOp myFlatMapOp;
    private final PsiVariable myMatchVariable;

    CompoundFilterOp(FilterOp source, FlatMapOp flatMapOp) {
      super(source.getExpression(), flatMapOp.myVariable, source.myNegated);
      myMatchVariable = source.myVariable;
      myFlatMapOp = flatMapOp;
    }

    @Override
    PsiExpression makeIntermediateExpression(PsiElementFactory factory) {
      return factory.createExpressionFromText(myFlatMapOp.getStreamExpression() + ".anyMatch(" +
                                              LambdaUtil.createLambda(myMatchVariable, myExpression) + ")", myExpression);
    }

    @Override
    boolean isWriteAllowed(PsiVariable variable, PsiExpression reference) {
      return myFlatMapOp.isWriteAllowed(variable, reference);
    }

    @Override
    StreamEx<PsiExpression> expressions() {
      return StreamEx.of(myExpression, myFlatMapOp.myExpression);
    }
  }

  static class MapOp extends Operation {
    private final @Nullable PsiType myType;

    MapOp(PsiExpression expression, PsiVariable variable, @Nullable PsiType targetType) {
      super(expression, variable);
      myType = targetType;
    }

    @Override
    public String createReplacement() {
      return StreamRefactoringUtil.generateMapOperation(myVariable, myType, myExpression);
    }

    @Override
    boolean isWriteAllowed(PsiVariable variable, PsiExpression reference) {
      return variable == myVariable && reference.getParent() == myExpression.getParent();
    }
  }

  static class FlatMapOp extends Operation {
    private final StreamSource mySource;

    FlatMapOp(StreamSource source, PsiVariable variable) {
      super(source.getExpression(), variable);
      mySource = source;
    }

    @Override
    public String createReplacement() {
      String operation = "flatMap";
      PsiType inType = myVariable.getType();
      PsiType outType = mySource.getVariable().getType();
      String lambda = myVariable.getName() + " -> " + getStreamExpression();
      if (outType instanceof PsiPrimitiveType && !outType.equals(inType)) {
        if (outType.equals(PsiType.INT)) {
          operation = "flatMapToInt";
        }
        else if (outType.equals(PsiType.LONG)) {
          operation = "flatMapToLong";
        }
        else if (outType.equals(PsiType.DOUBLE)) {
          operation = "flatMapToDouble";
        }
      }
      if (inType instanceof PsiPrimitiveType && !outType.equals(inType)) {
        return ".mapToObj(" + lambda + ")." + operation + "(" + CommonClassNames.JAVA_UTIL_FUNCTION_FUNCTION + ".identity())";
      }
      return "." + operation + "(" + lambda + ")";
    }

    @NotNull
    String getStreamExpression() {
      return mySource.createReplacement();
    }

    @Override
    boolean isWriteAllowed(PsiVariable variable, PsiExpression reference) {
      return mySource.isWriteAllowed(variable, reference);
    }

    @Override
    boolean canReassignVariable(PsiVariable variable) {
      return mySource.canReassignVariable(variable);
    }

    boolean breaksMe(PsiBreakStatement statement) {
      return statement.findExitedStatement() == mySource.getLoop();
    }
  }

  static class LimitOp extends Operation {
    private final PsiExpression myCounter;
    private final PsiLocalVariable myCounterVariable;
    private final int myDelta;

    LimitOp(PsiVariable variable,
            PsiExpression countExpression,
            PsiExpression limitExpression,
            PsiLocalVariable counterVariable,
            int delta) {
      super(limitExpression, variable);
      LOG.assertTrue(delta >= 0);
      myDelta = delta;
      myCounter = countExpression;
      myCounterVariable = counterVariable;
    }

    @Override
    String createReplacement() {
      return ".limit(" + getLimitExpression() + ")";
    }

    PsiLocalVariable getCounterVariable() {
      return myCounterVariable;
    }

    PsiExpression getCountExpression() {
      return myCounter;
    }

    @Override
    void cleanUp() {
      if (myCounterVariable != null) {
        myCounterVariable.delete();
      }
    }

    @Override
    boolean isWriteAllowed(PsiVariable variable, PsiExpression reference) {
      return variable == myCounterVariable && PsiTreeUtil.isAncestor(myCounter, reference, false);
    }

    private String getLimitExpression() {
      if (myDelta == 0) {
        return myExpression.getText();
      }
      if (myExpression instanceof PsiLiteralExpression) {
        Object value = ((PsiLiteralExpression)myExpression).getValue();
        if (value instanceof Integer || value instanceof Long) {
          return String.valueOf(((Number)value).longValue() + myDelta);
        }
      }
      return ParenthesesUtils.getText(myExpression, ParenthesesUtils.ADDITIVE_PRECEDENCE) + "+" + myDelta;
    }
  }

  static class DistinctOp extends Operation {
    protected DistinctOp(PsiVariable variable) {
      super(null, variable);
    }

    @Override
    String createReplacement() {
      return ".distinct()";
    }
  }

  abstract static class StreamSource extends Operation {
    private final PsiLoopStatement myLoop;

    protected StreamSource(PsiLoopStatement loop, PsiVariable variable, PsiExpression expression) {
      super(expression, variable);
      myLoop = loop;
    }

    PsiLoopStatement getLoop() {
      return myLoop;
    }

    @Contract("null -> null")
    static StreamSource tryCreate(PsiLoopStatement statement) {
      if (statement instanceof PsiForStatement) {
        return CountingLoopSource.from((PsiForStatement)statement);
      }
      if (statement instanceof PsiForeachStatement) {
        ArrayStream source = ArrayStream.from((PsiForeachStatement)statement);
        return source == null ? CollectionStream.from((PsiForeachStatement)statement) : source;
      }
      if (statement instanceof PsiWhileStatement) {
        return BufferedReaderLines.from((PsiWhileStatement)statement);
      }
      return null;
    }
  }

  static class BufferedReaderLines extends StreamSource {
    private BufferedReaderLines(PsiLoopStatement loop, PsiVariable variable, PsiExpression expression) {
      super(loop, variable, expression);
    }

    @Override
    String createReplacement() {
      return myExpression.getText() + ".lines()";
    }

    @Override
    void cleanUp() {
      myVariable.delete();
    }

    @Override
    boolean isWriteAllowed(PsiVariable variable, PsiExpression reference) {
      return myVariable == variable && reference.getParent() == PsiTreeUtil.getParentOfType(myExpression, PsiAssignmentExpression.class);
    }

    @Nullable
    public static BufferedReaderLines from(PsiWhileStatement whileLoop) {
      // while ((line = br.readLine()) != null)
      PsiBinaryExpression binOp = tryCast(PsiUtil.skipParenthesizedExprDown(whileLoop.getCondition()), PsiBinaryExpression.class);
      if (binOp == null) return null;
      if (!JavaTokenType.NE.equals(binOp.getOperationTokenType())) return null;
      PsiExpression operand = ExpressionUtils.getValueComparedWithNull(binOp);
      if (operand == null) return null;
      PsiAssignmentExpression assignment = ExpressionUtils.getAssignment(PsiUtil.skipParenthesizedExprDown(operand));
      if (assignment == null) return null;
      PsiReferenceExpression lValue = tryCast(assignment.getLExpression(), PsiReferenceExpression.class);
      if (lValue == null) return null;
      PsiLocalVariable var = tryCast(lValue.resolve(), PsiLocalVariable.class);
      if (var == null) return null;
      if (!ReferencesSearch.search(var).forEach(ref -> {
        return PsiTreeUtil.isAncestor(whileLoop, ref.getElement(), true);
      })) {
        return null;
      }
      PsiMethodCallExpression call = tryCast(PsiUtil.skipParenthesizedExprDown(assignment.getRExpression()), PsiMethodCallExpression.class);
      if (call == null || call.getArgumentList().getExpressions().length != 0) return null;
      if (!"readLine".equals(call.getMethodExpression().getReferenceName())) return null;
      PsiExpression readerExpression = call.getMethodExpression().getQualifierExpression();
      if (readerExpression == null) return null;
      PsiMethod method = call.resolveMethod();
      if (method == null) return null;
      PsiClass aClass = method.getContainingClass();
      if (aClass == null || !"java.io.BufferedReader".equals(aClass.getQualifiedName())) return null;
      return new BufferedReaderLines(whileLoop, var, readerExpression);
    }
  }

  static class ArrayStream extends StreamSource {
    private ArrayStream(PsiLoopStatement loop, PsiVariable variable, PsiExpression expression) {
      super(loop, variable, expression);
    }

    @Override
    String createReplacement() {
      if (myExpression instanceof PsiNewExpression) {
        PsiArrayInitializerExpression initializer = ((PsiNewExpression)myExpression).getArrayInitializer();
        if (initializer != null) {
          PsiElement[] children = initializer.getChildren();
          if (children.length > 2) {
            String initializerText = StreamEx.of(children, 1, children.length - 1).map(PsiElement::getText).joining();
            PsiType type = myExpression.getType();
            if (type instanceof PsiArrayType) {
              PsiType componentType = ((PsiArrayType)type).getComponentType();
              if (componentType.equals(PsiType.INT)) {
                return CommonClassNames.JAVA_UTIL_STREAM_INT_STREAM + ".of(" + initializerText + ")";
              }
              else if (componentType.equals(PsiType.LONG)) {
                return CommonClassNames.JAVA_UTIL_STREAM_LONG_STREAM + ".of(" + initializerText + ")";
              }
              else if (componentType.equals(PsiType.DOUBLE)) {
                return CommonClassNames.JAVA_UTIL_STREAM_DOUBLE_STREAM + ".of(" + initializerText + ")";
              }
              else if (componentType instanceof PsiClassType) {
                return CommonClassNames.JAVA_UTIL_STREAM_STREAM + ".<" + componentType.getCanonicalText() + ">of(" + initializerText + ")";
              }
            }
          }
        }
      }
      return CommonClassNames.JAVA_UTIL_ARRAYS + ".stream(" + myExpression.getText() + ")";
    }

    @Nullable
    public static ArrayStream from(PsiForeachStatement statement) {
      PsiExpression iteratedValue = statement.getIteratedValue();
      if (iteratedValue == null) return null;

      PsiArrayType iteratedValueType = tryCast(iteratedValue.getType(), PsiArrayType.class);
      PsiParameter parameter = statement.getIterationParameter();

      if (iteratedValueType != null && StreamApiUtil.isSupportedStreamElement(iteratedValueType.getComponentType()) &&
          (!(parameter.getType() instanceof PsiPrimitiveType) || parameter.getType().equals(iteratedValueType.getComponentType()))) {
        return new ArrayStream(statement, parameter, iteratedValue);
      }
      return null;
    }
  }

  static class CollectionStream extends StreamSource {

    private CollectionStream(PsiLoopStatement loop, PsiVariable variable, PsiExpression expression) {
      super(loop, variable, expression);
    }

    @Override
    String createReplacement() {
      return ParenthesesUtils.getText(myExpression, ParenthesesUtils.POSTFIX_PRECEDENCE) + ".stream()" + tryUnbox(myVariable);
    }

    @Contract("null, _ -> false")
    static boolean isRawSubstitution(PsiType iteratedValueType, PsiClass collectionClass) {
      return iteratedValueType instanceof PsiClassType &&
             PsiUtil.isRawSubstitutor(collectionClass,
                                      TypeConversionUtil.getSuperClassSubstitutor(collectionClass, (PsiClassType)iteratedValueType));
    }

    @Nullable
    public static CollectionStream from(PsiForeachStatement statement) {
      PsiExpression iteratedValue = statement.getIteratedValue();
      if (iteratedValue == null) return null;

      PsiType iteratedValueType = iteratedValue.getType();
      PsiClass collectionClass =
        JavaPsiFacade.getInstance(statement.getProject()).findClass(CommonClassNames.JAVA_UTIL_COLLECTION, statement.getResolveScope());
      PsiClass iteratorClass = PsiUtil.resolveClassInClassTypeOnly(iteratedValueType);
      if (collectionClass == null ||
          !InheritanceUtil.isInheritorOrSelf(iteratorClass, collectionClass, true) ||
          isRawSubstitution(iteratedValueType, collectionClass) ||
          !StreamApiUtil.isSupportedStreamElement(statement.getIterationParameter().getType())) {
        return null;
      }
      return new CollectionStream(statement, statement.getIterationParameter(), iteratedValue);
    }
  }

  static class CountingLoopSource extends StreamSource {
    final PsiExpression myBound;
    final boolean myIncluding;

    private CountingLoopSource(PsiLoopStatement loop,
                               PsiVariable counter,
                               PsiExpression initializer,
                               PsiExpression bound,
                               boolean including) {
      super(loop, counter, initializer);
      myBound = bound;
      myIncluding = including;
    }

    @Override
    StreamEx<PsiExpression> expressions() {
      return StreamEx.of(myExpression, myBound);
    }

    @Override
    public String createReplacement() {
      String className = myVariable.getType().equals(PsiType.LONG) ? "java.util.stream.LongStream" : "java.util.stream.IntStream";
      String methodName = myIncluding ? "rangeClosed" : "range";
      return className + "." + methodName + "(" + myExpression.getText() + ", " + myBound.getText() + ")";
    }

    CountingLoopSource withBound(PsiExpression bound) {
      return new CountingLoopSource(getLoop(), getVariable(), getExpression(), bound, myIncluding);
    }

    @Override
    boolean isWriteAllowed(PsiVariable variable, PsiExpression reference) {
      if (variable == myVariable) {
        PsiForStatement forStatement = PsiTreeUtil.getParentOfType(variable, PsiForStatement.class);
        if (forStatement != null) {
          return PsiTreeUtil.isAncestor(forStatement.getUpdate(), reference, false);
        }
      }
      return false;
    }

    @Override
    boolean canReassignVariable(PsiVariable variable) {
      return variable != myVariable;
    }

    @Nullable
    public static CountingLoopSource from(PsiForStatement forStatement) {
      CountingLoop loop = CountingLoop.from(forStatement);
      if (loop == null) return null;
      return new CountingLoopSource(forStatement, loop.getCounter(), loop.getInitializer(), loop.getBound(), loop.isIncluding());
    }
  }
}
