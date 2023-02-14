// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.streamMigration;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.intention.impl.StreamRefactoringUtil;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.JavaFeature;
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
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;
import static com.intellij.codeInspection.streamMigration.OperationReductionMigration.SUM_OPERATION;
import static com.intellij.util.ObjectUtils.tryCast;
import static com.siyeh.ig.psiutils.ControlFlowUtils.InitializerUsageStatus.UNKNOWN;

public class StreamApiMigrationInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance(StreamApiMigrationInspection.class);

  @SuppressWarnings("PublicField")
  public boolean REPLACE_TRIVIAL_FOREACH;
  @SuppressWarnings("PublicField")
  public boolean SUGGEST_FOREACH;
  private static final String SHORT_NAME = "Convert2streamapi";

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("SUGGEST_FOREACH", JavaBundle.message("checkbox.warn.if.only.foreach.replacement.is.available")),
      checkbox("REPLACE_TRIVIAL_FOREACH", JavaBundle.message("checkbox.warn.if.the.loop.is.trivial"))
    );
  }

  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.language.level.specific.issues.and.migration.aids");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  public String getShortName() {
    return SHORT_NAME;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    PsiFile file = holder.getFile();
    VirtualFile virtualFile = file.getVirtualFile();
    if (!JavaFeature.STREAMS.isFeatureSupported(file) || virtualFile == null ||
        !FileIndexFacade.getInstance(holder.getProject()).isInSourceContent(virtualFile)) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new StreamApiMigrationVisitor(holder, isOnTheFly);
  }

  @Contract("null, null -> true; null, !null -> false")
  private static boolean sameReference(PsiExpression expr1, PsiExpression expr2) {
    if (expr1 == null && expr2 == null) return true;
    if (!(expr1 instanceof PsiReferenceExpression ref1) || !(expr2 instanceof PsiReferenceExpression ref2)) return false;
    return Objects.equals(ref1.getReferenceName(), ref2.getReferenceName()) && 
           sameReference(ref1.getQualifierExpression(), ref2.getQualifierExpression());
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
      if (assignment.getRExpression() instanceof PsiBinaryExpression binOp) {
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
      if (assignment.getRExpression() instanceof PsiBinaryExpression binOp) {
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
    if (expression instanceof PsiUnaryExpression) {
      if (JavaTokenType.PLUSPLUS.equals(((PsiUnaryExpression)expression).getOperationTokenType())) {
        return ((PsiUnaryExpression)expression).getOperand();
      }
    }
    else if (expression instanceof PsiAssignmentExpression assignment && ExpressionUtils.isLiteral(extractAddend(assignment), 1)) {
      return assignment.getLExpression();
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
        maybeMapMethod.getParameterList().getParametersCount() != call.getArgumentList().getExpressionCount()) {
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
           ControlFlowUtils.getInitializerUsageStatus(variable, tb.getStreamSourceStatement()) != UNKNOWN;
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
    PsiElement block = PsiUtil.getVariableCodeBlock(variable, statement);
    if (block != null) {
      Predicate<PsiElement> notAllowedWrite = e -> e instanceof PsiReferenceExpression ref && 
                                                   PsiUtil.isAccessedForWriting(ref) &&
                                                   ref.isReferenceTo(variable) &&
                                                   tb.operations().noneMatch(op -> op.isWriteAllowed(variable, ref));
      if (PsiTreeUtil.processElements(block, notAllowedWrite.negate()::test)) return true;
    }
    return HighlightControlFlowUtil.isEffectivelyFinal(variable, statement, null);
  }

  static String tryUnbox(PsiVariable variable) {
    PsiType type = variable.getType();
    String mapOp = null;
    if (type.equals(PsiTypes.intType())) {
      mapOp = "mapToInt";
    }
    else if (type.equals(PsiTypes.longType())) {
      mapOp = "mapToLong";
    }
    else if (type.equals(PsiTypes.doubleType())) {
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
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
        super.visitMethodCallExpression(expression);
        final PsiExpression callQualifier = expression.getMethodExpression().getQualifierExpression();
        if (callQualifier == null ||
            callQualifier instanceof PsiThisExpression && ((PsiThisExpression)callQualifier).getQualifier() == null ||
            callQualifier instanceof PsiSuperExpression && ((PsiSuperExpression)callQualifier).getQualifier() == null) {
          dependsOnCollection[0] = true;
        }
      }

      @Override
      public void visitThisExpression(@NotNull PsiThisExpression expression) {
        super.visitThisExpression(expression);
        if (expression.getQualifier() == null && expression.getParent() instanceof PsiExpressionList) {
          dependsOnCollection[0] = true;
        }
      }

      @Override
      public void visitClass(@NotNull PsiClass aClass) {}

      @Override
      public void visitLambdaExpression(@NotNull PsiLambdaExpression expression) {}
    });

    return dependsOnCollection[0];
  }

  private class StreamApiMigrationVisitor extends JavaElementVisitor {
    private final ProblemsHolder myHolder;
    private final boolean myIsOnTheFly;

    StreamApiMigrationVisitor(ProblemsHolder holder, boolean isOnTheFly) {
      myHolder = holder;
      myIsOnTheFly = isOnTheFly;
    }

    @Override
    public void visitForeachStatement(@NotNull PsiForeachStatement statement) {
      super.visitForeachStatement(statement);
      processLoop(statement);
    }

    @Override
    public void visitWhileStatement(@NotNull PsiWhileStatement statement) {
      super.visitWhileStatement(statement);
      processLoop(statement);
    }

    @Override
    public void visitForStatement(@NotNull PsiForStatement statement) {
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

      BaseStreamApiMigration migration = findMigration(statement, body, tb, SUGGEST_FOREACH, REPLACE_TRIVIAL_FOREACH);
      if (migration == null || (!myIsOnTheFly && !migration.isShouldWarn())) return;
      MigrateToStreamFix[] fixes = {new MigrateToStreamFix(migration)};
      if (migration instanceof ForEachMigration && !(tb.getLastOperation() instanceof CollectionStream)) { //for .stream()
        fixes = ArrayUtil.append(fixes, new MigrateToStreamFix(new ForEachMigration(migration.isShouldWarn(), "forEachOrdered")));
      }
      ProblemHighlightType highlightType =
        migration.isShouldWarn() ? ProblemHighlightType.GENERIC_ERROR_OR_WARNING : ProblemHighlightType.INFORMATION;
      String message = JavaBundle.message("inspection.stream.api.migration.can.be.replaced.with.call", migration.getReplacement());
      TextRange range = getRange(migration.isShouldWarn(), statement, myIsOnTheFly);
      myHolder.registerProblem(statement, message, highlightType, range.shiftRight(-statement.getTextOffset()), fixes);
    }
  }


  @Nullable
  static BaseStreamApiMigration findMigration(PsiStatement loop,
                                              PsiElement body,
                                              TerminalBlock tb,
                                              boolean suggestForeach,
                                              boolean replaceTrivialForEach) {
    final ControlFlow controlFlow;
    try {
      controlFlow = ControlFlowFactory.getInstance(loop.getProject())
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
    CollectMigration.CollectTerminal terminal = CollectMigration.extractCollectTerminal(tb, nonFinalVariables);
    if (terminal != null) {
      boolean addAll = loop instanceof PsiForeachStatement && !tb.hasOperations() && isAddAllCall(tb);
      // Don't suggest to convert the loop which can be trivially replaced via addAll:
      // this is covered by UseBulkOperationInspection and ManualArrayToCollectionCopyInspection
      if (addAll) return null;
      boolean shouldWarn = replaceTrivialForEach ||
                           tb.hasOperations() ||
                           tb.getLastOperation() instanceof BufferedReaderLines ||
                           !terminal.isTrivial();
      return new CollectMigration(shouldWarn, terminal.getMethodName());
    }
    if (JoiningMigration.extractTerminal(tb, nonFinalVariables) != null) {
      return new JoiningMigration(true);
    }
    if (tb.getCountExpression() != null || tb.isEmpty()) return null;
    if (nonFinalVariables.isEmpty() && extractArray(tb) != null) {
      return new ToArrayMigration(true);
    }
    if (getAccumulatedVariable(tb, nonFinalVariables, SUM_OPERATION) != null) {
      return new SumMigration(true);
    }
    FindExtremumMigration.ExtremumTerminal extremumTerminal = FindExtremumMigration.extract(tb, nonFinalVariables);
    if (extremumTerminal != null) {
      return new FindExtremumMigration(true, FindExtremumMigration.getOperation(extremumTerminal.isMax()));
    }
    for (OperationReductionMigration.ReductionOperation reductionOperation : OperationReductionMigration.OPERATIONS) {
      if (getAccumulatedVariable(tb, nonFinalVariables, reductionOperation) != null) {
        return new OperationReductionMigration(true, reductionOperation);
      }
    }
    Collection<PsiStatement> exitPoints = tb.findExitPoints(controlFlow);
    if (exitPoints == null) return null;
    boolean onlyNonLabeledContinue = ContainerUtil.and(
      exitPoints, statement -> statement instanceof PsiContinueStatement && ((PsiContinueStatement)statement).getLabelIdentifier() == null);
    if (onlyNonLabeledContinue && nonFinalVariables.isEmpty()) {
      boolean shouldWarn = suggestForeach &&
                           (replaceTrivialForEach ||
                            tb.hasOperations() ||
                            ForEachMigration.tryExtractMapExpression(tb) != null ||
                            !isTrivial(tb));
      return new ForEachMigration(shouldWarn, "forEach");
    }
    if (nonFinalVariables.isEmpty() && tb.getSingleStatement() instanceof PsiReturnStatement) {
      return findMigrationForReturn(loop, tb, replaceTrivialForEach);
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
      if (loop instanceof PsiLoopStatement && ControlFlowUtils.statementBreaksLoop(breakStatement, (PsiLoopStatement)loop) &&
          exitPoints.size() == 1 &&
          exitPoints.contains(breakStatement)) {
        return findMigrationForBreak(tb, nonFinalVariables, statements[0], replaceTrivialForEach);
      }
    }
    return null;
  }

  @Nullable
  private static BaseStreamApiMigration findMigrationForBreak(TerminalBlock tb,
                                                              List<PsiVariable> nonFinalVariables,
                                                              PsiStatement statement,
                                                              boolean replaceTrivialForEach) {
    boolean shouldWarn = replaceTrivialForEach || tb.hasOperations();
    if (ReferencesSearch.search(tb.getVariable(), new LocalSearchScope(statement)).findFirst() == null) {
      return new MatchMigration(shouldWarn, "anyMatch()/noneMatch()/allMatch");
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
  private static BaseStreamApiMigration findMigrationForReturn(PsiStatement statement, TerminalBlock tb, boolean replaceTrivialForEach) {
    boolean shouldWarn = replaceTrivialForEach || tb.hasOperations();
    PsiReturnStatement returnStatement = (PsiReturnStatement)tb.getSingleStatement();
    if (returnStatement == null) return null;
    PsiExpression value = PsiUtil.skipParenthesizedExprDown(returnStatement.getReturnValue());
    PsiReturnStatement nextReturnStatement = ControlFlowUtils.getNextReturnStatement(statement);
    if (nextReturnStatement != null && value instanceof PsiLiteralExpression) {
      Boolean foundResult = tryCast(((PsiLiteralExpression)value).getValue(), Boolean.class);
      if (foundResult != null) {
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
    }
    if (!VariableAccessUtils.variableIsUsed(tb.getVariable(), value)) {
      if (!replaceTrivialForEach && !tb.hasOperations() ||
          (tb.getLastOperation() instanceof FilterOp && tb.operations().count() == 2)) {
        return null;
      }
      return new MatchMigration(shouldWarn, "anyMatch");
    }
    if (nextReturnStatement != null && ExpressionUtils.isSafelyRecomputableExpression(nextReturnStatement.getReturnValue())
        && (!(tb.getVariable().getType() instanceof PsiPrimitiveType) || ExpressionUtils.isReferenceTo(value, tb.getVariable()))) {
      return new FindFirstMigration(shouldWarn);
    }
    return null;
  }

  @NotNull
  private static TextRange getRange(boolean shouldWarn, PsiStatement statement, boolean isOnTheFly) {
    boolean wholeStatement =
      isOnTheFly && (!shouldWarn || InspectionProjectProfileManager.isInformationLevel(SHORT_NAME, statement));
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
    if (arrayVariable == null || ControlFlowUtils.getInitializerUsageStatus(arrayVariable, tb.getStreamSourceStatement()) == UNKNOWN) {
      return null;
    }
    PsiNewExpression initializer = tryCast(arrayVariable.getInitializer(), PsiNewExpression.class);
    if (initializer == null) return null;
    PsiArrayType arrayType = tryCast(initializer.getType(), PsiArrayType.class);
    if (arrayType == null || !StreamApiUtil.isSupportedStreamElement(arrayType.getComponentType())) return null;
    PsiExpression dimension = ArrayUtil.getFirstElement(initializer.getArrayDimensions());
    if (dimension == null) return null;
    PsiExpression bound = PsiUtil.skipParenthesizedExprDown(loop.myBound);
    if (!isArrayLength(arrayVariable, dimension, bound)) return null;
    if (VariableAccessUtils.variableIsUsed(arrayVariable, assignment.getRExpression())) return null;
    return arrayVariable;
  }

  private static boolean isArrayLength(PsiLocalVariable arrayVariable, PsiExpression dimension, PsiExpression bound) {
    if (ExpressionUtils.isReferenceTo(ExpressionUtils.getArrayFromLengthExpression(bound), arrayVariable)) return true;
    if (EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(dimension, bound)) return true;
    if (bound instanceof PsiMethodCallExpression) {
      PsiExpression qualifier = ((PsiMethodCallExpression)bound).getMethodExpression().getQualifierExpression();
      if (qualifier != null && CollectionUtils.isCollectionOrMapSize(dimension, qualifier)) return true;
    }
    if (dimension instanceof PsiMethodCallExpression) {
      PsiExpression qualifier = ((PsiMethodCallExpression)dimension).getMethodExpression().getQualifierExpression();
      if (qualifier != null && CollectionUtils.isCollectionOrMapSize(bound, qualifier)) return true;
    }
    return false;
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

    abstract String createReplacement(CommentTracker ct);

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
    public String createReplacement(CommentTracker ct) {
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(myExpression.getProject());
      PsiExpression intermediate = makeIntermediateExpression(ct, factory);
      PsiExpression expression =
        myNegated ? factory.createExpressionFromText(BoolUtils.getNegatedExpressionText(intermediate, ct), myExpression) : intermediate;
      return "." + getOpName() + "(" + LambdaUtil.createLambda(myVariable, expression) + ")";
    }

    @NotNull
    String getOpName() {
      return "filter";
    }

    PsiExpression makeIntermediateExpression(CommentTracker ct, PsiElementFactory factory) {
      return ct.markUnchanged(myExpression);
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
    private final StreamSource mySource;
    private final PsiVariable myMatchVariable;

    protected CompoundFilterOp(StreamSource source, PsiVariable matchVariable, FilterOp sourceFilter) {
      super(sourceFilter.getExpression(), matchVariable, sourceFilter.isNegated());
      mySource = source;
      myMatchVariable = sourceFilter.getVariable();
    }


    @Override
    PsiExpression makeIntermediateExpression(CommentTracker ct, PsiElementFactory factory) {
      return factory.createExpressionFromText(mySource.createReplacement(ct) + ".anyMatch(" +
                                              ct.lambdaText(myMatchVariable, myExpression) + ")", myExpression);
    }

    @Override
    boolean isWriteAllowed(PsiVariable variable, PsiExpression reference) {
      return mySource.isWriteAllowed(variable, reference);
    }

    @Override
    StreamEx<PsiExpression> expressions() {
      return StreamEx.of(myExpression, mySource.getExpression());
    }
  }

  static class MapOp extends Operation {
    private final @Nullable PsiType myType;

    MapOp(PsiExpression expression, PsiVariable variable, @Nullable PsiType targetType) {
      super(expression, variable);
      myType = targetType;
    }

    @Override
    public String createReplacement(CommentTracker ct) {
      return StreamRefactoringUtil.generateMapOperation(myVariable, myType, ct.markUnchanged(myExpression));
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
    public String createReplacement(CommentTracker ct) {
      String operation = "flatMap";
      PsiType inType = myVariable.getType();
      PsiType outType = mySource.getVariable().getType();
      String lambda = myVariable.getName() + " -> " + getStreamExpression(ct);
      if (outType instanceof PsiPrimitiveType && !outType.equals(inType)) {
        if (outType.equals(PsiTypes.intType())) {
          operation = "flatMapToInt";
        }
        else if (outType.equals(PsiTypes.longType())) {
          operation = "flatMapToLong";
        }
        else if (outType.equals(PsiTypes.doubleType())) {
          operation = "flatMapToDouble";
        }
      }
      if (inType instanceof PsiPrimitiveType && !outType.equals(inType)) {
        return ".mapToObj(" + lambda + ")." + operation + "(" + CommonClassNames.JAVA_UTIL_FUNCTION_FUNCTION + ".identity())";
      }
      return "." + operation + "(" + lambda + ")";
    }

    public StreamSource getSource() {
      return mySource;
    }

    @NotNull
    String getStreamExpression(CommentTracker ct) {
      return mySource.createReplacement(ct);
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
      return statement.findExitedStatement() == mySource.getMainStatement();
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
    String createReplacement(CommentTracker ct) {
      return ".limit(" + JavaPsiMathUtil.add(myExpression, myDelta, ct) + ")";
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
        new CommentTracker().deleteAndRestoreComments(myCounterVariable);
      }
    }

    @Override
    boolean isWriteAllowed(PsiVariable variable, PsiExpression reference) {
      return variable == myCounterVariable && PsiTreeUtil.isAncestor(myCounter, reference, false);
    }
  }

  static class DistinctOp extends Operation {
    protected DistinctOp(PsiVariable variable) {
      super(null, variable);
    }

    @Override
    String createReplacement(CommentTracker ct) {
      return ".distinct()";
    }
  }

  abstract static class StreamSource extends Operation {
    private final PsiStatement myMainStatement;

    protected StreamSource(PsiStatement mainStatement, PsiVariable variable, PsiExpression expression) {
      super(expression, variable);
      myMainStatement = mainStatement;
    }

    PsiStatement getMainStatement() {
      return myMainStatement;
    }

    @Contract("null -> null")
    static StreamSource tryCreate(PsiLoopStatement statement) {
      if (statement == null) return null;
      BufferedReaderLines readerSource = BufferedReaderLines.from(statement);
      if (readerSource != null) return readerSource;
      if (statement instanceof PsiForStatement) {
        CountingLoopSource countingLoopSource = CountingLoopSource.from((PsiForStatement)statement);
        if (countingLoopSource != null) return countingLoopSource;
        return IterateStreamSource.from((PsiForStatement)statement);
      }
      if (statement instanceof PsiForeachStatement) {
        ArrayStream source = ArrayStream.from((PsiForeachStatement)statement);
        return source == null ? CollectionStream.from((PsiForeachStatement)statement) : source;
      }
      return null;
    }
  }

  static final class BufferedReaderLines extends StreamSource {
    private static final CallMatcher BUFFERED_READER_READ_LINE =
      CallMatcher.instanceCall("java.io.BufferedReader", "readLine").parameterCount(0);

    private boolean myDeleteVariable;

    private BufferedReaderLines(PsiLoopStatement loop, PsiVariable variable, PsiExpression expression, boolean deleteVariable) {
      super(loop, variable, expression);
      myDeleteVariable = deleteVariable;
    }

    @Override
    String createReplacement(CommentTracker ct) {
      return ct.text(myExpression) + ".lines()";
    }

    @Override
    void cleanUp() {
      if (myDeleteVariable) {
        new CommentTracker().deleteAndRestoreComments(myVariable);
      }
    }

    @Override
    boolean isWriteAllowed(PsiVariable variable, PsiExpression reference) {
      if (myVariable == variable) {
        if (PsiUtil.skipParenthesizedExprUp(reference.getParent()) ==
            PsiTreeUtil.getParentOfType(myExpression, PsiAssignmentExpression.class)) {
          return true;
        }
        PsiForStatement forStatement = PsiTreeUtil.getParentOfType(variable, PsiForStatement.class);
        if (forStatement != null && forStatement == PsiTreeUtil.getParentOfType(myVariable, PsiForStatement.class)) {
          return PsiTreeUtil.isAncestor(forStatement.getUpdate(), reference, false) ||
                 PsiTreeUtil.isAncestor(forStatement.getCondition(), reference, false);
        }
      }
      return false;
    }

    @Nullable
    public static BufferedReaderLines from(PsiLoopStatement loopStatement) {
      BufferedReaderLines whileSimple = extractWhileSimple(loopStatement);
      if (whileSimple != null) return whileSimple;
      BufferedReaderLines forSimple = extractForSimple(loopStatement);
      if (forSimple != null) return forSimple;
      return extractForReadInCondition(loopStatement);
    }

    /**
     * Extracts BufferedReaderSource from condition (for update or while condition), but additional checks may be required
     * Condition must look like: (line = reader.readLine()) != null
     */
    @Nullable
    private static BufferedReaderLines extractReaderFromCondition(@Nullable PsiExpression condition,
                                                                  @NotNull PsiLoopStatement loopStatement) {
      PsiBinaryExpression binOp = tryCast(PsiUtil.skipParenthesizedExprDown(condition), PsiBinaryExpression.class);
      if (binOp == null) return null;
      if (!JavaTokenType.NE.equals(binOp.getOperationTokenType())) return null;
      PsiExpression operand = ExpressionUtils.getValueComparedWithNull(binOp);
      if (operand == null) return null;
      PsiAssignmentExpression assignment = ExpressionUtils.getAssignment(operand);
      if (assignment == null) return null;
      PsiMethodCallExpression readerCall =
        tryCast(PsiUtil.skipParenthesizedExprDown(assignment.getRExpression()), PsiMethodCallExpression.class);
      if (!BUFFERED_READER_READ_LINE.test(readerCall)) return null;
      PsiExpression reader = readerCall.getMethodExpression().getQualifierExpression();

      PsiLocalVariable lineVar = ExpressionUtils.resolveLocalVariable(assignment.getLExpression());
      if (lineVar == null) return null;
      if (ReferencesSearch.search(lineVar).anyMatch(ref -> !PsiTreeUtil.isAncestor(loopStatement, ref.getElement(), true))) {
        return null;
      }
      return new BufferedReaderLines(loopStatement, lineVar, reader, false);
    }

    // for (String line; (line = reader.readLine()) != null; )
    @Nullable
    private static BufferedReaderLines extractForReadInCondition(PsiLoopStatement loopStatement) {
      PsiForStatement forLoop = tryCast(loopStatement, PsiForStatement.class);
      if (forLoop == null || forLoop.getUpdate() != null) return null;

      BufferedReaderLines reader = extractReaderFromCondition(forLoop.getCondition(), loopStatement);
      if (reader == null) return null;

      PsiDeclarationStatement declaration = tryCast(forLoop.getInitialization(), PsiDeclarationStatement.class);
      if (declaration == null) return null;
      PsiElement[] declaredElements = declaration.getDeclaredElements();
      if (declaredElements.length != 1) return null;
      PsiVariable lineVar = reader.getVariable();
      if (declaredElements[0] != lineVar) return null;
      return reader;
    }

    // for (String line = reader.readLine(); line != null; line = reader.readLine()) ...
    @Nullable
    private static BufferedReaderLines extractForSimple(PsiLoopStatement loopStatement) {
      PsiForStatement forLoop = tryCast(loopStatement, PsiForStatement.class);
      if (forLoop == null) return null;

      PsiDeclarationStatement declarationStatement = tryCast(forLoop.getInitialization(), PsiDeclarationStatement.class);
      if (declarationStatement == null) return null;
      PsiElement[] declarations = declarationStatement.getDeclaredElements();
      if (declarations.length != 1) return null;
      PsiLocalVariable lineVar = tryCast(declarations[0], PsiLocalVariable.class);
      if (lineVar == null) return null;
      if (ReferencesSearch.search(lineVar).anyMatch(ref -> !PsiTreeUtil.isAncestor(forLoop, ref.getElement(), true))) {
        return null;
      }
      PsiMethodCallExpression maybeReadLines = tryCast(PsiUtil.skipParenthesizedExprDown(lineVar.getInitializer()), PsiMethodCallExpression.class);
      if (!BUFFERED_READER_READ_LINE.test(maybeReadLines)) return null;
      PsiExpression reader = PsiUtil.skipParenthesizedExprDown(maybeReadLines.getMethodExpression().getQualifierExpression());
      PsiReferenceExpression readerRef = tryCast(reader, PsiReferenceExpression.class);
      if (readerRef == null) return null;
      PsiVariable readerVar = tryCast(readerRef.resolve(), PsiVariable.class);
      if (readerVar == null) return null;

      PsiBinaryExpression binOp = tryCast(PsiUtil.skipParenthesizedExprDown(forLoop.getCondition()), PsiBinaryExpression.class);
      if (binOp == null) return null;
      if (!JavaTokenType.NE.equals(binOp.getOperationTokenType())) return null;
      PsiExpression lineExpr = ExpressionUtils.getValueComparedWithNull(binOp);
      if (!ExpressionUtils.isReferenceTo(lineExpr, lineVar)) return null;


      PsiExpressionStatement updateStmt = tryCast(forLoop.getUpdate(), PsiExpressionStatement.class);
      if (updateStmt == null) return null;
      PsiExpression readNewLineExpr = ExpressionUtils.getAssignmentTo(updateStmt.getExpression(), lineVar);
      PsiMethodCallExpression readNewLineCall = tryCast(PsiUtil.skipParenthesizedExprDown(readNewLineExpr), PsiMethodCallExpression.class);
      if (!BUFFERED_READER_READ_LINE.test(readNewLineCall)) return null;
      if (!ExpressionUtils.isReferenceTo(readNewLineCall.getMethodExpression().getQualifierExpression(), readerVar)) return null;
      return new BufferedReaderLines(forLoop, lineVar, reader, false);
    }

    // while ((line = br.readLine()) != null)
    @Nullable
    private static BufferedReaderLines extractWhileSimple(PsiLoopStatement loopStatement) {
      PsiWhileStatement whileLoop = tryCast(loopStatement, PsiWhileStatement.class);
      if (whileLoop == null) return null;
      BufferedReaderLines reader = extractReaderFromCondition(whileLoop.getCondition(), loopStatement);
      if (reader == null) return null;
      reader.myDeleteVariable = true;
      return reader;
    }
  }

  static final class ArrayStream extends StreamSource {
    private ArrayStream(PsiLoopStatement loop, PsiVariable variable, PsiExpression expression) {
      super(loop, variable, expression);
    }

    @Override
    String createReplacement(CommentTracker ct) {
      if (myExpression instanceof PsiNewExpression) {
        PsiArrayInitializerExpression initializer = ((PsiNewExpression)myExpression).getArrayInitializer();
        if (initializer != null) {
          PsiElement[] children = initializer.getChildren();
          if (children.length > 2) {
            String initializerText = StreamEx.of(children, 1, children.length - 1).map(ct::text).joining();
            PsiType type = myExpression.getType();
            if (type instanceof PsiArrayType) {
              PsiType componentType = ((PsiArrayType)type).getComponentType();
              String streamClass = StreamApiUtil.getStreamClassForType(componentType);
              if (streamClass != null) {
                return streamClass + "." + (componentType instanceof PsiClassType ? "<" + componentType.getCanonicalText() + ">" : "")
                       + "of(" + initializerText + ")";
              }
            }
          }
        }
      }
      return CommonClassNames.JAVA_UTIL_ARRAYS + ".stream(" + ct.text(myExpression) + ")";
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

  static final class CollectionStream extends StreamSource {

    private CollectionStream(PsiLoopStatement loop, PsiVariable variable, PsiExpression expression) {
      super(loop, variable, expression);
    }

    @Override
    String createReplacement(CommentTracker ct) {
      return ct.text(myExpression, ParenthesesUtils.METHOD_CALL_PRECEDENCE) + ".stream()" + tryUnbox(myVariable);
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
      PsiParameter parameter = statement.getIterationParameter();
      if (collectionClass == null ||
          !InheritanceUtil.isInheritorOrSelf(iteratorClass, collectionClass, true) ||
          isRawSubstitution(iteratedValueType, collectionClass) ||
          !StreamApiUtil.isSupportedStreamElement(parameter.getType())) {
        return null;
      }
      return new CollectionStream(statement, parameter, iteratedValue);
    }
  }

  static final class CountingLoopSource extends StreamSource {
    final PsiExpression myBound;
    final boolean myIncluding;

    private CountingLoopSource(PsiStatement loop,
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
    public String createReplacement(CommentTracker ct) {
      String className = myVariable.getType().equals(PsiTypes.longType()) ? "java.util.stream.LongStream" : "java.util.stream.IntStream";
      String methodName = myIncluding ? "rangeClosed" : "range";
      return className + "." + methodName + "(" + ct.text(myExpression) + ", " + ct.text(myBound) + ")";
    }

    CountingLoopSource withBound(PsiExpression bound) {
      return new CountingLoopSource(getMainStatement(), getVariable(), getExpression(), bound, myIncluding);
    }

    CountingLoopSource withInitializer(PsiExpression expression) {
      return new CountingLoopSource(getMainStatement(), getVariable(), expression, myBound, myIncluding);
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
      if (loop == null || loop.isDescending()) return null;
      return new CountingLoopSource(forStatement, loop.getCounter(), loop.getInitializer(), loop.getBound(), loop.isIncluding());
    }
  }

  /**
   * for(int i = 0;; i = i + 1) // i + 1   - expression
   */
  static class IterateStreamSource extends StreamSource {
    private final PsiExpression myInitializer;
    private @Nullable final PsiExpression myCondition;
    private @Nullable final IElementType myOpType;
    private @Nullable final PsiUnaryExpression myUnaryExpression;

    /**
     * @param type            if not null, equivalent form of update is: variable type= expression;
     */
    protected IterateStreamSource(
      @NotNull PsiLoopStatement loop,
      @NotNull PsiVariable variable,
      @Nullable PsiExpression expression,
      @NotNull PsiExpression initializer,
      @Nullable PsiExpression condition,
      @Nullable IElementType type,
      @Nullable PsiUnaryExpression unaryExpression) {
      super(loop, variable, expression);
      myInitializer = initializer;
      myCondition = condition;
      myOpType = type;
      myUnaryExpression = unaryExpression;
    }

    @Nullable
    @Contract(pure = true)
    private static String getOperationSign(IElementType op) {
      if (op == JavaTokenType.AND) {
        return "&";
      }
      else if (op == JavaTokenType.ASTERISK) {
        return "*";
      }
      else if (op == JavaTokenType.DIV) {
        return "/";
      }
      else if (op == JavaTokenType.GTGT) {
        return ">>";
      }
      else if (op == JavaTokenType.GTGTGT) {
        return ">>>";
      }
      else if (op == JavaTokenType.LTLT) {
        return "<<";
      }
      else if (op == JavaTokenType.MINUS) {
        return "-";
      }
      else if (op == JavaTokenType.OR) {
        return "|";
      }
      else if (op == JavaTokenType.PERC) {
        return "%";
      }
      else if (op == JavaTokenType.PLUS) {
        return "+";
      }
      else if (op == JavaTokenType.XOR) {
        return "^";
      }
      return null;
    }

    @Override
    String createReplacement(CommentTracker ct) {
      String lambda;
      if (myOpType != null) {
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(myVariable.getProject());
        PsiExpression expression = myUnaryExpression == null ? myExpression : factory.createExpressionFromText("1", null);
        String expressionText = ParenthesesUtils.getText(ct.markUnchanged(expression), ParenthesesUtils.getPrecedenceForOperator(myOpType));
        String lambdaBody = myVariable.getName() + getOperationSign(myOpType) + expressionText;
        if (!myVariable.getType().equals(expression.getType())) {
          lambdaBody = ("(" + myVariable.getType().getCanonicalText() + ")") + "(" + lambdaBody + ")";
        }
        lambda = myVariable.getName() + "->" + lambdaBody;
      }
      else {
        lambda = ct.lambdaText(myVariable, myExpression);
      }
      String maybeCondition = myCondition != null ? ct.lambdaText(myVariable, myCondition) + "," : "";

      return StreamApiUtil.getStreamClassForType(myVariable.getType()) + ".iterate(" + ct.text(myInitializer) + "," + maybeCondition + lambda + ")";
    }

    @Override
    StreamEx<PsiExpression> expressions() {
      return StreamEx.of(myInitializer, myExpression, myCondition).nonNull();
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
    static IterateStreamSource from(@NotNull PsiForStatement forStatement) {
      PsiExpression condition = forStatement.getCondition();
      if (!PsiUtil.isLanguageLevel9OrHigher(forStatement) && condition != null) return null;
      PsiStatement initialization = forStatement.getInitialization();
      PsiDeclarationStatement initStmt = tryCast(initialization, PsiDeclarationStatement.class);
      if (initStmt == null || initStmt.getDeclaredElements().length != 1) return null;
      PsiLocalVariable variable = tryCast(initStmt.getDeclaredElements()[0], PsiLocalVariable.class);
      if (variable == null) return null;
      if (StreamApiUtil.getStreamClassForType(variable.getType()) == null) return null;
      PsiExpression initializer = variable.getInitializer();
      if (initializer == null) return null;
      PsiStatement update = forStatement.getUpdate();
      if (update == null) return null;
      PsiExpressionStatement exprStmt = tryCast(update, PsiExpressionStatement.class);
      if (exprStmt == null) return null;
      PsiExpression expression = exprStmt.getExpression();
      PsiExpression updateExpr = null;
      IElementType op;
      PsiUnaryExpression unaryExpression = null;
      if (expression instanceof PsiAssignmentExpression assignment) {
        op = TypeConversionUtil.convertEQtoOperation(assignment.getOperationTokenType());
        updateExpr = assignment.getRExpression();
        if (!ExpressionUtils.isReferenceTo(assignment.getLExpression(), variable)) return null;
        if (updateExpr == null) return null;
      }
      else if (expression instanceof PsiUnaryExpression) {
        unaryExpression = (PsiUnaryExpression)expression;
        IElementType tokenType = unaryExpression.getOperationTokenType();
        op = getOperation(tokenType);
        if (op == null) return null;
      }
      else {
        return null;
      }
      if (updateExpr != null && !ExceptionUtil.getThrownCheckedExceptions(updateExpr).isEmpty()) return null;
      if (condition != null && !ExceptionUtil.getThrownCheckedExceptions(condition).isEmpty()) return null;
      if (!VariableAccessUtils.variableIsUsed(variable, update)) return null;
      return new IterateStreamSource(forStatement, variable, updateExpr, initializer, condition, op, unaryExpression);
    }

    @Nullable
    private static IElementType getOperation(IElementType tokenType) {
      if (tokenType == JavaTokenType.PLUSPLUS) {
        return JavaTokenType.PLUS;
      }
      else if (tokenType == JavaTokenType.MINUSMINUS) {
        return JavaTokenType.MINUS;
      }
      else {
        return null;
      }
    }
  }
}
