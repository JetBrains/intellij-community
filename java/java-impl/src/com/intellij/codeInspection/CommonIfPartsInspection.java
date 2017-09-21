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
package com.intellij.codeInspection;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.HashSet;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.SideEffectChecker;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.intellij.util.ObjectUtils.tryCast;

public class CommonIfPartsInspection extends BaseJavaBatchLocalInspectionTool {

  public static final EquivalenceChecker ourEquivalence = new EquivalenceChecker() {
    @Override
    protected Match referenceExpressionsMatch(PsiReferenceExpression first,
                                              PsiReferenceExpression second) {
      PsiElement firstElement = first.resolve();
      PsiElement secondElement = second.resolve();
      if(firstElement != null && firstElement != secondElement && firstElement instanceof PsiLocalVariable && secondElement instanceof PsiLocalVariable) {
        PsiLocalVariable secondVar = (PsiLocalVariable)secondElement;
        PsiLocalVariable firstVar = (PsiLocalVariable)firstElement;
        if(firstVar.getName() == secondVar.getName() && firstVar.getType().equals(secondVar.getType())) {
          return EXACT_MATCH;
        }
      }
      return super.referenceExpressionsMatch(first, second);
    }
  };

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaRecursiveElementVisitor() {
      @Override
      public void visitIfStatement(PsiIfStatement ifStatement) {
        ExtractionContext context = ExtractionContext.from(ifStatement);
        if (context == null) return;
        CommonPartType type = context.getType();
        boolean mayChangeSemantics = context.mayChangeSemantics();
        boolean warning = type == CommonPartType.WITHOUT_VARIABLES_EXTRACT && !mayChangeSemantics;
        ProblemHighlightType highlightType = warning ? ProblemHighlightType.WEAK_WARNING : ProblemHighlightType.INFORMATION;
        String message = getMessage(mayChangeSemantics, type);
        holder.registerProblem(ifStatement, message, highlightType, new ExtractCommonIfPartsFix(type, mayChangeSemantics));
      }
    };
  }

  @NotNull
  private static String getMessage(boolean mayChangeSemantics, CommonPartType type) {
    String mayChangeSemanticsText = mayChangeSemantics ? "(may change semantics)" : "";
    switch (type) {
      case VARIABLES_ONLY:
        return InspectionsBundle.message("inspection.common.if.parts.message.variables.only", mayChangeSemanticsText);
      case WITH_VARIABLES_EXTRACT:
        return InspectionsBundle.message("inspection.common.if.parts.message.with.variables.extract", mayChangeSemanticsText);
      case WITHOUT_VARIABLES_EXTRACT:
        return InspectionsBundle.message("inspection.common.if.parts.message.without.variables.extract", mayChangeSemanticsText);
    }
    return null;
  }

  @Nullable
  private static ExtractionUnit extractHeadCommonStatement(@NotNull PsiStatement thenStmt,
                                                           @NotNull PsiStatement elseStmt,
                                                           @NotNull PsiIfStatement ifStatement) {
    if (!(thenStmt instanceof PsiDeclarationStatement) && ourEquivalence.statementsAreEquivalent(thenStmt, elseStmt)) {
      boolean mayChangeSemantics = SideEffectChecker.mayHaveSideEffects(thenStmt, expression -> false);
      return new ExtractionUnit(thenStmt, mayChangeSemantics, elseStmt, true);
    }
    if (!(thenStmt instanceof PsiDeclarationStatement) || !(elseStmt instanceof PsiDeclarationStatement)) return null;
    PsiVariable thenVariable = extractVariable(thenStmt);
    if(thenVariable == null) return null;
    PsiVariable elseVariable = extractVariable(elseStmt);
    if(elseVariable == null) return null;
    if (thenVariable.getName() == null || thenVariable.getName() != elseVariable.getName()) return null;
    if (!thenVariable.getType().equals(elseVariable.getType())) return null;

    PsiExpression thenInitializer = thenVariable.getInitializer();
    boolean hasSideEffects = thenInitializer != null &&
                             SideEffectChecker.mayHaveSideEffects(thenInitializer, expression -> false);
    //String name = thenVariable.getName();
    //if (name == null || PsiResolveHelper.SERVICE.getInstance(thenStmt.getProject()).resolveReferencedVariable(name, ifStatement) != null) {
    //  return null;
    //} // TODO look after ifStatement for the declared variable with this name
    boolean equivalent = ourEquivalence.expressionsAreEquivalent(thenInitializer, elseVariable.getInitializer());
    return new ExtractionUnit(thenStmt, hasSideEffects, elseStmt, equivalent);
  }


  private static class ExtractCommonIfPartsFix implements LocalQuickFix {
    private final CommonPartType myType;
    private final boolean myMayChangeSemantics;

    private ExtractCommonIfPartsFix(CommonPartType type, boolean semantics) {
      myType = type;
      myMayChangeSemantics = semantics;
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Common parts of if statement can be extracted";
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return getMessage(myMayChangeSemantics, myType);
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiIfStatement ifStatement = tryCast(descriptor.getStartElement(), PsiIfStatement.class);
      if (ifStatement == null) return;
      ExtractionContext context = ExtractionContext.from(ifStatement);
      if (context == null) return;
      List<ExtractionUnit> units = context.getStartingUnits();
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);

      if (tryCleanUpHead(ifStatement, units, factory)) return;
      cleanUpTail(ifStatement, context);

      PsiStatement elseBranch = ifStatement.getElseBranch();
      if (elseBranch != null && ControlFlowUtils.unwrapBlock(elseBranch).length == 0) {
        elseBranch.delete();
      }
    }

    private static boolean tryCleanUpHead(PsiIfStatement ifStatement, List<ExtractionUnit> units, PsiElementFactory factory) {
      PsiElement parent = ifStatement.getParent();
      for (ExtractionUnit unit : units) {
        PsiStatement thenStatement = unit.getThenStatement();
        PsiStatement elseStatement = unit.getElseStatement();
        if (thenStatement instanceof PsiDeclarationStatement && (unit.mayChangeSemantics() || !unit.hasEquivalentStatements())) {
          PsiExpression thenInitializer = extractInitializer(thenStatement);
          PsiExpression elseInitializer = extractInitializer(elseStatement);
          PsiVariable variable = extractVariable(thenStatement);
          if (variable == null) return true;
          String varName = variable.getName(); // TODO handle outer context
          if (varName == null) return true;
          String variableDeclaration = variable.getType().getCanonicalText() + " " + varName + ";";
          PsiStatement varDeclarationStmt = factory.createStatementFromText(variableDeclaration, parent);
          parent.addBefore(varDeclarationStmt, ifStatement);

          replaceWithDeclarationIfNeeded(ifStatement, factory, thenStatement, thenInitializer, varName);
          replaceWithDeclarationIfNeeded(ifStatement, factory, elseStatement, elseInitializer, varName);
        }
        else {
          parent.addBefore(thenStatement.copy(), ifStatement);
          thenStatement.delete();
          elseStatement.delete();
        }
      }
      return false;
    }

    private static void cleanUpTail(PsiIfStatement ifStatement, ExtractionContext context) {
      List<PsiStatement> tailStatements = context.getFinishingStatements();
      if(!tailStatements.isEmpty()) {
        //PsiStatement anchor = ifStatement;
        for (PsiStatement statement : tailStatements) {
          ifStatement.getParent().addAfter(statement.copy(), ifStatement);
        }
        PsiStatement[] thenStatements = ControlFlowUtils.unwrapBlock(ifStatement.getThenBranch());
        PsiStatement[] elseStatements = ControlFlowUtils.unwrapBlock(ifStatement.getElseBranch());
        int thenLength = thenStatements.length;
        int elseLength = elseStatements.length;
        for (int i = 0; i < tailStatements.size(); i++) {
          thenStatements[thenLength - 1 - i].delete();
          elseStatements[elseLength - 1 - i].delete();
        }
      }
    }

    private static void replaceWithDeclarationIfNeeded(PsiIfStatement ifStatement,
                                                       PsiElementFactory factory,
                                                       PsiStatement statement,
                                                       PsiExpression initializer,
                                                       String varName) {
      if (initializer != null) {
        PsiStatement assignment = factory.createStatementFromText(varName + "=" + initializer.getText() + ";", ifStatement);
        statement.replace(assignment);
      }
    }

    @Nullable
    private static PsiExpression extractInitializer(@Nullable PsiStatement statement) {
      PsiVariable variable = extractVariable(statement);
      if (variable == null) return null;
      return variable.getInitializer();
    }
  }

  @Nullable
  private static PsiVariable extractVariable(@Nullable PsiStatement statement) {
    PsiDeclarationStatement declarationStatement = tryCast(statement, PsiDeclarationStatement.class);
    if (declarationStatement == null) return null;
    PsiElement[] elements = declarationStatement.getDeclaredElements();
    if (elements.length != 1) return null;
    return tryCast(elements[0], PsiLocalVariable.class);
  }

  private static class ExtractionUnit {
    private final boolean myMayChangeSemantics;
    private final @NotNull PsiStatement myThenStatement;
    private final @NotNull PsiStatement myElseStatement;
    private final boolean myIsEquivalent;


    private ExtractionUnit(@NotNull PsiStatement thenStatement,
                           boolean mayChangeSemantics,
                           @NotNull PsiStatement elseStatement,
                           boolean isEquivalent) {
      myMayChangeSemantics = mayChangeSemantics;
      myThenStatement = thenStatement;
      myElseStatement = elseStatement;
      myIsEquivalent = isEquivalent;
    }

    public boolean mayChangeSemantics() {
      return myMayChangeSemantics;
    }

    @NotNull
    public PsiStatement getThenStatement() {
      return myThenStatement;
    }

    @NotNull
    public PsiStatement getElseStatement() {
      return myElseStatement;
    }

    public boolean hasEquivalentStatements() {
      return myIsEquivalent;
    }
  }

  private enum CommonPartType {
    VARIABLES_ONLY,
    WITH_VARIABLES_EXTRACT, // statements require some of variables from head to be extracted
    WITHOUT_VARIABLES_EXTRACT
  }

  private static class ExtractionContext {
    private final List<ExtractionUnit> myStartingUnits;
    private final List<PsiStatement> myFinishingStatements; // In reversed order
    private final CommonPartType myType;

    private ExtractionContext(List<ExtractionUnit> units,
                              List<PsiStatement> statements,
                              CommonPartType type) {
      myStartingUnits = units;
      myFinishingStatements = statements;
      myType = type;
    }

    boolean mayChangeSemantics() {
      return !myStartingUnits.isEmpty() && StreamEx.of(myStartingUnits)
        .anyMatch(unit -> unit.mayChangeSemantics() && !(unit.getThenStatement() instanceof PsiDeclarationStatement));
    }

    public List<ExtractionUnit> getStartingUnits() {
      return myStartingUnits;
    }

    public List<PsiStatement> getFinishingStatements() {
      return myFinishingStatements;
    }

    public CommonPartType getType() {
      return myType;
    }

    @Nullable
    static ExtractionContext from(@NotNull PsiIfStatement ifStatement) {
      PsiStatement[] thenBranch = ControlFlowUtils.unwrapBlock(ifStatement.getThenBranch());
      PsiStatement[] elseBranch = ControlFlowUtils.unwrapBlock(ifStatement.getElseBranch());

      int thenLen = thenBranch.length;
      int elseLen = elseBranch.length;
      int minStmtCount = Math.min(thenLen, elseLen);

      PsiExpression condition = ifStatement.getCondition();
      if (condition == null) return null;
      if (SideEffectChecker.mayHaveSideEffects(condition)) return null;

      List<ExtractionUnit> headCommonParts = new ArrayList<>();
      Set<PsiVariable> extractedVariables = new HashSet<>();
      Set<PsiVariable> notEquivalentVariableDeclarations = new HashSet<>();
      for (int i = 0; i < minStmtCount; i++) {
        PsiStatement thenStmt = thenBranch[i];
        PsiStatement elseStmt = elseBranch[i];
        ExtractionUnit unit = extractHeadCommonStatement(thenStmt, elseStmt, ifStatement);
        if (unit == null) break;
        PsiVariable variable = extractVariable(unit.getThenStatement());
        if(variable != null) {
          extractedVariables.add(variable);
          if(!unit.hasEquivalentStatements()) {
            notEquivalentVariableDeclarations.add(variable);
          }
        } else {
          boolean dependsOnVariableWithNonEquivalentInitializer = StreamEx.ofTree((PsiElement)thenStmt, stmt -> StreamEx.of(stmt.getChildren()))
            .select(PsiReferenceExpression.class)
            .map(ref -> ref.resolve())
            .select(PsiLocalVariable.class)
            .anyMatch(var -> notEquivalentVariableDeclarations.contains(var));
          if(dependsOnVariableWithNonEquivalentInitializer) {
            break;
          }
        }
        headCommonParts.add(unit);
      }

      int extractedFromStart = headCommonParts.size();
      int canBeExtractedFromEnd = Math.min(thenLen - extractedFromStart, elseLen - extractedFromStart);
      List<PsiStatement> tailCommonParts = new ArrayList<>();
      for (int i = 0; i < canBeExtractedFromEnd; i++) {
        PsiStatement thenStmt = thenBranch[thenLen - i - 1];
        PsiStatement elseStmt = elseBranch[elseLen - i - 1];
        if (ourEquivalence.statementsAreEquivalent(thenStmt, elseStmt)) {
          boolean canBeExtractedOutOfIf = StreamEx.ofTree((PsiElement)thenStmt, stmt -> StreamEx.of(stmt.getChildren()))
            .select(PsiReferenceExpression.class)
            .map(ref -> ref.resolve())
            .select(PsiLocalVariable.class)
            .filter(var -> PsiTreeUtil.isAncestor(ifStatement, var, false))
            .allMatch(var -> extractedVariables.contains(var));
          if(!canBeExtractedOutOfIf) break;
          tailCommonParts.add(thenStmt);
        }
        else {
          break;
        }
      }
      if(canBeExtractedFromEnd == tailCommonParts.size()) {
        // trying to append to tail statements that may change semantics from head, because in tail they can't change semantics
        for (int i = headCommonParts.size() - 1; i > 0; i--) {
          ExtractionUnit unit = headCommonParts.get(i);
          PsiStatement thenStatement = unit.getThenStatement();
          if(unit.mayChangeSemantics() && unit.hasEquivalentStatements()) {
            headCommonParts.remove(i);
            tailCommonParts.add(thenStatement);
          } else {
            break;
          }
        }
      }
      if (headCommonParts.isEmpty() && tailCommonParts.isEmpty()) return null;
      final CommonPartType type = getType(headCommonParts, tailCommonParts);
      return new ExtractionContext(headCommonParts, tailCommonParts, type);
    }

    @NotNull
    private static CommonPartType getType(@NotNull List<ExtractionUnit> headStatements, List<PsiStatement> tailStatements) {
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
      if(!(hasVariables && hasNonVariables)) {
        for (PsiStatement statement : tailStatements) {
          if(statement instanceof PsiDeclarationStatement) {
            hasVariables = true;
          } else {
            hasNonVariables = true;
          }
          if(hasVariables && hasNonVariables) break;
        }
      }
      final CommonPartType type;
      if (hasVariables && hasNonVariables) {
        type = CommonPartType.WITH_VARIABLES_EXTRACT;
      }
      else if (hasVariables) {
        type = CommonPartType.VARIABLES_ONLY;
      }
      else {
        type = CommonPartType.WITHOUT_VARIABLES_EXTRACT;
      }
      return type;
    }
  }
}
