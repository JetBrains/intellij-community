// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.migration;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.*;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.intellij.util.ObjectUtils.tryCast;

/**
 * @author Bas Leijdekkers
 */
public final class TryFinallyCanBeTryWithResourcesInspection extends BaseInspection {

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("try.finally.can.be.try.with.resources.problem.descriptor");
  }

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new TryFinallyCanBeTryWithResourcesFix();
  }

  private static class TryFinallyCanBeTryWithResourcesFix extends PsiUpdateModCommandQuickFix {
    @Override
    public @Nls @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("try.finally.can.be.try.with.resources.quickfix");
    }

    private static <T> Pair<List<T>, List<T>> partition(Iterable<? extends T> iterable, Predicate<? super T> predicate) {
      List<T> list1 = new SmartList<>();
      List<T> list2 = new SmartList<>();
      for (T value : iterable) {
        if (predicate.test(value)) {
          list1.add(value);
        } else {
          list2.add(value);
        }
      }
      return new Pair<>(list1, list2);
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiTryStatement tryStatement)) {
        return;
      }
      PsiCodeBlock tryBlock = tryStatement.getTryBlock();
      if (tryBlock == null) return;
      Context context = Context.from(tryStatement);
      if (context == null) return;

      Pair<List<ResourceVariable>, List<ResourceVariable>> partition =
        partition(context.myResourceVariables, variable -> variable.getInitializedElement().getTextOffset() < tryStatement.getTextOffset());
      List<ResourceVariable> before = partition.first;
      List<ResourceVariable> after = partition.second;
      String resourceListBefore = joinToString(before);
      String resourceListAfter = joinToString(after);
      @NonNls StringBuilder sb = new StringBuilder("try(");
      PsiResourceList resourceListElement = tryStatement.getResourceList();
      boolean hasSemicolon = false;
      if (!before.isEmpty()) {
        sb.append(resourceListBefore);
        if (resourceListElement != null || !after.isEmpty()) {
          sb.append(";");
          hasSemicolon = true;
        }
      }
      if (resourceListElement != null) {
        PsiElement[] children = resourceListElement.getChildren();
        if (children.length > 2 && resourceListElement.getResourceVariablesCount() > 0) {
          for (int i = 1; i < children.length - 1; i++) {
            sb.append(children[i].getText());
          }
        }
      }
      if (!after.isEmpty()) {
        if ((!before.isEmpty() || resourceListElement != null) && !hasSemicolon) {
          sb.append(";");
        }
        sb.append(resourceListAfter);
      }
      sb.append(")");
      List<PsiLocalVariable> locals = StreamEx.of(context.myResourceVariables)
                                              .map(resourceVariable -> resourceVariable.myVariable)
                                              .select(PsiLocalVariable.class)
                                              .sorted(PsiElementOrderComparator.getInstance())
                                              .toList();

      if (locals.size() == 1) {
        PsiLocalVariable variable = locals.get(0);
        PsiStatement declaration = PsiTreeUtil.getParentOfType(variable, PsiStatement.class);
        if (declaration != null)  {
          if (declaration.getParent() == tryStatement.getParent()) {
            List<PsiStatement> statements = collectStatementsBetween(declaration, tryStatement);
            PsiJavaToken lBrace = tryBlock.getLBrace();
            if (lBrace != null) {
              for (int i = statements.size() - 1; i >= 0; i--) {
                PsiStatement statement = statements.get(i);
                tryBlock.addAfter(statement, lBrace);
                if (statement.isValid()) {
                  statement.delete();
                }
              }
            }
          }
        }
      }
      restoreStatementsBeforeLastVariableInTryResource(tryStatement, tryBlock, context);

      Set<PsiCatchSection> catchSectionSet = new HashSet<>(context.myCatchSectionsToAdd);

      for (PsiStatement statement : context.myStatementsToDelete) {
        if (statement.isValid()) {
          deleteStatement(statement, catchSectionSet);
        }
      }
      for (ResourceVariable variable : context.myResourceVariables) {
        if (!variable.myUsedOutsideTry) {
          if (variable.myVariable.isValid()) {
            new CommentTracker().deleteAndRestoreComments(variable.myVariable);
          }
        }
      }

      if (!context.myCatchSectionsToAdd.isEmpty() && tryStatement.getCatchSections().length != 0) {
        sb.append("{").append("try");
        if (!addTryAndCatchBlocks(tryStatement, sb)) return;
        sb.append("}");
      } else {
        if (!addTryAndCatchBlocks(tryStatement, sb)) return;
      }

      for (PsiCatchSection section : context.myCatchSectionsToAdd) {
        sb.append(section.getText());
      }

      if (!addFinallyBlock(tryStatement, sb)) return;

      tryStatement.replace(JavaPsiFacade.getElementFactory(project).createStatementFromText(sb.toString(), tryStatement));
    }

    private static boolean addTryAndCatchBlocks(@NotNull PsiTryStatement tryStatement, @NotNull StringBuilder sb) {
      PsiCodeBlock tryBlock = tryStatement.getTryBlock();
      if (tryBlock == null) return false;
      sb.append(tryBlock.getText());

      for (PsiCatchSection section : tryStatement.getCatchSections()) {
        sb.append(section.getText());
      }

      return true;
    }

    private static boolean addFinallyBlock(@NotNull PsiTryStatement tryStatement, @NotNull StringBuilder sb) {
      PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
      if (finallyBlock == null) return false;
      if (!ControlFlowUtils.isEmptyCodeBlock(finallyBlock)) {
        sb.append("finally").append(finallyBlock.getText());
      }
      else {
        PsiElement[] finallyBlockChildren = finallyBlock.getChildren();
        if (!StreamEx.of(finallyBlockChildren).skip(1).limit(finallyBlockChildren.length - 2)
          .allMatch(el -> el instanceof PsiWhiteSpace)) {
          PsiElement tryParent = tryStatement.getParent();
          tryParent.addRangeAfter(finallyBlockChildren[1], finallyBlockChildren[finallyBlockChildren.length - 2], tryStatement);
        }
      }
      return true;
    }

    private static void deleteStatement(@NotNull PsiStatement statement, @NotNull Set<PsiCatchSection> catchSectionSet) {
      CommentTracker ct = new CommentTracker();
      if (statement instanceof PsiTryStatement psiTryStatement) {
        for (PsiCatchSection section : psiTryStatement.getCatchSections()) {
          if (catchSectionSet.contains(section)) {
            ct.markUnchanged(section);
          }
        }
      }
      ct.deleteAndRestoreComments(statement);
    }

    private static String joinToString(List<? extends ResourceVariable> variables) {
      return variables.stream().map(ResourceVariable::generateResourceDeclaration).collect(Collectors.joining("; "));
    }

    private static void restoreStatementsBeforeLastVariableInTryResource(PsiTryStatement tryStatement,
                                                                         PsiCodeBlock tryBlock,
                                                                         Context context) {
      Optional<PsiExpression> lastInTryVariable = StreamEx.of(context.myResourceVariables)
                                                          .map(v -> v.myInitializer)
                                                          .filter(e -> e != null && PsiTreeUtil.isAncestor(tryBlock, e, false))
                                                          .max(PsiElementOrderComparator.getInstance());
      List<PsiStatement> elementsToRestore = new ArrayList<>();
      if (lastInTryVariable.isPresent()) {
        PsiStatement last = PsiTreeUtil.getParentOfType(lastInTryVariable.get(), PsiStatement.class);
        PsiStatement[] statements = tryBlock.getStatements();
        for (int i = 0; i < statements.length && statements[i] != last; i++) {
          PsiStatement current = statements[i];
          if (context.myStatementsToDelete.contains(current)) {
            continue;
          }
          elementsToRestore.add(current);
        }
      }
      PsiElement tryStatementParent = tryStatement.getParent();
      for (PsiStatement statement : elementsToRestore) {
        tryStatementParent.addBefore(statement, tryStatement);
        statement.delete();
      }
    }
  }

  @Override
  public @NotNull Set<@NotNull JavaFeature> requiredFeatures() {
    return Set.of(JavaFeature.TRY_WITH_RESOURCES);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new TryFinallyCanBeTryWithResourcesVisitor();
  }

  private static class TryFinallyCanBeTryWithResourcesVisitor extends BaseInspectionVisitor {

    @Override
    public void visitTryStatement(@NotNull PsiTryStatement tryStatement) {
      super.visitTryStatement(tryStatement);
      if (Context.from(tryStatement) == null) return;
      registerStatementError(tryStatement);
    }
  }

  private static final class Context {
    final @NotNull List<ResourceVariable> myResourceVariables;
    final @NotNull List<PsiCatchSection> myCatchSectionsToAdd;
    final @NotNull Set<PsiStatement> myStatementsToDelete;

    private Context(@NotNull List<ResourceVariable> resourceVariables, @NotNull Set<PsiStatement> statementsToDelete, @NotNull List<PsiCatchSection> catchSectionsToAdd) {
      myResourceVariables = resourceVariables;
      myStatementsToDelete = statementsToDelete;
      myCatchSectionsToAdd = catchSectionsToAdd;
    }

    static @Nullable Context from(@NotNull PsiTryStatement tryStatement) {
      PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
      if (finallyBlock == null) return null;
      PsiCodeBlock tryBlock = tryStatement.getTryBlock();
      if (tryBlock == null) return null;
      PsiStatement[] tryStatements = tryBlock.getStatements();
      PsiStatement[] finallyStatements = finallyBlock.getStatements();
      BitSet closedVariableStatementIndices = new BitSet(finallyStatements.length);
      Set<PsiVariable> collectedVariables = new HashSet<>();
      List<PsiCatchSection> catchSectionsToMigrate = new ArrayList<>();
      for (int i = 0, length = finallyStatements.length; i < length; i++) {
        PsiStatement statement = finallyStatements[i];
        boolean shouldDeleteStatement;
        if (statement instanceof PsiTryStatement) {
          if (i != 0) return null;
          shouldDeleteStatement = findAutoCloseableVariables(statement, collectedVariables, catchSectionsToMigrate);
        } else {
          shouldDeleteStatement = findAutoClosableVariableWithoutTry(statement, collectedVariables);
        }
        closedVariableStatementIndices.set(i, shouldDeleteStatement);
      }

      if (collectedVariables.isEmpty()) return null;
      if (resourceVariableUsedInCatches(tryStatement, collectedVariables)) return null;
      if (resourceVariablesUsedInFinally(finallyBlock, collectedVariables)) return null;

      List<ResourceVariable> resourceVariables = new ArrayList<>();
      List<PsiStatement> statementsToDelete = new ArrayList<>();
      IntList initializerPositions = new IntArrayList();
      for (PsiVariable resourceVariable : collectedVariables) {
        boolean variableUsedOutsideTry = isVariableUsedOutsideContext(resourceVariable, tryStatement);
        if (!PsiUtil.isAvailable(JavaFeature.REFS_AS_RESOURCE, finallyBlock) && variableUsedOutsideTry) return null;
        if (!variableUsedOutsideTry && resourceVariable instanceof PsiLocalVariable) {
          PsiExpression initializer = resourceVariable.getInitializer();
          boolean hasNonNullInitializer = initializer != null && !PsiTypes.nullType().equals(initializer.getType());
          if (!hasNonNullInitializer) {
            int assignmentStatementIndex = findInitialization(tryStatements, resourceVariable);
            if (assignmentStatementIndex == -1) return null;
            initializerPositions.add(assignmentStatementIndex);
            PsiExpressionStatement assignmentStatement = (PsiExpressionStatement)tryStatements[assignmentStatementIndex];
            PsiExpression expression = assignmentStatement.getExpression();
            PsiAssignmentExpression assignment = tryCast(expression, PsiAssignmentExpression.class);
            if (assignment == null) return null;
            initializer = assignment.getRExpression();
            if (initializer == null) return null;
            statementsToDelete.add(tryStatements[assignmentStatementIndex]);
          }
          else {
            if (VariableAccessUtils.variableIsAssigned(resourceVariable, tryBlock)) return null;
          }
          resourceVariables.add(new ResourceVariable(initializer, false, resourceVariable));
        }
        else if (((resourceVariable instanceof PsiLocalVariable && resourceVariable.getInitializer() != null) ||
                  resourceVariable instanceof PsiParameter) && FinalUtils.canBeFinal(resourceVariable)) {
          resourceVariables.add(new ResourceVariable(null, true, resourceVariable));
        }
        else {
          return null;
        }
      }
      for (int i = 0; i < finallyStatements.length; i++) {
        if (closedVariableStatementIndices.get(i)) {
          statementsToDelete.add(finallyStatements[i]);
        }
      }
      if (!noStatementsBetweenVariableDeclarations(collectedVariables) || !initializersAreAtTheBeginning(initializerPositions)) {
        return null;
      }

      resourceVariables.sort(Comparator.comparing(o -> o.getInitializedElement(), PsiElementOrderComparator.getInstance()));
      ResourceVariable lastNonTryVar = ContainerUtil.findLast(resourceVariables,
                                                              r -> !PsiTreeUtil.isAncestor(tryStatement, r.myVariable, false));
      if (lastNonTryVar != null) {
        PsiVariable variable = lastNonTryVar.myVariable;
        PsiStatement statement = PsiTreeUtil.getParentOfType(variable, PsiStatement.class);
        List<PsiStatement> statements = collectStatementsBetween(statement, tryStatement);

        boolean varUsedNotInTry = StreamEx.of(statements)
          .flatMap(stmt -> StreamEx.ofTree((PsiElement)stmt, e -> StreamEx.of(e.getChildren())))
          .select(PsiLocalVariable.class)
          .anyMatch(variable1 -> isVariableUsedOutsideContext(variable1, tryStatement) || isVariableUsedInsideContext(variable1, finallyBlock));
        if (varUsedNotInTry) return null;
      }
      return new Context(resourceVariables, new HashSet<>(statementsToDelete), catchSectionsToMigrate);
    }

    private static boolean initializersAreAtTheBeginning(IntList initializerPositions) {
      initializerPositions.sort(null);
      for (int i = 0; i < initializerPositions.size(); i++) {
        if (initializerPositions.getInt(i) != i) return false;
      }
      return true;
    }

    private static boolean noStatementsBetweenVariableDeclarations(Set<PsiVariable> collectedVariables) {
      return StreamEx.of(collectedVariables)
                     .select(PsiLocalVariable.class)
                     .sorted(PsiElementOrderComparator.getInstance())
                     .map(var -> PsiTreeUtil.getParentOfType(var, PsiStatement.class))
                     .pairMap((l1, l2) -> l1 != null && l2 != null && l1.getParent() == l2.getParent() && collectStatementsBetween(l1, l2).isEmpty())
                     .allMatch(b -> b);
    }
  }

  private static List<PsiStatement> collectStatementsBetween(PsiStatement startExclusive, PsiStatement endExclusive) {
    List<PsiStatement> statements = new ArrayList<>();
    PsiStatement current = PsiTreeUtil.getNextSiblingOfType(startExclusive, PsiStatement.class);
    while (current != endExclusive && current != null) {
      statements.add(current);
      current = PsiTreeUtil.getNextSiblingOfType(current.getNextSibling(), PsiStatement.class);
    }
    return statements;
  }

  private static boolean resourceVariablesUsedInFinally(@NotNull PsiCodeBlock finallyBlock, @NotNull Set<? extends PsiVariable> collectedVariables) {
    if (ContainerUtil.exists(collectedVariables, variable -> isAutoCloseableDeclaredInFinallyBlock(finallyBlock, variable))) return true;

    AutoCloseableVariableUsedVisitor visitor = new AutoCloseableVariableUsedVisitor(collectedVariables);
    finallyBlock.accept(visitor);
    return visitor.isVariableUsed();
  }

  private static boolean resourceVariableUsedInCatches(PsiTryStatement tryStatement, Set<? extends PsiVariable> collectedVariables) {
    for (PsiCatchSection catchSection : tryStatement.getCatchSections()) {
      for (PsiVariable variable : collectedVariables) {
        if (VariableAccessUtils.variableIsUsed(variable, catchSection)) {
          return true;
        }
      }
    }
    return false;
  }

  private static class ResourceVariable {
    final @Nullable("when in java 9") PsiExpression myInitializer;
    final boolean myUsedOutsideTry; // true only if Java9 or above
    final @NotNull PsiVariable myVariable;

    ResourceVariable(@Nullable PsiExpression initializer, boolean usedOutsideTry, @NotNull PsiVariable variable) {
      myInitializer = initializer;
      myUsedOutsideTry = usedOutsideTry;
      myVariable = variable;
    }

    String generateResourceDeclaration() {
      if (myUsedOutsideTry) {
        return myVariable.getName();
      }
      else {
        assert myInitializer != null;
        return Objects.requireNonNull(myVariable.getTypeElement()).getText() + " " + myVariable.getName() + "=" + myInitializer.getText();
      }
    }

    PsiElement getInitializedElement() {
      if (myInitializer != null) return myInitializer;
      return myVariable;
    }
  }

  private static boolean isVariableUsedInsideContext(@NotNull PsiVariable variable, @NotNull PsiElement context) {
    VariableUsedWithContextVisitor visitor = new VariableUsedWithContextVisitor(variable, null);
    context.accept(visitor);
    return visitor.isVariableUsed();
  }

  private static boolean isVariableUsedOutsideContext(PsiVariable variable, PsiElement context) {
    final VariableUsedWithContextVisitor visitor = new VariableUsedWithContextVisitor(variable, context);
    final PsiElement declarationScope = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
    if (declarationScope == null) {
      return true;
    }
    declarationScope.accept(visitor);
    return visitor.isVariableUsed();
  }

  private static boolean findAutoClosableVariableWithoutTry(@Nullable PsiStatement statement,
                                                            @NotNull Set<? super PsiVariable> variables) {
    if (statement instanceof PsiIfStatement ifStatement) {
      if (ifStatement.getElseBranch() != null) return false;
      final PsiExpression condition = ifStatement.getCondition();
      if (!(condition instanceof PsiBinaryExpression binaryExpression)) return false;
      final IElementType tokenType = binaryExpression.getOperationTokenType();
      if (!JavaTokenType.NE.equals(tokenType)) return false;
      final PsiExpression lhs = binaryExpression.getLOperand();
      final PsiExpression rhs = binaryExpression.getROperand();
      if (rhs == null) return false;
      final PsiLocalVariable variable;
      if (PsiTypes.nullType().equals(rhs.getType())) {
        variable = ExpressionUtils.resolveLocalVariable(lhs);
      }
      else if (PsiTypes.nullType().equals(lhs.getType())) {
        variable = ExpressionUtils.resolveLocalVariable(rhs);
      }
      else {
        return false;
      }
      if (variable == null) return false;
      final PsiStatement thenBranch = ifStatement.getThenBranch();
      final PsiVariable resourceVariable;
      if (thenBranch instanceof PsiExpressionStatement) {
        resourceVariable = findAutoCloseableVariable(thenBranch);
      }
      else if (thenBranch instanceof PsiBlockStatement blockStatement) {
        final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
        resourceVariable = findAutoCloseableVariable(ControlFlowUtils.getOnlyStatementInBlock(codeBlock));
      }
      else {
        return false;
      }
      if (variable.equals(resourceVariable)) {
        variables.add(resourceVariable);
        return true;
      }
    }
    else if (statement instanceof PsiExpressionStatement expressionStatement) {
      final PsiExpression expression = expressionStatement.getExpression();
      if (!(expression instanceof PsiMethodCallExpression methodCallExpression)) return false;
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final String methodName = methodExpression.getReferenceName();
      if (HardcodedMethodConstants.CLOSE.equals(methodName)) {
        final PsiExpression qualifier = methodExpression.getQualifierExpression();
        if (!(qualifier instanceof PsiReferenceExpression referenceExpression)) return false;
        final PsiElement target = referenceExpression.resolve();
        if (!(target instanceof PsiLocalVariable || target instanceof PsiParameter) || target instanceof PsiResourceVariable) return false;
        PsiVariable variable = (PsiVariable)target;
        if (!isAutoCloseable(variable)) return false;
        variables.add(variable);
        return true;
      }
      else {
        return false;
      }
    }
    return false;
  }

  private static boolean isAutoCloseableDeclaredInFinallyBlock(@NotNull PsiCodeBlock block, @NotNull PsiVariable variable) {
    return variable instanceof PsiLocalVariable && PsiTreeUtil.isAncestor(block, variable, true);
  }

  private static @Nullable PsiVariable findAutoCloseableVariable(@Nullable PsiStatement statement) {
    Set<PsiVariable> variables = new HashSet<>(1);
    if (!findAutoCloseableVariables(statement, variables, null)) return null;
    if (variables.isEmpty()) {
      return null;
    }
    else {
      return ContainerUtil.getFirstItem(variables);
    }
  }

  private static boolean findAutoCloseableVariables(@Nullable PsiStatement statement,
                                                    @NotNull Set<? super PsiVariable> variables,
                                                    @Nullable List<? super PsiCatchSection> catchSectionsToMigrate) {
    if (findAutoClosableVariableWithoutTry(statement, variables)) return true;
    if (statement instanceof PsiTryStatement tryStatement) {
      if (tryStatement.getResourceList() != null || tryStatement.getFinallyBlock() != null) return true;
      PsiCodeBlock tryBlock = tryStatement.getTryBlock();
      if (tryBlock == null) return true;
      PsiStatement[] tryStatements = tryBlock.getStatements();
      for (PsiStatement tryStmt : tryStatements) {
        if (!findAutoClosableVariableWithoutTry(tryStmt, variables)) {
          return false;
        }
      }
      if (catchSectionsToMigrate != null) {
        catchSectionsToMigrate.addAll(Arrays.asList(tryStatement.getCatchSections()));
      }
      return true;
    }
    return false;
  }

  private static boolean isAutoCloseable(PsiVariable variable) {
    final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(variable.getType());
    return InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_LANG_AUTO_CLOSEABLE);
  }

  private static int findInitialization(PsiElement[] elements, PsiVariable variable) {
    int result = -1;
    final int statementsLength = elements.length;
    for (int i = 0; i < statementsLength; i++) {
      final PsiElement element = elements[i];
      if (isAssignmentToVariable(element, variable)) {
        if (result >= 0) {
          return -1;
        }
        result = i;
      }
      else if (VariableAccessUtils.variableIsAssigned(variable, element)) {
        return -1;
      }
    }
    return result;
  }

  private static boolean isAssignmentToVariable(PsiElement element, PsiVariable variable) {
    PsiExpressionStatement expressionStatement = tryCast(element, PsiExpressionStatement.class);
    if (expressionStatement == null) return false;
    PsiAssignmentExpression assignmentExpression = tryCast(expressionStatement.getExpression(), PsiAssignmentExpression.class);
    if (assignmentExpression == null) return false;
    if (assignmentExpression.getRExpression() == null) return false;
    return ExpressionUtils.isReferenceTo(assignmentExpression.getLExpression(), variable);
  }

  private static class VariableUsedVisitorBase extends JavaRecursiveElementWalkingVisitor {
    protected boolean used;

    @Override
    public void visitElement(@NotNull PsiElement element) {
      if (used) return;
      super.visitElement(element);
    }

    public boolean isVariableUsed() {
      return used;
    }
  }


  private static class AutoCloseableVariableUsedVisitor extends VariableUsedVisitorBase {

    private final @NotNull Set<? extends PsiVariable> collectedVariables;

    private AutoCloseableVariableUsedVisitor(@NotNull Set<? extends PsiVariable> collectedVariables) {
      this.collectedVariables = collectedVariables;
    }
    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression referenceExpression) {
      if (used) return;

      super.visitReferenceExpression(referenceExpression);

      PsiVariable variable = ExpressionUtils.resolveVariable(referenceExpression);
      if (variable == null || !collectedVariables.contains(variable)) return;
      if (isInDifferentAnonymousClassOrLambda(referenceExpression, variable)) {
        used = true;
        return;
      }
      PsiElement parent = PsiTreeUtil.getParentOfType(referenceExpression, true, PsiMethodCallExpression.class, PsiExpressionList.class);
      if (parent instanceof PsiMethodCallExpression methodCallExpression && !isCloseMethodCalled(methodCallExpression) || parent instanceof PsiExpressionList) {
        used = true;
      }
    }

    private static boolean isInDifferentAnonymousClassOrLambda(@NotNull PsiElement referenceExpression,
                                                               @NotNull PsiElement variable) {
      return PsiTreeUtil.getParentOfType(referenceExpression, true, PsiAnonymousClass.class, PsiLambdaExpression.class) !=
             PsiTreeUtil.getParentOfType(variable, true, PsiAnonymousClass.class, PsiLambdaExpression.class);
    }

    private static boolean isCloseMethodCalled(@NotNull PsiMethodCallExpression methodCallExpression) {
      final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      if (!argumentList.isEmpty()) {
        return false;
      }
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final String name = methodExpression.getReferenceName();
      return HardcodedMethodConstants.CLOSE.equals(name);
    }
  }

  private static class VariableUsedWithContextVisitor extends VariableUsedVisitorBase {
    private final @NotNull PsiVariable variable;
    private final @Nullable PsiElement skipContext;

    VariableUsedWithContextVisitor(@NotNull PsiVariable variable, @Nullable PsiElement skipContext) {
      this.variable = variable;
      this.skipContext = skipContext;
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
      if (used) return;
      if (element.equals(skipContext)) {
        return;
      }
      super.visitElement(element);
    }

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression referenceExpression) {
      if (used) return;

      super.visitReferenceExpression(referenceExpression);
      final PsiElement target = referenceExpression.resolve();
      if (target == null) {
        return;
      }
      if (target.equals(variable)) {
        used = true;
      }
    }
  }
}
