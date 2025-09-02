/*
 * Copyright 2003-2024 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.controlflow;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.function.Predicate;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;
import static com.intellij.util.ObjectUtils.tryCast;

/**
 * Reports not only on identical branches, but also when the branches have common parts.
 */
public final class IfStatementWithIdenticalBranchesInspection extends AbstractBaseJavaLocalInspectionTool {
  public boolean myHighlightWhenLastStatementIsCall = false;
  public boolean myHighlightElseIf = false;

  private static final List<IfStatementInspector> ourInspectors = List.of(
    ImplicitElse::inspect,
    ThenElse::inspect,
    ElseIf::inspect
  );


  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("myHighlightWhenLastStatementIsCall",
               JavaAnalysisBundle.message("inspection.common.if.parts.settings.highlight.when.tail.call")),
      checkbox("myHighlightElseIf",
               JavaAnalysisBundle.message("inspection.common.if.parts.settings.highlight.else.if"))
      );
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitIfStatement(@NotNull PsiIfStatement ifStatement) {
        PsiStatement[] thenStatements = unwrap(ifStatement.getThenBranch());
        PsiStatement[] elseStatements = unwrap(ifStatement.getElseBranch());
        for (IfStatementInspector inspector : ourInspectors) {
          IfInspectionResult result = inspector.inspect(ifStatement, thenStatements, elseStatements, isOnTheFly, 
                                                        IfStatementWithIdenticalBranchesInspection.this);
          if (result != null) {
            ProblemHighlightType highlightType;
            if (result.myIsWarning) {
              highlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
            }
            else {
              if (!isOnTheFly) return;
              highlightType = ProblemHighlightType.INFORMATION;
            }
            LocalQuickFix[] fixes;
            if (myHighlightWhenLastStatementIsCall) {
              fixes = new LocalQuickFix[]{
                LocalQuickFix.from(new UpdateInspectionOptionFix(
                  IfStatementWithIdenticalBranchesInspection.this,
                  "myHighlightWhenLastStatementIsCall",
                  JavaAnalysisBundle.message("inspection.common.if.parts.disable.highlight.tail.call"),
                  false
                )),
                result.myFix
              };
            } else {
              fixes = new LocalQuickFix[] {
                result.myFix
              };
            }
            holder.registerProblem(result.myElementToHighlight, result.myMessage, highlightType, fixes);
          }
        }
      }
    };
  }

  @FunctionalInterface
  private interface IfStatementInspector {
    @Nullable IfInspectionResult inspect(@NotNull PsiIfStatement ifStatement,
                                         PsiStatement @NotNull [] thenBranch,
                                         PsiStatement @NotNull [] elseBranch,
                                         boolean isOnTheFly,
                                         IfStatementWithIdenticalBranchesInspection inspection);
  }

  private record IfInspectionResult(@NotNull PsiElement myElementToHighlight,
                                     boolean myIsWarning,
                                     @NotNull LocalQuickFix myFix,
                                     @NotNull @InspectionMessage String myMessage) {}

  private static class MergeElseIfsFix extends PsiUpdateModCommandQuickFix {
    private final boolean myInvert;

    private MergeElseIfsFix(boolean invert) {
      myInvert = invert;
    }

    @Override
    public @Nls @NotNull String getName() {
      return myInvert ? JavaAnalysisBundle.message("inspection.common.if.parts.family.else.if.invert") 
                      : JavaAnalysisBundle.message("inspection.common.if.parts.family.else.if");
    }

    @Override
    public @Nls @NotNull String getFamilyName() {
      return JavaAnalysisBundle.message("inspection.common.if.parts.family.else.if");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiIfStatement ifStatement = tryCast(element.getParent(), PsiIfStatement.class);
      if (ifStatement == null) return;
      ElseIf elseIf = ElseIf.from(ifStatement, unwrap(ifStatement.getThenBranch()));
      if (elseIf == null) return;
      PsiExpression condition = ifStatement.getCondition();
      if (condition == null) return;
      CommentTracker ct = new CommentTracker();
      ct.markUnchanged(elseIf.myElseIfBranchToRemove);
      ct.markUnchanged(condition);
      ct.replace(elseIf.myElseBranch, elseIf.myElseIfBranchToKeep);

      String firstCondition = ParenthesesUtils.getText(condition, ParenthesesUtils.OR_PRECEDENCE);
      String secondCondition = elseIf.myInvert ?
                               BoolUtils.getNegatedExpressionText(elseIf.myElseIfCondition, ParenthesesUtils.OR_PRECEDENCE, ct) :
                               ParenthesesUtils.getText(ct.markUnchanged(elseIf.myElseIfCondition), ParenthesesUtils.OR_PRECEDENCE);

      String newCondition = firstCondition + "||" + secondCondition;
      ct.replaceAndRestoreComments(condition, newCondition);
    }
  }

  private static @Nullable ExtractionUnit extractHeadCommonStatement(@NotNull PsiStatement thenStmt,
                                                                     @NotNull PsiStatement elseStmt,
                                                                     @NotNull List<PsiLocalVariable> conditionVariables,
                                                                     @NotNull LocalEquivalenceChecker equivalence) {
    if (thenStmt instanceof PsiDeclarationStatement) {
      return extractDeclarationUnit(thenStmt, elseStmt, conditionVariables, equivalence);
    }
    if (!equivalence.statementsAreEquivalent(thenStmt, elseStmt)) return null;
    boolean statementMayChangeSemantics = SideEffectChecker.mayHaveSideEffects(thenStmt, e -> false);
    boolean mayInfluenceCondition = mayInfluenceCondition(thenStmt, conditionVariables);
    return new ExtractionUnit(thenStmt, elseStmt, statementMayChangeSemantics, mayInfluenceCondition, true);
  }

  private static @Nullable ExtractionUnit extractDeclarationUnit(@NotNull PsiStatement thenStmt,
                                                                 @NotNull PsiStatement elseStmt,
                                                                 @NotNull List<PsiLocalVariable> conditionVariables,
                                                                 @NotNull LocalEquivalenceChecker equivalence) {
    if (!equivalence.topLevelVarsAreEqualNotConsideringInitializers(thenStmt, elseStmt)) return null;
    PsiLocalVariable thenVariable = extractVariable(thenStmt);
    PsiLocalVariable elseVariable = extractVariable(elseStmt);
    if (thenVariable == null || elseVariable == null) return null;
    PsiExpression thenInitializer = thenVariable.getInitializer();
    if (thenInitializer == null) return null;
    boolean statementMayChangeSemantics = SideEffectChecker.mayHaveSideEffects(thenInitializer, e -> false);
    boolean mayInfluenceCondition = mayInfluenceCondition(thenInitializer, conditionVariables);
    boolean equivalent = equivalence.expressionsAreEquivalent(thenInitializer, elseVariable.getInitializer());
    return new VariableDeclarationUnit(thenStmt,
                                       elseStmt,
                                       statementMayChangeSemantics,
                                       mayInfluenceCondition,
                                       equivalent,
                                       thenVariable,
                                       elseVariable);
  }

  private static boolean mayInfluenceCondition(@NotNull PsiElement element, @NotNull List<PsiLocalVariable> conditionVariables) {
    return StreamEx.ofTree(element, e -> StreamEx.of(e.getChildren()))
      .select(PsiReferenceExpression.class)
      .map(expression -> expression.resolve())
      .nonNull()
      .select(PsiLocalVariable.class)
      .anyMatch(el -> conditionVariables.contains(el));
  }


  private static final class ExtractCommonIfPartsFix extends PsiUpdateModCommandQuickFix {
    private final CommonPartType myType;
    private final boolean myMayChangeSemantics;
    private final boolean myIsOnTheFly;


    private ExtractCommonIfPartsFix(CommonPartType type,
                                    boolean mayChangeSemantics,
                                    boolean isOnTheFly) {
      myType = type;
      myMayChangeSemantics = mayChangeSemantics;
      myIsOnTheFly = isOnTheFly;
    }

    @Override
    public @NotNull String getFamilyName() {
      return JavaAnalysisBundle.message("inspection.common.if.parts.family");
    }

    @Override
    public @NotNull String getName() {
      return myType.getFixMessage(myMayChangeSemantics);
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiIfStatement ifStatement = PsiTreeUtil.getParentOfType(element, PsiIfStatement.class, false);
      if (ifStatement == null) return;
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      if (!(ifStatement.getParent() instanceof PsiCodeBlock)) {
        ifStatement = BlockUtils.expandSingleStatementToBlockStatement(ifStatement);
      }
      PsiStatement[] thenStatements = unwrap(ifStatement.getThenBranch());
      PsiStatement[] elseStatements = unwrap(ifStatement.getElseBranch());
      if (tryApplyThenElseFix(ifStatement, factory, thenStatements, elseStatements)) return;
      applyImplicitElseFix(ifStatement, thenStatements, elseStatements);
    }

    private static void applyImplicitElseFix(PsiIfStatement ifStatement, PsiStatement[] thenStatements, PsiStatement[] elseStatements) {
      ImplicitElse implicitElse = ImplicitElse.from(thenStatements, elseStatements, ifStatement);
      if (implicitElse == null) return;
      PsiIfStatement ifToDelete = implicitElse.myIfToDelete;
      CommentTracker ct = new CommentTracker();
      PsiElement parent = ifToDelete.getParent();
      if(ifToDelete == ifStatement) { // Only in this case condition may contains side effect
        PsiExpression condition = ifToDelete.getCondition();
        if(condition == null) return;
        ct.markUnchanged(condition);
        List<PsiExpression> sideEffectExpressions = SideEffectChecker.extractSideEffectExpressions(condition);
        PsiStatement[] sideEffectStatements = StatementExtractor.generateStatements(sideEffectExpressions, condition);
        for (int statementIndex = sideEffectStatements.length - 1; statementIndex >= 0; statementIndex--) {
          PsiStatement statement = sideEffectStatements[statementIndex];
          parent.addAfter(statement, ifToDelete);
        }
      }
      ct.deleteAndRestoreComments(ifToDelete);
    }

    private boolean tryApplyThenElseFix(PsiIfStatement ifStatement,
                                        PsiElementFactory factory,
                                        PsiStatement[] thenStatements,
                                        PsiStatement[] elseStatements) {
      ThenElse thenElse = ThenElse.from(ifStatement, thenStatements, elseStatements, myIsOnTheFly);
      if (thenElse == null) return false;
      PsiStatement thenBranch = ifStatement.getThenBranch();
      PsiStatement elseBranch = ifStatement.getElseBranch();
      if (thenBranch == null || elseBranch == null) return false;
      CommentTracker ct = new CommentTracker();
      if (!tryCleanUpHead(ifStatement, thenElse.myHeadUnitsOfThen, factory, thenElse.mySubstitutionTable, ct)) return true;
      cleanUpTail(ifStatement, thenElse.myTailStatementsOfThen, ct);
      boolean elseToDelete = ControlFlowUtils.unwrapBlock(ifStatement.getElseBranch()).length == 0;
      boolean thenToDelete = ControlFlowUtils.unwrapBlock(ifStatement.getThenBranch()).length == 0;
      ct.insertCommentsBefore(ifStatement);
      if (thenToDelete && elseToDelete) {
        ifStatement.delete();
      }
      else if(thenElse.myCommonPartType != CommonPartType.WHOLE_BRANCH) {
        // it is possible when one branch can be removed but it contains comments
        return true;
      }
      else if (elseToDelete) {
        elseBranch.delete();
      }
      else if (thenToDelete) {
        PsiExpression condition = ifStatement.getCondition();
        if (condition == null) return true;
        String negatedCondition = BoolUtils.getNegatedExpressionText(condition);
        String newThenBranch = elseBranch.getText();
        ifStatement.replace(factory.createStatementFromText("if(" + negatedCondition + ")" + newThenBranch, ifStatement));
      }
      return true;
    }

    private static boolean tryCleanUpHead(@NotNull PsiIfStatement ifStatement,
                                          @NotNull List<? extends ExtractionUnit> units,
                                          @NotNull PsiElementFactory factory,
                                          @NotNull Map<PsiLocalVariable, String> substitutionTable,
                                          @NotNull CommentTracker ct) {
      // collect replacement info
      Map<PsiReferenceExpression, String> referenceToNewName = new HashMap<>();
      Map<PsiLocalVariable, String> variableToNewName = new HashMap<>();
      for (ExtractionUnit unit : units) {
        if (unit instanceof VariableDeclarationUnit declarationUnit) {
          PsiLocalVariable thenVariable = declarationUnit.myThenVariable;
          PsiLocalVariable elseVariable = declarationUnit.myElseVariable;
          String baseName = substitutionTable.get(elseVariable);
          JavaCodeStyleManager manager = JavaCodeStyleManager.getInstance(ifStatement.getProject());
          if (baseName != null) {
            Predicate<PsiVariable> canReuseVariable = var -> PsiTreeUtil.isAncestor(ifStatement, var, false);
            String nameToBeReplacedWith = manager.suggestUniqueVariableName(baseName, ifStatement, canReuseVariable);
            for (PsiReference reference : ReferencesSearch.search(thenVariable, new LocalSearchScope(ifStatement)).asIterable()) {
              PsiReferenceExpression expression = (PsiReferenceExpression)reference.getElement();
              referenceToNewName.put(expression, nameToBeReplacedWith);
            }
            for (PsiReference reference : ReferencesSearch.search(elseVariable, new LocalSearchScope(ifStatement)).asIterable()) {
              PsiReferenceExpression expression = (PsiReferenceExpression)reference.getElement();
              referenceToNewName.put(expression, nameToBeReplacedWith);
            }
            variableToNewName.put(thenVariable, nameToBeReplacedWith);
            variableToNewName.put(elseVariable, nameToBeReplacedWith);
            declarationUnit.myNewVariableName = nameToBeReplacedWith;
          } else {
            declarationUnit.myNewVariableName = thenVariable.getName();
          }
        }
      }

      // do actual replace (can't be done in collection loop, because it can affect resolve)
      referenceToNewName.forEach((reference, newName) -> ExpressionUtils.bindReferenceTo(reference, newName));
      variableToNewName.forEach((variable, newName) -> variable.setName(newName));

      PsiElement parent = ifStatement.getParent();
      for (ExtractionUnit unit : units) {
        PsiStatement statement = unit.getStatementToPutBeforeIf(factory, ifStatement);
        parent.addBefore(statement, ifStatement);
        unit.cleanup(ct, factory, ifStatement);
      }
      return true;
    }

    private static void cleanUpTail(@NotNull PsiIfStatement ifStatement,
                                    @NotNull List<? extends PsiStatement> tailStatements,
                                    CommentTracker ct) {
      if (!tailStatements.isEmpty()) {
        for (PsiStatement statement : tailStatements) {
          ifStatement.getParent().addAfter(statement.copy(), ifStatement);
        }
        PsiStatement[] thenStatements = ControlFlowUtils.unwrapBlock(ifStatement.getThenBranch());
        PsiStatement[] elseStatements = ControlFlowUtils.unwrapBlock(ifStatement.getElseBranch());
        for (int i = thenStatements.length - 1; i >= 0; i--) {
          PsiStatement statement = thenStatements[i];
          if (!(statement instanceof PsiEmptyStatement)) break;
          //emptyStatementCount++;
        }
        if (thenStatements.length == 1 && thenStatements[0].getParent() == ifStatement) {
          thenStatements[0].replace(JavaPsiFacade.getElementFactory(ifStatement.getProject()).createCodeBlock());
        }
        else {
          deleteStatements(thenStatements, tailStatements.size(), ct, false);
        }
        deleteStatements(elseStatements, tailStatements.size(), ct, true);
      }
    }

    private static void deleteStatements(PsiStatement[] statements, int count, CommentTracker ct, boolean keepComments) {
      for (int i = statements.length - 1; i >= 0; i--) {
        PsiStatement statement = statements[i];
        if (statement instanceof PsiEmptyStatement) {
          ct.delete(statement);
        }
        else if (count > 0) {
          count--;
          if (keepComments) {
            ct.delete(statement);
          }
          else {
            statement.delete();
          }
        }
        else {
          break;
        }
      }
    }
  }

  @Contract(pure = true)
  private static @Nullable PsiLocalVariable extractVariable(@Nullable PsiStatement statement) {
    PsiDeclarationStatement declarationStatement = tryCast(statement, PsiDeclarationStatement.class);
    if (declarationStatement == null) return null;
    PsiElement[] elements = declarationStatement.getDeclaredElements();
    if (elements.length != 1) return null;
    return tryCast(elements[0], PsiLocalVariable.class);
  }

  /**
   * Unit of equivalence, represents pair of equivalent statements in if/else branches.
   *
   * Main part to preserve during replacement is, by convention, from `then` branch.
   */
  private static class ExtractionUnit {
    private final boolean myMayChangeSemantics;
    private final boolean myMayInfluenceCondition;
    private final @NotNull PsiStatement myThenStatement;
    private final @NotNull PsiStatement myElseStatement;
    private final boolean myIsEquivalent;

    private ExtractionUnit(@NotNull PsiStatement thenStatement,
                           @NotNull PsiStatement elseStatement,
                           boolean mayChangeSemantics,
                           boolean mayInfluenceCondition,
                           boolean isEquivalent) {
      myMayChangeSemantics = mayChangeSemantics;
      myThenStatement = thenStatement;
      myElseStatement = elseStatement;
      myMayInfluenceCondition = mayInfluenceCondition;
      myIsEquivalent = isEquivalent;
    }

    boolean haveSideEffects() {
      return myMayChangeSemantics;
    }

    @NotNull
    PsiStatement getThenStatement() {
      return myThenStatement;
    }

    @NotNull
    PsiStatement getElseStatement() {
      return myElseStatement;
    }

    /**
     * Can return false only if it is declaration statement and initializers are not equivalent
     *
     * @return true if pair of statements in both branches are equivalent in terms of {@link LocalEquivalenceChecker }
     */
    boolean isEquivalent() {
      return myIsEquivalent;
    }

    /**
     * @return true if this statement may somehow change variables, that are used in condition if put this statement before condition
     */
    boolean mayInfluenceCondition() {
      return myMayInfluenceCondition;
    }

    @NotNull
    PsiStatement getStatementToPutBeforeIf(PsiElementFactory factory, @NotNull PsiIfStatement ifStatement) {
      return (PsiStatement)myThenStatement.copy();
    }

    void cleanup(@NotNull CommentTracker ct, PsiElementFactory factory, PsiIfStatement ifStatement) {
      myThenStatement.delete(); // Intentionally do not preserve comments in one branch
      ct.delete(myElseStatement);
    }
  }

  private static final class VariableDeclarationUnit extends ExtractionUnit {
    final @NotNull PsiLocalVariable myThenVariable;
    final @NotNull PsiLocalVariable myElseVariable;
    String myNewVariableName; // must be set when

    private VariableDeclarationUnit(@NotNull PsiStatement thenStatement,
                                    @NotNull PsiStatement elseStatement,
                                    boolean mayChangeSemantics,
                                    boolean mayInfluenceCondition,
                                    boolean isEquivalent,
                                    @NotNull PsiLocalVariable thenVariable,
                                    @NotNull PsiLocalVariable elseVariable) {
      super(thenStatement, elseStatement, mayChangeSemantics, mayInfluenceCondition, isEquivalent);
      myThenVariable = thenVariable;
      myElseVariable = elseVariable;
    }

    /**
     * @return variable without initializer in case when other variable has initializer
     */
    @Nullable PsiVariable getVariableWithoutInitializer() {
      if (myThenVariable.hasInitializer()) {
        if (!myElseVariable.hasInitializer()) {
          return myElseVariable;
        }
      } else {
        if (!myElseVariable.hasInitializer()) {
          return myThenVariable;
        }
      }
      return null;
    }

    @Override
    void cleanup(@NotNull CommentTracker ct, PsiElementFactory factory, PsiIfStatement ifStatement) {
      if (isEquivalent()) {
        super.cleanup(ct, factory, ifStatement);
        return;
      }
      String type = myThenVariable.getType().getCanonicalText();
      PsiExpression thenInitializer = myThenVariable.getInitializer();
      PsiStatement thenAssignment = createAssignment(thenInitializer, type, myNewVariableName, ifStatement, factory);
      if (thenAssignment != null) {
        getThenStatement().replace(thenAssignment);
      }
      PsiExpression elseInitializer = myElseVariable.getInitializer();
      PsiStatement elseAssignment = createAssignment(elseInitializer, type, myNewVariableName, ifStatement, factory);
      if (elseAssignment != null) {
        ct.replace(getElseStatement(), elseAssignment);
      }
    }

    private static PsiStatement createAssignment(PsiExpression initializer,
                                                 String type,
                                                 String varName,
                                                 PsiIfStatement ifStatement,
                                                 PsiElementFactory factory) {
      if (initializer == null) return null;
      final String initializerText;
      if (initializer instanceof PsiArrayInitializerExpression) {
        initializerText = "new " + type + initializer.getText();
      }
      else {
        initializerText = initializer.getText();
      }
      return factory.createStatementFromText(varName + "=" + initializerText + ";", ifStatement);
    }

    @NotNull
    @Override
    PsiStatement getStatementToPutBeforeIf(PsiElementFactory factory, @NotNull PsiIfStatement ifStatement) {
      if (isEquivalent()) {
        return getThenStatement();
      }
      String thenVariableTypeText = myThenVariable.getType().getCanonicalText();
      PsiModifierList thenModifierList = myThenVariable.getModifierList();
      String modifiers = thenModifierList == null || thenModifierList.getText().isEmpty() ? "" : thenModifierList.getText() + " ";
      return factory.createStatementFromText(modifiers + thenVariableTypeText + " " + myNewVariableName + ";", ifStatement.getParent());
    }
  }


  private enum CommonPartType {
    VARIABLES_ONLY("inspection.common.if.parts.message.variables.only", "inspection.common.if.parts.description.variables.only"),
    WITH_VARIABLES_EXTRACT("inspection.common.if.parts.message.with.variables.extract",
                           "inspection.common.if.parts.description.with.variables.extract"),
    WITHOUT_VARIABLES_EXTRACT("inspection.common.if.parts.message.without.variables.extract",
                              "inspection.common.if.parts.description.without.variables.extract"),
    WHOLE_BRANCH("inspection.common.if.parts.message.whole.branch", "inspection.common.if.parts.description.whole.branch"),
    COMPLETE_DUPLICATE("inspection.common.if.parts.message.complete.duplicate",
                       "inspection.common.if.parts.description.complete.duplicate"),
    EXTRACT_SIDE_EFFECTS("inspection.common.if.parts.message.complete.duplicate.side.effect",
                         "inspection.common.if.parts.description.complete.duplicate.side.effect");

    private final @PropertyKey(resourceBundle = InspectionGadgetsBundle.BUNDLE) @NotNull String myBundleFixKey;
    private final @PropertyKey(resourceBundle = InspectionGadgetsBundle.BUNDLE) @NotNull String myBundleDescriptionKey;

    private @NotNull @IntentionName String getFixMessage(boolean mayChangeSemantics) {
      return InspectionGadgetsBundle.message(myBundleFixKey, getMayChangeSemanticsText(mayChangeSemantics));
    }

    private static @NotNull String getMayChangeSemanticsText(boolean mayChangeSemantics) {
      return mayChangeSemantics ? " (" + InspectionGadgetsBundle.message("inspection.note.may.change.semantics") + ")" : "";
    }

    private @NotNull @InspectionMessage String getDescriptionMessage(boolean mayChangeSemantics) {
      return InspectionGadgetsBundle.message(myBundleDescriptionKey, getMayChangeSemanticsText(mayChangeSemantics));
    }

    CommonPartType(@PropertyKey(resourceBundle = InspectionGadgetsBundle.BUNDLE) @NotNull String key,
                   @PropertyKey(resourceBundle = InspectionGadgetsBundle.BUNDLE) @NotNull String bundleDescriptionKey) {
      myBundleFixKey = key;
      myBundleDescriptionKey = bundleDescriptionKey;
    }
  }

  private static @Nullable PsiIfStatement getEnclosingIfStmt(@NotNull PsiIfStatement ifStatement) {
    PsiElement parent = ifStatement.getParent();
    if (parent instanceof PsiIfStatement) {
      return (PsiIfStatement)parent;
    }
    if (parent instanceof PsiCodeBlock) {
      if (((PsiCodeBlock)parent).getStatements()[0] != ifStatement) return null;
      return tryCast(parent.getParent().getParent(), PsiIfStatement.class);
    }
    return null;
  }

  private static boolean isMeaningful(@NotNull PsiStatement statement) {
    if (statement instanceof PsiEmptyStatement) return false;
    if (statement instanceof PsiBlockStatement) {
      return !((PsiBlockStatement)statement).getCodeBlock().isEmpty();
    }
    return true;
  }

  private static final class ImplicitElseData {
    final @NotNull List<PsiStatement> myImplicitElseStatements;
    final @NotNull PsiIfStatement myIfWithImplicitElse;

    private ImplicitElseData(@NotNull List<PsiStatement> implicitElseStatements, @NotNull PsiIfStatement ifWithImplicitElse) {
      myImplicitElseStatements = implicitElseStatements;
      myIfWithImplicitElse = ifWithImplicitElse;
    }
  }

  /**
   * detects
   * if(c1) {
   *   if(c2) {
   *     ...commonStatements
   *   }
   * }
   * ...commonStatements
   */
  private static @Nullable ImplicitElseData getIfWithImplicitElse(@NotNull PsiIfStatement ifStatement,
                                                        PsiStatement @NotNull [] thenStatements,
                                                        String jumpKeyword, boolean basicJumpStatement) {
    if (!JavaKeywords.RETURN.equals(jumpKeyword) && !JavaKeywords.CONTINUE.equals(jumpKeyword)) return null;
    int statementsLength = thenStatements.length;
    if (statementsLength == 0) return null;
    PsiIfStatement currentIf = ifStatement;
    List<PsiStatement> statements = new ArrayList<>();
    int count = 0;
    boolean conditionHasSideEffects = false;
    while (true) {
      if (currentIf.getElseBranch() != null) return null;
      if (currentIf != ifStatement && ControlFlowUtils.unwrapBlock(currentIf.getThenBranch()).length != 1) break;
      if(currentIf.getCondition() != null && SideEffectChecker.mayHaveSideEffects(currentIf.getCondition())) {
        conditionHasSideEffects = true;
      }
      PsiStatement sibling = currentIf;
      do {
        sibling = PsiTreeUtil.getNextSiblingOfType(sibling, PsiStatement.class);
        if (sibling == null) break;
        if (!isMeaningful(sibling)) continue;
        count++;
        statements.add(sibling);
      }
      while (count <= statementsLength);
      if (!statements.isEmpty()) break;

      PsiIfStatement enclosingIf = getEnclosingIfStmt(currentIf);
      if (enclosingIf == null) break;
      currentIf = enclosingIf;
    }
    if (conditionHasSideEffects && ifStatement != currentIf) return null;
    // ensure it is last statements in method
    PsiElement parent = currentIf.getParent();
    if (!(parent instanceof PsiCodeBlock)) return null;
    if (JavaKeywords.RETURN.equals(jumpKeyword)) {
      if (!(parent.getParent() instanceof PsiMethod)) return null;
      if (!statements.isEmpty()) {
        if (PsiTreeUtil.getNextSiblingOfType(statements.get(statements.size() - 1), PsiStatement.class) != null) return null;
      }
    }
    if (JavaKeywords.CONTINUE.equals(jumpKeyword) && !(parent.getParent() instanceof PsiBlockStatement) && !(parent.getParent().getParent() instanceof PsiLoopStatement)) return null;
    if (basicJumpStatement) {
      // skip possible jump statement
      if (count == statementsLength || count == statementsLength - 1) return new ImplicitElseData(statements, currentIf);
    }
    else if (count == statementsLength) {
      return new ImplicitElseData(statements, currentIf);
    }
    return null;
  }

  private static void addLocalVariables(Set<? super PsiLocalVariable> variables, List<? extends PsiStatement> statements) {
    for (PsiStatement statement : statements) {
      addVariables(variables, statement);
    }
  }

  private static void addVariables(Set<? super PsiLocalVariable> variables, PsiStatement statement) {
    PsiDeclarationStatement declarationStatement = tryCast(statement, PsiDeclarationStatement.class);
    if (declarationStatement == null) return;
    for (PsiElement element : declarationStatement.getDeclaredElements()) {
      if (element instanceof PsiLocalVariable) {
        variables.add((PsiLocalVariable)element);
      }
    }
  }

  private static PsiStatement @NotNull [] unwrap(@Nullable PsiStatement statement) {
    PsiBlockStatement block = tryCast(statement, PsiBlockStatement.class);
    if (block != null) {
      return ContainerUtil.filter(block.getCodeBlock().getStatements(), IfStatementWithIdenticalBranchesInspection::isMeaningful)
        .toArray(PsiStatement.EMPTY_ARRAY);
    }
    return statement == null ? PsiStatement.EMPTY_ARRAY : new PsiStatement[]{statement};
  }

  private static final class ImplicitElse {
    final @NotNull PsiIfStatement myIfToDelete;

    private ImplicitElse(@NotNull PsiIfStatement ifToDelete) {
      myIfToDelete = ifToDelete;
    }

    static @Nullable ImplicitElse from(PsiStatement @NotNull [] thenBranch,
                                       PsiStatement @NotNull [] elseBranch,
                                       @NotNull PsiIfStatement ifStatement) {
      if (elseBranch.length != 0 || thenBranch.length == 0) return null;
      PsiStatement lastThenStatement = thenBranch[thenBranch.length - 1];
      String jumpKeyword = null;
      boolean basicJumpStatement = false;
      if (lastThenStatement instanceof PsiReturnStatement) {
        jumpKeyword = JavaKeywords.RETURN;
        basicJumpStatement = ((PsiReturnStatement)lastThenStatement).getReturnValue() == null;
      } else if (lastThenStatement instanceof PsiContinueStatement) {
        jumpKeyword = JavaKeywords.CONTINUE;
        basicJumpStatement = ((PsiContinueStatement)lastThenStatement).getLabelIdentifier() == null;
      }
      if (jumpKeyword == null) return null;
      ImplicitElseData implicitElse = getIfWithImplicitElse(ifStatement, thenBranch, jumpKeyword, basicJumpStatement);
      if (implicitElse == null) return null;
      if (implicitElse.myImplicitElseStatements.isEmpty()) return null;
      if (implicitElse.myImplicitElseStatements.size() == 1) {
        PsiStatement statement = implicitElse.myImplicitElseStatements.get(0);
        if (statement instanceof PsiReturnStatement) {
          if (((PsiReturnStatement)statement).getReturnValue() == null) return null;
        }
      }
      List<PsiStatement> elseStatements = implicitElse.myImplicitElseStatements;
      Set<PsiLocalVariable> variables = new HashSet<>();
      List<PsiStatement> thenStatements = new ArrayList<>(Arrays.asList(thenBranch));
      addLocalVariables(variables, thenStatements);
      addLocalVariables(variables, implicitElse.myImplicitElseStatements);
      if (!branchesAreEquivalent(thenBranch, elseStatements, new LocalEquivalenceChecker(variables))) return null;
      return new ImplicitElse(implicitElse.myIfWithImplicitElse);
    }

    CommonPartType getType() {
      PsiExpression condition = myIfToDelete.getCondition();
      if(condition != null) {
        if (SideEffectChecker.mayHaveSideEffects(condition)) {
          return CommonPartType.EXTRACT_SIDE_EFFECTS;
        }
      }
      return CommonPartType.COMPLETE_DUPLICATE;
    }

    static @Nullable IfInspectionResult inspect(@NotNull PsiIfStatement ifStatement,
                                                PsiStatement @NotNull [] thenBranch,
                                                PsiStatement @NotNull [] elseBranch,
                                                boolean isOnTheFly,
                                                IfStatementWithIdenticalBranchesInspection inspection) {
      ImplicitElse implicitElse = from(thenBranch, elseBranch, ifStatement);
      if (implicitElse == null) return null;
      CommonPartType type = implicitElse.getType();
      ExtractCommonIfPartsFix fix = new ExtractCommonIfPartsFix(type, false, isOnTheFly);
      return new IfInspectionResult(ifStatement.getFirstChild(), true, fix, type.getDescriptionMessage(false));
    }
  }

  private static @NotNull LocalEquivalenceChecker getChecker(PsiStatement[] thenBranch, PsiStatement[] elseBranch) {
    Set<PsiLocalVariable> localVariables = new HashSet<>();
    addLocalVariables(localVariables, Arrays.asList(thenBranch));
    addLocalVariables(localVariables, Arrays.asList(elseBranch));
    return new LocalEquivalenceChecker(localVariables);
  }

  private static final class ThenElse {
    // count of statements required to consider branch consists of similar statements
    public static final int SIMILAR_STATEMENTS_COUNT = 2;
    final List<ExtractionUnit> myHeadUnitsOfThen;
    final List<PsiStatement> myTailStatementsOfThen;
    final boolean myMayChangeSemantics;
    final CommonPartType myCommonPartType;
    final Map<PsiLocalVariable, String> mySubstitutionTable;

    private ThenElse(List<ExtractionUnit> headUnitsOfThen,
                     List<PsiStatement> tailStatementsOfThen,
                     boolean mayChangeSemantics,
                     CommonPartType commonPartType,
                     Map<PsiLocalVariable, String> substitutionTable) {
      myHeadUnitsOfThen = headUnitsOfThen;
      myTailStatementsOfThen = tailStatementsOfThen;
      myMayChangeSemantics = mayChangeSemantics;
      myCommonPartType = commonPartType;
      mySubstitutionTable = substitutionTable;
    }

    boolean variableRenameRequired() {
      return ContainerUtil.or(myHeadUnitsOfThen, unit -> {
        return unit instanceof VariableDeclarationUnit declarationUnit &&
               !Objects.equals(declarationUnit.myThenVariable.getName(), declarationUnit.myElseVariable.getName());
      });
    }


    private static boolean mayChangeSemantics(boolean conditionHasSideEffects,
                                              boolean conditionVariablesCantBeChangedTransitively,
                                              List<? extends ExtractionUnit> headCommonParts) {
      if (headCommonParts.isEmpty()) return false;
      if (conditionHasSideEffects) return true;
      if (conditionVariablesCantBeChangedTransitively) {
        return ContainerUtil.or(headCommonParts, unit -> unit.mayInfluenceCondition() &&
                                                         !(unit.getThenStatement() instanceof PsiDeclarationStatement));
      }
      return ContainerUtil
        .or(headCommonParts, unit -> unit.haveSideEffects() && !(unit.getThenStatement() instanceof PsiDeclarationStatement));
    }

    private static @NotNull CommonPartType getType(@NotNull List<? extends ExtractionUnit> headStatements,
                                                   List<? extends PsiStatement> tailStatements,
                                                   boolean declarationsAreEquivalent,
                                                   int thenLen,
                                                   int elseLen,
                                                   @NotNull PsiStatement thenBranch,
                                                   @NotNull PsiStatement elseBranch) {
      if (declarationsAreEquivalent) {
        int duplicatedStatements = headStatements.size() + tailStatements.size();
        if (thenLen == duplicatedStatements && elseLen == duplicatedStatements) {
          return CommonPartType.COMPLETE_DUPLICATE;
        }
        if (canRemoveBranch(thenLen, thenBranch, duplicatedStatements)) return CommonPartType.WHOLE_BRANCH;
        if (canRemoveBranch(elseLen, elseBranch, duplicatedStatements)) return CommonPartType.WHOLE_BRANCH;
      }
      boolean hasVariables = false;
      boolean hasNonVariables = false;
      for (ExtractionUnit unit : headStatements) {
        if (unit.getThenStatement() instanceof PsiDeclarationStatement) {
          hasVariables = true;
        }
        else {
          hasNonVariables = true;
        }
        if (hasVariables && hasNonVariables) break;
      }
      if (!(hasVariables && hasNonVariables)) {
        for (PsiStatement statement : tailStatements) {
          if (statement instanceof PsiDeclarationStatement) {
            hasVariables = true;
          }
          else {
            hasNonVariables = true;
          }
          if (hasVariables && hasNonVariables) break;
        }
      }
      if (hasVariables && hasNonVariables) {
        return CommonPartType.WITH_VARIABLES_EXTRACT;
      }
      if (hasVariables) {
        return CommonPartType.VARIABLES_ONLY;
      }
      return CommonPartType.WITHOUT_VARIABLES_EXTRACT;
    }

    private static boolean canRemoveBranch(int len, PsiStatement branch, int duplicatedStatementsLen) {
      if (len == duplicatedStatementsLen) {
        PsiBlockStatement blockStatement = tryCast(branch, PsiBlockStatement.class);
        return blockStatement != null && PsiTreeUtil.getChildOfType(blockStatement.getCodeBlock(), PsiComment.class) == null;
      }
      return false;
    }

    static @Nullable ThenElse from(@NotNull PsiIfStatement ifStatement,
                                   PsiStatement @NotNull [] thenBranch,
                                   PsiStatement @NotNull [] elseBranch,
                                   boolean isOnTheFly) {
      LocalEquivalenceChecker equivalence = getChecker(thenBranch, elseBranch);

      int thenLen = thenBranch.length;
      int elseLen = elseBranch.length;
      int minStmtCount = Math.min(thenLen, elseLen);

      PsiExpression condition = ifStatement.getCondition();
      if (condition == null) return null;

      boolean conditionHasSideEffects = SideEffectChecker.mayHaveSideEffects(condition);
      if (!isOnTheFly && conditionHasSideEffects) return null;
      List<PsiLocalVariable> conditionVariables = new ArrayList<>();
      boolean conditionVariablesCantBeChangedTransitively = StreamEx.ofTree((PsiElement)condition, el -> StreamEx.of(el.getChildren()))
        .allMatch(element -> {
          if (!(element instanceof PsiReferenceExpression)) {
            return !(element instanceof PsiMethodCallExpression);
          }
          PsiLocalVariable localVariable = tryCast(((PsiReferenceExpression)element).resolve(), PsiLocalVariable.class);
          if (localVariable == null) return false;
          conditionVariables.add(localVariable);
          return true;
        });

      List<ExtractionUnit> headCommonParts = new ArrayList<>();
      Set<PsiVariable> extractedVariables = new HashSet<>();
      Set<PsiVariable> notEquivalentVariableDeclarations = new HashSet<>(0);

      extractHeadCommonParts(thenBranch, elseBranch, isOnTheFly, equivalence, minStmtCount, conditionVariables, headCommonParts,
                             extractedVariables, notEquivalentVariableDeclarations);
      if (headCommonParts.isEmpty()) {
        // when head common parts contains e.g. first variable with different names but changes semantics and batch mode is on
        equivalence.mySubstitutionTable.clear();
      }

      // set of variables which have in one branch initializer, but in other branch doesn't have initializer
      final Set<PsiVariable> differentDeclarationsWithoutInitializers = StreamEx.of(headCommonParts)
        .select(VariableDeclarationUnit.class)
        .map(unit -> unit.getVariableWithoutInitializer())
        .nonNull()
        .toSet();

      int extractedFromStart = headCommonParts.size();
      int canBeExtractedFromThenTail = thenLen - extractedFromStart;
      int canBeExtractedFromElseTail = elseLen - extractedFromStart;
      int canBeExtractedFromTail = Math.min(canBeExtractedFromThenTail, canBeExtractedFromElseTail);
      List<PsiStatement> tailCommonParts = new ArrayList<>();
      extractTailCommonParts(ifStatement, thenBranch, elseBranch, equivalence, thenLen, elseLen, extractedVariables, canBeExtractedFromTail,
                             tailCommonParts, differentDeclarationsWithoutInitializers);

      tryAppendHeadPartsToTail(headCommonParts, canBeExtractedFromThenTail, canBeExtractedFromElseTail, canBeExtractedFromTail,
                               tailCommonParts);

      Map<PsiLocalVariable, String> substitutionTable = equivalence.mySubstitutionTable;
      if (uncommonElseStatementsContainsThenNames(elseBranch, elseLen, headCommonParts, tailCommonParts, substitutionTable)) {
        return null;
      }
      if (headCommonParts.isEmpty() && tailCommonParts.isEmpty()) return null;
      PsiStatement thenStatement = ifStatement.getThenBranch();
      PsiStatement elseStatement = ifStatement.getElseBranch();
      if (thenStatement == null || elseStatement == null) return null;
      final CommonPartType type = getType(headCommonParts, tailCommonParts, notEquivalentVariableDeclarations.isEmpty(), thenLen, elseLen,
                                          thenStatement, elseStatement);
      if (type == CommonPartType.VARIABLES_ONLY && !notEquivalentVariableDeclarations.isEmpty()) return null;
      boolean mayChangeSemantics =
        mayChangeSemantics(conditionHasSideEffects, conditionVariablesCantBeChangedTransitively, headCommonParts);
      return new ThenElse(headCommonParts, tailCommonParts, mayChangeSemantics, type, substitutionTable);
    }

    private static boolean isSingleCallTail(List<PsiStatement> tail) {
      if (tail.size() != 1) return false;
      PsiExpressionStatement expressionStatement = tryCast(tail.get(0), PsiExpressionStatement.class);
      if (expressionStatement == null) return false;
      PsiMethodCallExpression call = tryCast(expressionStatement.getExpression(), PsiMethodCallExpression.class);
      return call != null;
    }


    static @Nullable IfInspectionResult inspect(@NotNull PsiIfStatement ifStatement,
                                                PsiStatement @NotNull [] thenBranch,
                                                PsiStatement @NotNull [] elseBranch,
                                                boolean isOnTheFly,
                                                IfStatementWithIdenticalBranchesInspection inspection) {
      ThenElse thenElse = from(ifStatement, thenBranch, elseBranch, isOnTheFly);
      if (thenElse == null) return null;
      boolean isNotInCodeBlock = !(ifStatement.getParent() instanceof PsiCodeBlock);
      boolean mayChangeSemantics = thenElse.myMayChangeSemantics;
      CommonPartType type = thenElse.myCommonPartType;
      ExtractCommonIfPartsFix fix = new ExtractCommonIfPartsFix(type, mayChangeSemantics, isOnTheFly);
      boolean tailStatementIsSingleCall = !inspection.myHighlightWhenLastStatementIsCall
                                          && (thenBranch.length > 1 || elseBranch.length > 1)
                                          && isSingleCallTail(thenElse.myTailStatementsOfThen)
                                          && thenElse.myHeadUnitsOfThen.isEmpty();
      boolean isInfoLevel = mayChangeSemantics
                            || isNotInCodeBlock
                            || isVariableTypeWithRename(thenElse, type)
                            || tailStatementIsSingleCall;
      PsiElement elementToHighlight = isInfoLevel ? ifStatement : ifStatement.getFirstChild();
      if (type == CommonPartType.VARIABLES_ONLY && !isOnTheFly) return null;
      return new IfInspectionResult(elementToHighlight, !isInfoLevel, fix, type.getDescriptionMessage(mayChangeSemantics));
    }

    private static boolean isVariableTypeWithRename(ThenElse thenElse, CommonPartType type) {
      return (type == CommonPartType.WITH_VARIABLES_EXTRACT || type == CommonPartType.VARIABLES_ONLY) && thenElse.variableRenameRequired();
    }

    private static void tryAppendHeadPartsToTail(List<? extends ExtractionUnit> headCommonParts,
                                                 int canBeExtractedFromThenTail,
                                                 int canBeExtractedFromElseTail,
                                                 int canBeExtractedFromTail,
                                                 List<? super PsiStatement> tailCommonParts) {
      if (canBeExtractedFromTail == tailCommonParts.size() && canBeExtractedFromElseTail == canBeExtractedFromThenTail) {
        // trying to append to tail statements, that may change semantics from head, because in tail they can't change semantics
        for (int i = headCommonParts.size() - 1; i >= 0; i--) {
          ExtractionUnit unit = headCommonParts.get(i);
          PsiStatement thenStatement = unit.getThenStatement();
          if (!unit.haveSideEffects() || !unit.isEquivalent()) break;
          headCommonParts.remove(i);
          tailCommonParts.add(thenStatement);
        }
      }
    }

    private static void extractTailCommonParts(@NotNull PsiIfStatement ifStatement,
                                               PsiStatement @NotNull [] thenBranch,
                                               PsiStatement @NotNull [] elseBranch,
                                               LocalEquivalenceChecker equivalence,
                                               int thenLen,
                                               int elseLen,
                                               Set<PsiVariable> extractedVariables,
                                               int canBeExtractedFromTail,
                                               List<? super PsiStatement> tailCommonParts,
                                               Set<PsiVariable> differentDeclarationsWithoutInitializers) {
      if (isSimilarTailStatements(thenBranch)) return;
      for (int i = 0; i < canBeExtractedFromTail; i++) {
        PsiStatement thenStmt = thenBranch[thenLen - i - 1];
        PsiStatement elseStmt = elseBranch[elseLen - i - 1];

        // Check that if we put our statement out of if, no var declaration will be uninitialized
        if (!differentDeclarationsWithoutInitializers.isEmpty()) {
          boolean willUseUninitializedVar = StreamEx.of(thenStmt, elseStmt)
            .flatMap(statement -> StreamEx.ofTree((PsiElement)statement, element -> StreamEx.of(element.getChildren())))
            .select(PsiReferenceExpression.class)
            .map(expression -> expression.resolve())
            .select(PsiVariable.class)
            .anyMatch(element -> differentDeclarationsWithoutInitializers.contains(element));
          if (willUseUninitializedVar) break;
        }
        if (!equivalence.statementsAreEquivalent(thenStmt, elseStmt)) break;
        boolean canBeExtractedOutOfIf = VariableAccessUtils.collectUsedVariables(thenStmt).stream()
          .filter(var -> var instanceof PsiLocalVariable)
          .filter(var -> PsiTreeUtil.isAncestor(ifStatement, var, false))
          .allMatch(var -> extractedVariables.contains(var));
        if (!canBeExtractedOutOfIf) break;
        tailCommonParts.add(thenStmt);
      }
    }

    private static void extractHeadCommonParts(PsiStatement @NotNull [] thenBranch,
                                               PsiStatement @NotNull [] elseBranch,
                                               boolean isOnTheFly,
                                               LocalEquivalenceChecker equivalence,
                                               int minStmtCount,
                                               List<PsiLocalVariable> conditionVariables,
                                               List<? super ExtractionUnit> headCommonParts,
                                               Set<? super PsiVariable> extractedVariables,
                                               Set<? super PsiVariable> notEquivalentVariableDeclarations) {
      if (!isSimilarHeadStatements(thenBranch)) {
        for (int i = 0; i < minStmtCount; i++) {
          PsiStatement thenStmt = thenBranch[i];
          PsiStatement elseStmt = elseBranch[i];
          ExtractionUnit unit = extractHeadCommonStatement(thenStmt, elseStmt, conditionVariables, equivalence);
          if (unit == null) break;
          if (!isOnTheFly && unit.haveSideEffects()) break;
          boolean dependsOnVariableWithNonEquivalentInitializer = VariableAccessUtils.collectUsedVariables(thenStmt).stream()
            .filter(var -> var instanceof PsiLocalVariable)
            .anyMatch(var -> notEquivalentVariableDeclarations.contains(var));

          if (dependsOnVariableWithNonEquivalentInitializer) {
            break;
          }
          PsiVariable variable = extractVariable(unit.getThenStatement());
          if (variable != null) {
            extractedVariables.add(variable);
            if (!unit.isEquivalent()) {
              notEquivalentVariableDeclarations.add(variable);
            }
          }
          headCommonParts.add(unit);
        }
      }
    }


    /**
     * Heuristic detecting that removing duplication can decrease beauty of the code
     */
    private static boolean isSimilarHeadStatements(PsiStatement @NotNull [] thenBranch) {
      if (thenBranch.length <= SIMILAR_STATEMENTS_COUNT) return false;
      PsiExpressionStatement expressionStatement = tryCast(thenBranch[0], PsiExpressionStatement.class);
      return isSimilarStatements(thenBranch, expressionStatement);
    }

    private static boolean isSimilarStatements(PsiStatement @NotNull [] branch, PsiExpressionStatement expressionStatement) {
      if (expressionStatement == null) return false;
      PsiMethodCallExpression call = tryCast(expressionStatement.getExpression(), PsiMethodCallExpression.class);
      if (call == null) return false;
      for (int i = branch.length - 1; i >= 0; i--) {
        if (!isSimilarCall(branch[i], call)) return false;
      }
      return true;
    }

    private static boolean isSimilarTailStatements(PsiStatement @NotNull [] thenBranch) {
      if (thenBranch.length <= SIMILAR_STATEMENTS_COUNT) return false;
      PsiExpressionStatement expressionStatement = tryCast(thenBranch[thenBranch.length - 1], PsiExpressionStatement.class);
      return isSimilarStatements(thenBranch, expressionStatement);
    }

    private static boolean isSimilarCall(PsiStatement statement, PsiMethodCallExpression call) {
      PsiExpressionStatement currentStatement = tryCast(statement, PsiExpressionStatement.class);
      if (currentStatement == null) return false;
      PsiMethodCallExpression otherCall = tryCast(currentStatement.getExpression(), PsiMethodCallExpression.class);
      if (otherCall == null) return false;
      return isEqualChain(call, otherCall);
    }

    /**
     * equals on chain of methods not considering argument lists, just method names
     */
    private static boolean isEqualChain(@Nullable PsiExpression first, @Nullable PsiExpression second) {
      if(first == null && second == null) return true;
      if(first == null || second == null) return false;
      PsiMethodCallExpression firstCall = tryCast(first, PsiMethodCallExpression.class);
      PsiMethodCallExpression secondCall = tryCast(second, PsiMethodCallExpression.class);
      PsiExpression firstCurrent = first;
      PsiExpression secondCurrent = second;
      while (firstCall != null && secondCall != null) {
        String firstName = firstCall.getMethodExpression().getReferenceName();
        String secondName = secondCall.getMethodExpression().getReferenceName();
        if(firstName == null || !firstName.equals(secondName)) return false;

        firstCurrent = firstCall.getMethodExpression().getQualifierExpression();
        secondCurrent = secondCall.getMethodExpression().getQualifierExpression();
        firstCall = tryCast(firstCurrent, PsiMethodCallExpression.class);
        secondCall = tryCast(secondCurrent, PsiMethodCallExpression.class);
      }
      return firstCurrent == null && secondCurrent == null ||
             EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(firstCurrent, secondCurrent);
    }

    private static boolean uncommonElseStatementsContainsThenNames(PsiStatement @NotNull [] elseBranch,
                                                                   int elseLen,
                                                                   List<ExtractionUnit> headCommonParts,
                                                                   List<PsiStatement> tailCommonParts,
                                                                   Map<PsiLocalVariable, String> substitutionTable) {
      if (!substitutionTable.isEmpty()) {
        int firstTailCommonStatementIndex = elseLen - tailCommonParts.size();
        HashSet<String> names = new HashSet<>(substitutionTable.values());
        for (int i = headCommonParts.size(); i < firstTailCommonStatementIndex; i++) {
          if (StreamEx.ofTree((PsiElement)elseBranch[i], e -> StreamEx.of(e.getChildren()))
            .select(PsiVariable.class)
            .filter(var -> var instanceof PsiLocalVariable || var instanceof PsiParameter)
            .anyMatch(var -> names.contains(var.getName()))) {
            return true;
          }
        }
      }
      return false;
    }
  }

  private static boolean branchesAreEquivalent(PsiStatement @NotNull [] thenBranch,
                                               @NotNull List<? extends PsiStatement> statements,
                                               @NotNull EquivalenceChecker equivalence) {
    for (int i = 0, length = statements.size(); i < length; i++) {
      PsiStatement elseStmt = statements.get(i);
      PsiStatement thenStmt = thenBranch[i];
      if (!equivalence.statementsAreEquivalent(thenStmt, elseStmt)) return false;
    }
    return true;
  }

  private static final class ElseIf {
    final @NotNull PsiStatement myElseBranch;
    final @NotNull PsiStatement myElseIfBranchToKeep;
    final @NotNull PsiElement myElseIfBranchToRemove;
    final @NotNull PsiExpression myElseIfCondition;
    final @NotNull Map<PsiLocalVariable, String> mySubstitutionTable;
    final boolean myInvert;

    private ElseIf(@NotNull PsiStatement elseBranch,
                   @NotNull PsiStatement elseIfBranchToKeep,
                   @NotNull PsiStatement elseIfBranchToRemove, 
                   @NotNull PsiExpression elseIfCondition,
                   @NotNull Map<PsiLocalVariable, String> table, boolean needInvert) {
      myElseBranch = elseBranch;
      myElseIfBranchToKeep = elseIfBranchToKeep;
      myElseIfBranchToRemove = elseIfBranchToRemove;
      myElseIfCondition = elseIfCondition;
      mySubstitutionTable = table;
      myInvert = needInvert;
    }

    static @Nullable ElseIf from(@NotNull PsiIfStatement ifStatement, PsiStatement @NotNull [] thenStatements) {
      PsiStatement elseBranch = ifStatement.getElseBranch();
      if (ifStatement.getCondition() == null) return null;
      PsiIfStatement elseIf = tryCast(ControlFlowUtils.stripBraces(elseBranch), PsiIfStatement.class);
      if (elseIf == null) return null;
      PsiExpression elseIfCondition = elseIf.getCondition();
      if (elseIfCondition == null) return null;
      PsiStatement elseIfThenBranch = elseIf.getThenBranch();
      if(elseIfThenBranch == null) return null;
      PsiStatement[] elseIfThen = ControlFlowUtils.unwrapBlock(elseIfThenBranch);
      PsiStatement elseIfElseBranch = elseIf.getElseBranch();
      if (elseIfElseBranch == null) return null;
      if (elseIfThen.length == thenStatements.length) {
        LocalEquivalenceChecker equivalence = getChecker(thenStatements, elseIfThen);
        if (branchesAreEquivalent(thenStatements, Arrays.asList(elseIfThen), equivalence)) {
          return new ElseIf(elseBranch, elseIfElseBranch, elseIfThenBranch, elseIfCondition, equivalence.mySubstitutionTable, false);
        }
      }
      PsiStatement[] elseIfElse = ControlFlowUtils.unwrapBlock(elseIfElseBranch);
      if (elseIfElse.length == thenStatements.length) {
        LocalEquivalenceChecker equivalence = getChecker(thenStatements, elseIfThen);
        if (branchesAreEquivalent(thenStatements, Arrays.asList(elseIfElse), equivalence)) {
          return new ElseIf(elseBranch, elseIfThenBranch, elseIfElseBranch, elseIfCondition, equivalence.mySubstitutionTable, true);
        }
      }
      return null;
    }

    static @Nullable IfInspectionResult inspect(@NotNull PsiIfStatement ifStatement,
                                                PsiStatement @NotNull [] thenBranch,
                                                PsiStatement @NotNull [] elseBranch,
                                                boolean isOnTheFly,
                                                IfStatementWithIdenticalBranchesInspection inspection) {
      ElseIf elseIf = from(ifStatement, thenBranch);
      if (elseIf == null) return null;
      String message = JavaAnalysisBundle.message("inspection.common.if.parts.family.else.if.description");
      return new IfInspectionResult(ifStatement.getFirstChild(), inspection.myHighlightElseIf, new MergeElseIfsFix(elseIf.myInvert), message);
    }
  }

  /**
   * Equivalence checker that allows to substitute some variable names with another
   */
  private static final class LocalEquivalenceChecker extends EquivalenceChecker {
    final Set<PsiLocalVariable> myLocalVariables;
    // From else variable to then variable name
    final Map<PsiLocalVariable, String> mySubstitutionTable = new HashMap<>(0); // supposed to use rare

    private LocalEquivalenceChecker(Set<PsiLocalVariable> variables) {
      myLocalVariables = variables;
    }

    public boolean topLevelVarsAreEqualNotConsideringInitializers(@NotNull PsiStatement first,
                                                                  @NotNull PsiStatement second) {
      PsiLocalVariable localVariable1 = extractVariable(first);
      PsiLocalVariable localVariable2 = extractVariable(second);
      if (localVariable1 == null || localVariable2 == null) return false;
      if (!myLocalVariables.contains(localVariable1) || !myLocalVariables.contains(localVariable2)) {
        return false;
      }
      return equalNotConsideringInitializer(localVariable1, localVariable2);
    }

    private boolean equalNotConsideringInitializer(@NotNull PsiLocalVariable localVariable1, @NotNull PsiLocalVariable localVariable2) {

      PsiModifierList firstModifierList = localVariable1.getModifierList();
      PsiModifierList secondModifierList = localVariable2.getModifierList();
      if (firstModifierList != null || secondModifierList != null) {
        if (firstModifierList == null || secondModifierList == null) {
          return false;
        }
        String firstModifierListText = firstModifierList.getText();
        String secondModifierListText = secondModifierList.getText();
        if (firstModifierListText != null && !firstModifierListText.equals(secondModifierListText)) {
          return false;
        }
      }
      PsiType firstType = localVariable1.getType();
      if (!firstType.equals(localVariable2.getType())) return false;
      String firstName = localVariable1.getName();
      String secondName = localVariable2.getName();
      if (!firstName.equals(secondName)) {
        mySubstitutionTable.put(localVariable2, firstName);
      }
      return true;
    }

    @Override
    protected Match localVariablesAreEquivalent(@NotNull PsiLocalVariable localVariable1,
                                                @NotNull PsiLocalVariable localVariable2) {
      if (!myLocalVariables.contains(localVariable1) || !myLocalVariables.contains(localVariable2)) {
        return super.localVariablesAreEquivalent(localVariable1, localVariable2);
      }
      if (!equalNotConsideringInitializer(localVariable1, localVariable2)) return EXACT_MISMATCH;
      PsiExpression firstInitializer = localVariable1.getInitializer();
      PsiExpression secondInitializer = localVariable2.getInitializer();
      return expressionsMatch(firstInitializer, secondInitializer);
    }

    @Override
    protected Match referenceExpressionsMatch(PsiReferenceExpression first, PsiReferenceExpression second) {
      PsiElement firstElement = first.resolve();
      PsiElement secondElement = second.resolve();
      if (firstElement instanceof PsiLocalVariable firstVar &&
          secondElement instanceof PsiLocalVariable secondVar &&
          myLocalVariables.contains(firstElement) &&
          myLocalVariables.contains(secondElement)) {
        if (firstVar.getType().equals(secondVar.getType())) {
          String firstVarName = firstVar.getName();
          String secondVarName = secondVar.getName();
          String substitutedName = mySubstitutionTable.get(secondVar);
          if (substitutedName == null) {
            return firstVarName.equals(secondVarName)  ? EXACT_MATCH : EXACT_MISMATCH;
          }
          return firstVarName.equals(substitutedName) ? EXACT_MATCH : EXACT_MISMATCH;
        }
      }
      return super.referenceExpressionsMatch(first, second);
    }
  }
}
