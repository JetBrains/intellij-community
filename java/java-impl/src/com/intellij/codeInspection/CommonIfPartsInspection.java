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
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.HashSet;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.util.ObjectUtils.tryCast;

public class CommonIfPartsInspection extends BaseJavaBatchLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitIfStatement(PsiIfStatement ifStatement) {
        ExtractionContext context = ExtractionContext.from(ifStatement, isOnTheFly);
        if (context == null) return;
        CommonPartType type = context.getType();
        boolean mayChangeSemantics = context.mayChangeSemantics();
        boolean warning = type != CommonPartType.WITH_VARIABLES_EXTRACT && type != CommonPartType.VARIABLES_ONLY && !mayChangeSemantics;
        ProblemHighlightType highlightType = warning ? ProblemHighlightType.WEAK_WARNING : ProblemHighlightType.INFORMATION;
        String message = type.getMessage(mayChangeSemantics);
        PsiElement element = warning ? ifStatement.getChildren()[0] : ifStatement;
        holder.registerProblem(element, message, highlightType, new ExtractCommonIfPartsFix(type, mayChangeSemantics, isOnTheFly));

      }
    };
  }


  @Nullable
  private static ExtractionUnit extractHeadCommonStatement(@NotNull PsiStatement thenStmt,
                                                           @NotNull PsiStatement elseStmt,
                                                           boolean isOnTheFly,
                                                           LocalEquivalenceChecker equivalence) {
    boolean equal = thenStmt instanceof PsiDeclarationStatement
                ? equivalence.topLevelVarsAreEqualNotConsideringInitializers(thenStmt, elseStmt)
                : equivalence.statementsAreEquivalent(thenStmt, elseStmt);
    if (!equal) return null;
    final boolean statementMayChangeSemantics;
    final boolean equivalent;
    if (!(thenStmt instanceof PsiDeclarationStatement)) {
      statementMayChangeSemantics = SideEffectChecker.mayHaveSideEffects(thenStmt, expression -> false);
      equivalent = true;
    } else {
      PsiLocalVariable thenVariable = extractVariable(thenStmt);
      PsiLocalVariable elseVariable = extractVariable(elseStmt);
      if(thenVariable == null || elseVariable == null) return null;
      PsiExpression thenInitializer = thenVariable.getInitializer();
      if(thenInitializer == null) return null;
      statementMayChangeSemantics = SideEffectChecker.mayHaveSideEffects(thenInitializer, expression -> false);
      equivalent = equivalence.expressionsAreEquivalent(thenInitializer, elseVariable.getInitializer());
    }
    if (!isOnTheFly && statementMayChangeSemantics) return null;
    return new ExtractionUnit(thenStmt, elseStmt, statementMayChangeSemantics, equivalent);
  }


  private static class ExtractCommonIfPartsFix implements LocalQuickFix {
    private final CommonPartType myType;
    private final boolean myMayChangeSemantics;
    private final boolean myIsOnTheFly;

    private ExtractCommonIfPartsFix(CommonPartType type, boolean semantics, boolean isOnTheFly) {
      myType = type;
      myMayChangeSemantics = semantics;
      myIsOnTheFly = isOnTheFly;
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.common.if.parts.family");
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return myType.getMessage(myMayChangeSemantics);
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiIfStatement ifStatement = PsiTreeUtil.getParentOfType(descriptor.getStartElement(), PsiIfStatement.class, false);
      if (ifStatement == null) return;
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      if (!(ifStatement.getParent() instanceof PsiCodeBlock)) {
        PsiBlockStatement block = BlockUtils.expandSingleStatementToBlockStatement(ifStatement);
        ifStatement = (PsiIfStatement)block.getCodeBlock().getStatements()[0];
      }
      ExtractionContext context = ExtractionContext.from(ifStatement, myIsOnTheFly);
      if (context == null) return;
      List<ExtractionUnit> units = context.getHeadUnits();

      if (context.getImplicitElse() == null) {
        replaceNames(ifStatement.getElseBranch(), context);
        if (!tryCleanUpHead(ifStatement, units, factory)) return;
        cleanUpTail(ifStatement, context);
        PsiStatement elseBranch = ifStatement.getElseBranch();
        PsiStatement thenBranch = ifStatement.getThenBranch();
        boolean elseToDelete = elseBranch != null && ControlFlowUtils.unwrapBlock(elseBranch).length == 0;
        boolean thenToDelete = thenBranch != null && ControlFlowUtils.unwrapBlock(thenBranch).length == 0;
        if (thenToDelete && elseToDelete) {
          ifStatement.delete();
        }
        else if (elseToDelete) {
          elseBranch.delete();
        }
        else if (thenToDelete) {
          PsiExpression condition = ifStatement.getCondition();
          if (condition == null) return;
          String negatedCondition = BoolUtils.getNegatedExpressionText(condition);
          String newThenBranch = elseBranch == null ? "{}" : elseBranch.getText();
          ifStatement.replace(factory.createStatementFromText("if(" + negatedCondition + ")" + newThenBranch, ifStatement));
        }
      }
      else {
        List<PsiStatement> tailStatements = context.getTailStatements();
        PsiIfStatement withImplicitElse = context.getImplicitElse().getIfWithImplicitElse();
        PsiIfStatement currentIf = withImplicitElse != null ? withImplicitElse : ifStatement;
        for (PsiStatement statement : tailStatements) {
          currentIf.getParent().addAfter(statement.copy(), currentIf);
        }
        for (PsiStatement statement : tailStatements) {
          statement.delete();
        }
        new CommentTracker().deleteAndRestoreComments(currentIf);
      }
    }

    private static void replaceNames(@Nullable PsiStatement statement, ExtractionContext context) {
      if(statement == null) return;
      Map<PsiLocalVariable, String> substitutionTable = context.getSubstitutionTable();
      for (Map.Entry<PsiLocalVariable, String> entry : substitutionTable.entrySet()) {
        PsiLocalVariable variable = entry.getKey();
        String newName = entry.getValue();
        ReferencesSearch.search(variable, new LocalSearchScope(statement)).forEach(reference -> {
          if (reference.getElement() instanceof PsiReferenceExpression) {
            ExpressionUtils.bindReferenceTo((PsiReferenceExpression)reference.getElement(), newName);
          }
        });
      }
    }

    private static boolean tryCleanUpHead(PsiIfStatement ifStatement, List<ExtractionUnit> units, PsiElementFactory factory) {
      PsiElement parent = ifStatement.getParent();
      for (ExtractionUnit unit : units) {
        PsiStatement thenStatement = unit.getThenStatement();
        PsiStatement elseStatement = unit.getElseStatement();
        if (thenStatement instanceof PsiDeclarationStatement) {
          PsiExpression thenInitializer = extractInitializer(thenStatement);
          PsiExpression elseInitializer = extractInitializer(elseStatement);
          PsiVariable variable = extractVariable(thenStatement);
          JavaCodeStyleManager manager = JavaCodeStyleManager.getInstance(ifStatement.getProject());
          if (variable == null) return false;
          String baseName = variable.getName();
          if (baseName == null) return false;
          String varName = manager.suggestUniqueVariableName(baseName, ifStatement, var -> PsiTreeUtil.isAncestor(ifStatement, var, false));
          if (!baseName.equals(varName)) {
            thenStatement = replaceName(ifStatement, factory, thenStatement, variable, varName);
          }
          if (!unit.hasEquivalentStatements()) {
            PsiModifierList modifierList = variable.getModifierList();
            String modifiers;
            if (modifierList == null || modifierList.getText().isEmpty()) {
              modifiers = "";
            }
            else {
              modifiers = modifierList.getText() + " ";
            }
            String variableDeclaration = modifiers + variable.getType().getCanonicalText() + " " + varName + ";";
            PsiStatement varDeclarationStmt = factory.createStatementFromText(variableDeclaration, parent);
            parent.addBefore(varDeclarationStmt, ifStatement);

            replaceWithDeclarationIfNeeded(ifStatement, factory, thenStatement, thenInitializer, varName);
            replaceWithDeclarationIfNeeded(ifStatement, factory, elseStatement, elseInitializer, varName);
            continue;
          }
        }
        parent.addBefore(thenStatement.copy(), ifStatement);
        thenStatement.delete();
        elseStatement.delete();
      }
      return true;
    }

    private static PsiStatement replaceName(PsiIfStatement ifStatement,
                                            PsiElementFactory factory,
                                            PsiStatement thenStatement,
                                            PsiVariable variable, String varName) {
      ReferencesSearch.search(variable, new LocalSearchScope(ifStatement)).forEach(reference -> {
        if (reference.getElement() instanceof PsiReferenceExpression) {
          ExpressionUtils.bindReferenceTo((PsiReferenceExpression)reference.getElement(), varName);
        }
      });
      String maybeInitializer = variable.getInitializer() == null ? "" : "=" + variable.getInitializer().getText();
      String text = variable.getType().getCanonicalText() + " " + varName + maybeInitializer + ";";
      PsiStatement variableDeclaration =
        factory.createStatementFromText(text, null);
      thenStatement = (PsiStatement)thenStatement.replace(variableDeclaration);
      return thenStatement;
    }

    private static void cleanUpTail(PsiIfStatement ifStatement, ExtractionContext context) {
      List<PsiStatement> tailStatements = context.getTailStatements();
      if (!tailStatements.isEmpty()) {
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
  private static PsiLocalVariable extractVariable(@Nullable PsiStatement statement) {
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
                           @NotNull PsiStatement elseStatement,
                           boolean mayChangeSemantics,
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
    VARIABLES_ONLY("inspection.common.if.parts.message.variables.only"),
    WITH_VARIABLES_EXTRACT("inspection.common.if.parts.message.with.variables.extract"),
    WITHOUT_VARIABLES_EXTRACT("inspection.common.if.parts.message.without.variables.extract"),
    WHOLE_BRANCH("inspection.common.if.parts.message.whole.branch"),
    COMPLETE_DUPLICATE("inspection.common.if.parts.message.complete.duplicate");

    private @NotNull final String myBundleKey;

    @NotNull
    private String getMessage(boolean mayChangeSemantics) {
      String mayChangeSemanticsText = mayChangeSemantics ? "(may change semantics)" : "";
      return InspectionsBundle.message(myBundleKey, mayChangeSemanticsText);
    }

    CommonPartType(@NotNull String key) {myBundleKey = key;}
  }

  private static class ExtractionContext {
    private final List<ExtractionUnit> myHeadUnits;
    private final List<PsiStatement> myFinishingStatements; // In reversed order
    private final CommonPartType myType;
    private final @Nullable ImplicitElse myImplicitElse;
    private final @NotNull Map<PsiLocalVariable, String> mySubstitutionTable;
    private final boolean myMayChangeSemantics;

    @NotNull
    public Map<PsiLocalVariable, String> getSubstitutionTable() {
      return mySubstitutionTable;
    }

    private ExtractionContext(List<ExtractionUnit> units,
                              List<PsiStatement> statements,
                              CommonPartType type,
                              @Nullable ImplicitElse implicitElse,
                              @NotNull Map<PsiLocalVariable, String> table,
                              boolean mayChangeSemantics) {
      myHeadUnits = units;
      myFinishingStatements = statements;
      myType = type;
      myImplicitElse = implicitElse;
      mySubstitutionTable = table;
      myMayChangeSemantics = mayChangeSemantics;
    }


    public List<ExtractionUnit> getHeadUnits() {
      return myHeadUnits;
    }

    public List<PsiStatement> getTailStatements() {
      return myFinishingStatements;
    }

    public CommonPartType getType() {
      return myType;
    }

    @Nullable
    public ImplicitElse getImplicitElse() {
      return myImplicitElse;
    }

    public boolean mayChangeSemantics() {
      return myMayChangeSemantics;
    }


    private static ExtractionContext fromImplicitElse(PsiStatement[] thenBranch,
                                                      PsiStatement[] elseBranch,
                                                      PsiIfStatement ifStatement) {
      if (elseBranch.length == 0 && thenBranch.length != 0) {
        PsiStatement lastThenStatement = thenBranch[thenBranch.length - 1];
        if (!(lastThenStatement instanceof PsiReturnStatement)) return null;
        boolean returnsNothing = ((PsiReturnStatement)lastThenStatement).getReturnValue() == null;
        ImplicitElse implicitElse = extractImplicitElse(ifStatement, thenBranch.length - 1, returnsNothing);
        if (implicitElse == null) return null;
        List<PsiStatement> statements = implicitElse.getStatements();
        Set<PsiLocalVariable> variables = new HashSet<>();
        addLocalVariables(variables, Arrays.asList(thenBranch));
        addLocalVariables(variables, implicitElse.getStatements());
        LocalEquivalenceChecker equivalence = new LocalEquivalenceChecker(variables);
        for (int i = 0, length = statements.size(); i < length; i++) {
          PsiStatement elseStmt = statements.get(i);
          PsiStatement thenStmt = thenBranch[i];
          if (!equivalence.statementsAreEquivalent(thenStmt, elseStmt)) return null;
        }
        Collections.reverse(statements);
        return new ExtractionContext(Collections.emptyList(), statements, CommonPartType.COMPLETE_DUPLICATE, implicitElse, new HashMap<>(),
                                     false);
      }
      return null;
    }

    private static boolean isMeaningful(@NotNull PsiStatement statement) {
      if (statement instanceof PsiEmptyStatement) return false;
      if (statement instanceof PsiBlockStatement) {
        return ((PsiBlockStatement)statement).getCodeBlock().getStatements().length != 0;
      }
      return true;
    }

    @NotNull
    private static PsiStatement[] unwrap(@Nullable PsiStatement statement) {
      PsiBlockStatement block = tryCast(statement, PsiBlockStatement.class);
      if (block != null) {
        return Arrays.stream(block.getCodeBlock().getStatements()).filter(ExtractionContext::isMeaningful).collect(Collectors.toList())
          .toArray(PsiStatement.EMPTY_ARRAY);
      }
      return statement == null ? PsiStatement.EMPTY_ARRAY : new PsiStatement[]{statement};
    }

    private static void addLocalVariables(Set<PsiLocalVariable> variables, List<PsiStatement> statements) {
      for (PsiStatement statement : statements) {
        addVariables(variables, statement);
      }
    }

    private static void addVariables(Set<PsiLocalVariable> variables, PsiStatement statement) {
      PsiDeclarationStatement declarationStatement = tryCast(statement, PsiDeclarationStatement.class);
      if (declarationStatement == null) return;
      for (PsiElement element : declarationStatement.getDeclaredElements()) {
        if (element instanceof PsiLocalVariable) {
          variables.add((PsiLocalVariable)element);
        }
      }
    }

    @Nullable
    static ExtractionContext from(@NotNull PsiIfStatement ifStatement, boolean isOnTheFly) {
      PsiStatement[] thenBranch = unwrap(ifStatement.getThenBranch());
      PsiStatement[] elseBranch = unwrap(ifStatement.getElseBranch());
      LocalEquivalenceChecker equivalence = getChecker(thenBranch, elseBranch);
      ExtractionContext withImplicitElse = fromImplicitElse(thenBranch, elseBranch, ifStatement);
      if (withImplicitElse != null) {
        return withImplicitElse;
      }

      int thenLen = thenBranch.length;
      int elseLen = elseBranch.length;
      int minStmtCount = Math.min(thenLen, elseLen);

      PsiExpression condition = ifStatement.getCondition();
      if (condition == null) return null;

      List<ExtractionUnit> headCommonParts = new ArrayList<>();
      Set<PsiVariable> extractedVariables = new HashSet<>();
      Set<PsiVariable> notEquivalentVariableDeclarations = new HashSet<>();

      for (int i = 0; i < minStmtCount; i++) {
        PsiStatement thenStmt = thenBranch[i];
        PsiStatement elseStmt = elseBranch[i];
        ExtractionUnit unit = extractHeadCommonStatement(thenStmt, elseStmt, isOnTheFly, equivalence);
        if (unit == null) break;
        PsiVariable variable = extractVariable(unit.getThenStatement());
        if (variable != null) {
          extractedVariables.add(variable);
          if (!unit.hasEquivalentStatements()) {
            notEquivalentVariableDeclarations.add(variable);
          }
        }
        else {
          boolean dependsOnVariableWithNonEquivalentInitializer =
            StreamEx.ofTree((PsiElement)thenStmt, stmt -> StreamEx.of(stmt.getChildren()))
              .select(PsiReferenceExpression.class)
              .map(ref -> ref.resolve())
              .select(PsiLocalVariable.class)
              .anyMatch(var -> notEquivalentVariableDeclarations.contains(var));
          if (dependsOnVariableWithNonEquivalentInitializer) {
            break;
          }
        }
        headCommonParts.add(unit);
      }

      int extractedFromStart = headCommonParts.size();
      int canBeExtractedFromThenTail = thenLen - extractedFromStart;
      int canBeExtractedFromElseTail = elseLen - extractedFromStart;
      int canBeExtractedFromEnd = Math.min(canBeExtractedFromThenTail, canBeExtractedFromElseTail);
      List<PsiStatement> tailCommonParts = new ArrayList<>();
      for (int i = 0; i < canBeExtractedFromEnd; i++) {
        PsiStatement thenStmt = thenBranch[thenLen - i - 1];
        PsiStatement elseStmt = elseBranch[elseLen - i - 1];
        if (equivalence.statementsAreEquivalent(thenStmt, elseStmt)) {
          boolean canBeExtractedOutOfIf = StreamEx.ofTree((PsiElement)thenStmt, stmt -> StreamEx.of(stmt.getChildren()))
            .select(PsiReferenceExpression.class)
            .map(ref -> ref.resolve())
            .select(PsiLocalVariable.class)
            .filter(var -> PsiTreeUtil.isAncestor(ifStatement, var, false))
            .allMatch(var -> extractedVariables.contains(var));
          if (!canBeExtractedOutOfIf) break;
          tailCommonParts.add(thenStmt);
        }
        else {
          break;
        }
      }
      if (canBeExtractedFromEnd == tailCommonParts.size() && canBeExtractedFromElseTail == canBeExtractedFromThenTail) {
        // trying to append to tail statements that may change semantics from head, because in tail they can't change semantics
        for (int i = headCommonParts.size() - 1; i >= 0; i--) {
          ExtractionUnit unit = headCommonParts.get(i);
          PsiStatement thenStatement = unit.getThenStatement();
          if (!unit.mayChangeSemantics() || !unit.hasEquivalentStatements()) break;
          headCommonParts.remove(i);
          tailCommonParts.add(thenStatement);
        }
      }
      if (headCommonParts.isEmpty() && tailCommonParts.isEmpty()) return null;
      final CommonPartType type = getType(headCommonParts, tailCommonParts, thenLen, elseLen, notEquivalentVariableDeclarations.isEmpty());
      boolean mayChangeSemantics = SideEffectChecker.mayHaveSideEffects(condition) && !headCommonParts.isEmpty() && StreamEx.of(headCommonParts)
        .anyMatch(unit -> unit.mayChangeSemantics() && !(unit.getThenStatement() instanceof PsiDeclarationStatement));
      return new ExtractionContext(headCommonParts, tailCommonParts, type, null, equivalence.getSubstitutionTable(), mayChangeSemantics);
    }

    @NotNull
    private static LocalEquivalenceChecker getChecker(PsiStatement[] thenBranch, PsiStatement[] elseBranch) {
      Set<PsiLocalVariable> localVariables = new HashSet<>();
      addLocalVariables(localVariables, Arrays.asList(thenBranch));
      addLocalVariables(localVariables, Arrays.asList(elseBranch));
      return new LocalEquivalenceChecker(localVariables);
    }

    @Nullable
    private static PsiIfStatement getEnclosingIfStmt(@NotNull PsiIfStatement ifStatement) {
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

    static class ImplicitElse {
      private final List<PsiStatement> myStatements;
      private final PsiIfStatement myIfWithImplicitElse;

      ImplicitElse(List<PsiStatement> statements, PsiIfStatement anElse) {
        myStatements = statements;
        myIfWithImplicitElse = anElse;
      }

      public List<PsiStatement> getStatements() {
        return myStatements;
      }

      public PsiIfStatement getIfWithImplicitElse() {
        return myIfWithImplicitElse;
      }
    }

    @Nullable
    private static ImplicitElse extractImplicitElse(@NotNull PsiIfStatement ifStmt,
                                                    int returnStmtIndex,
                                                    boolean returnsNothing) {
      List<PsiStatement> statements = new ArrayList<>();
      if (ifStmt.getElseBranch() != null) return null;
      int count = 0;
      PsiIfStatement currentIf = ifStmt;
      do {
        PsiStatement sibling = currentIf;
        do {
          sibling = PsiTreeUtil.getNextSiblingOfType(sibling, PsiStatement.class);
          if (sibling == null) break;
          if (!isMeaningful(sibling)) continue;
          count++;
          statements.add(sibling);
        }
        while (count <= returnStmtIndex);
        PsiIfStatement nextIf = getEnclosingIfStmt(currentIf);
        if (nextIf == null) break;
        if (nextIf.getElseBranch() != null) return null;
        currentIf = nextIf;
      }
      while (statements.size() != returnStmtIndex + 1);

      if (statements.isEmpty()) return null;
      PsiStatement last = statements.get(statements.size() - 1);
      boolean lastIsReturn = last instanceof PsiReturnStatement;

      if (lastIsReturn) {
        if (statements.size() != returnStmtIndex + 1) return null;
      }
      else { // maybe last statement in method
        PsiMethod method = PsiTreeUtil.getParentOfType(currentIf, PsiMethod.class);
        if (method == null) return null;
        PsiCodeBlock methodBody = method.getBody();
        if (methodBody == null) return null;
        if (!(returnsNothing && statements.size() == returnStmtIndex)) return null;
        PsiStatement[] bodyStatements = methodBody.getStatements();
        PsiStatement lastMethodStmt = bodyStatements[bodyStatements.length - 1];
        if (lastMethodStmt != statements.get(statements.size() - 1)) return null;
      }
      return new ImplicitElse(statements, currentIf);
    }

    @NotNull
    private static CommonPartType getType(@NotNull List<ExtractionUnit> headStatements,
                                          List<PsiStatement> tailStatements,
                                          int thenLen,
                                          int elseLen,
                                          boolean declarationsAreEquivalent) {
      if (declarationsAreEquivalent) {
        int duplicatedStatements = headStatements.size() + tailStatements.size();
        if (thenLen == duplicatedStatements && elseLen == duplicatedStatements) {
          return CommonPartType.COMPLETE_DUPLICATE;
        }
        if (thenLen == duplicatedStatements || elseLen == duplicatedStatements) {
          return CommonPartType.WHOLE_BRANCH;
        }
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
  }

  private static class LocalEquivalenceChecker extends EquivalenceChecker {
    private final Set<PsiLocalVariable> myLocalVariables;
    private final Map<PsiLocalVariable, String> mySubstitutionTable = new HashMap<>(); // From else variable to then variable name

    private LocalEquivalenceChecker(Set<PsiLocalVariable> variables) {myLocalVariables = variables;}

    public Map<PsiLocalVariable, String> getSubstitutionTable() {
      return mySubstitutionTable;
    }

    public boolean topLevelVarsAreEqualNotConsideringInitializers(@NotNull PsiStatement first,
                                                                  @NotNull PsiStatement second) {
      PsiLocalVariable localVariable1 = extractVariable(first);
      PsiLocalVariable localVariable2 = extractVariable(second);
      if(localVariable1 == null || localVariable2 == null) return false;
      if(!myLocalVariables.contains(localVariable1) || !myLocalVariables.contains(localVariable2)) {
        return false;
      }
      if (!equalNotConsideringInitializer(localVariable1, localVariable2)) return false;
      return true;
    }

    private boolean equalNotConsideringInitializer(@NotNull PsiLocalVariable localVariable1, @NotNull PsiLocalVariable localVariable2) {

      PsiModifierList firstModifierList = localVariable1.getModifierList();
      PsiModifierList secondModifierList = localVariable2.getModifierList();
      if(firstModifierList != null || secondModifierList != null) {
        if (firstModifierList == null || secondModifierList == null) {
          return false;
        }
        String firstModifierListText = firstModifierList.getText();
        String secondModifierListText = secondModifierList.getText();
        if(firstModifierListText != null && !firstModifierListText.equals(secondModifierListText)) {
          return false;
        }
      }
      PsiAnnotation[] firstAnnotations = localVariable1.getAnnotations();
      if (firstAnnotations.length != localVariable2.getAnnotations().length || firstAnnotations.length != 0) return false;
      PsiType firstType = localVariable1.getType();
      if(!firstType.equals(localVariable2.getType())) return false;
      String firstName = localVariable1.getName();
      String secondName = localVariable2.getName();
      if (firstName == null || !firstName.equals(secondName)) {
        mySubstitutionTable.put(localVariable2, firstName);
      }
      return true;
    }

    @Override
    protected Match localVariablesAreEquivalent(@NotNull PsiLocalVariable localVariable1,
                                                @NotNull PsiLocalVariable localVariable2) {
      if(!myLocalVariables.contains(localVariable1) || !myLocalVariables.contains(localVariable2)) {
        return super.localVariablesAreEquivalent(localVariable1, localVariable2);
      }
      if (!equalNotConsideringInitializer(localVariable1, localVariable2)) return EXACT_MISMATCH;
      PsiExpression firstInitializer = localVariable1.getInitializer();
      PsiExpression secondInitializer = localVariable2.getInitializer();
      return expressionsMatch(firstInitializer, secondInitializer);
    }

    @Override
    protected Match referenceExpressionsMatch(PsiReferenceExpression first,
                                              PsiReferenceExpression second) {
      PsiElement firstElement = first.resolve();
      PsiElement secondElement = second.resolve();
      if (firstElement instanceof PsiLocalVariable &&
          secondElement instanceof PsiLocalVariable &&
          myLocalVariables.contains(firstElement) &&
          myLocalVariables.contains(secondElement)) {
        PsiLocalVariable secondVar = (PsiLocalVariable)secondElement;
        PsiLocalVariable firstVar = (PsiLocalVariable)firstElement;
        if (firstVar.getType().equals(secondVar.getType())) {
          String firstVarName = firstVar.getName();
          String secondVarName = secondVar.getName();
          if (firstVarName != null && secondVarName != null) {
            if (firstVarName.equals(secondVarName) || firstVarName.equals(mySubstitutionTable.get(secondVar))) {
              return EXACT_MATCH;
            } else {
              return EXACT_MISMATCH;
            }
          }
        }
      }
      return super.referenceExpressionsMatch(first, second);
    }
  }
}
