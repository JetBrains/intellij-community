// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class ConvertSwitchToIfIntention implements IntentionAction {
  private final PsiSwitchStatement mySwitchStatement;

  public ConvertSwitchToIfIntention(@NotNull PsiSwitchStatement switchStatement) {
    mySwitchStatement = switchStatement;
  }

  @NotNull
  @Override
  public String getText() {
    return CommonQuickFixBundle.message("fix.replace.x.with.y", PsiKeyword.SWITCH, PsiKeyword.IF);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return isAvailable(mySwitchStatement);
  }

  public static boolean isAvailable(PsiSwitchStatement switchStatement) {
    final PsiCodeBlock body = switchStatement.getBody();
    return body != null && !body.isEmpty() && BreakConverter.from(switchStatement) != null && !mayFallThroughNonTerminalDefaultCase(body);
  }

  private static boolean mayFallThroughNonTerminalDefaultCase(PsiCodeBlock body) {
    List<PsiSwitchLabelStatementBase> labels = PsiTreeUtil.getChildrenOfTypeAsList(body, PsiSwitchLabelStatementBase.class);
    return StreamEx.of(labels).pairMap((prev, next) -> {
        if (prev.isDefaultCase()) {
          Set<PsiSwitchLabelStatementBase> targets = getFallThroughTargets(body);
          return targets.contains(prev) || targets.contains(next);
        }
        return false;
      }).has(true);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    doProcessIntention(mySwitchStatement);
  }

  @NotNull
  @Override
  public PsiElement getElementToMakeWritable(@NotNull PsiFile file) {
    return mySwitchStatement;
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  public static void doProcessIntention(@NotNull PsiSwitchStatement switchStatement) {
    final PsiExpression switchExpression = switchStatement.getExpression();
    if (switchExpression == null) {
      return;
    }
    final PsiType switchExpressionType = RefactoringUtil.getTypeByExpressionWithExpectedType(switchExpression);
    if (switchExpressionType == null) {
      return;
    }
    CommentTracker commentTracker = new CommentTracker();
    final boolean isSwitchOnString = switchExpressionType.equalsToText(CommonClassNames.JAVA_LANG_STRING);
    boolean useEquals = isSwitchOnString;
    if (!useEquals) {
      final PsiClass aClass = PsiUtil.resolveClassInType(switchExpressionType);
      String fqn;
      useEquals = aClass != null && !aClass.isEnum() && ((fqn = aClass.getQualifiedName()) == null || !TypeConversionUtil.isPrimitiveWrapper(fqn));
    }
    PsiCodeBlock body = switchStatement.getBody();
    if (body == null) {
      return;
    }
    // Should execute getFallThroughTargets and statementMayCompleteNormally before converting breaks
    Set<PsiSwitchLabelStatementBase> fallThroughTargets = getFallThroughTargets(body);
    boolean mayCompleteNormally = ControlFlowUtils.statementMayCompleteNormally(switchStatement);
    BreakConverter converter = BreakConverter.from(switchStatement);
    if (converter == null) return;
    converter.process();
    final List<SwitchStatementBranch> allBranches = extractBranches(commentTracker, body, fallThroughTargets);

    final String declarationString;
    final boolean hadSideEffects;
    final String expressionText;
    final Project project = switchStatement.getProject();
    int totalCases = allBranches.stream().mapToInt(br -> br.getCaseValues().size()).sum();
    if (totalCases > 0) {
      commentTracker.markUnchanged(switchExpression);
    }
    if (totalCases > 1 && RemoveUnusedVariableUtil.checkSideEffects(switchExpression, null, new ArrayList<>())) {
      hadSideEffects = true;

      final String variableName = new VariableNameGenerator(switchExpression, VariableKind.LOCAL_VARIABLE)
        .byExpression(switchExpression).byType(switchExpressionType).byName(isSwitchOnString ? "s" : "i").generate(true);
      expressionText = variableName;
      declarationString = switchExpressionType.getCanonicalText() + ' ' + variableName + " = " + switchExpression.getText() + ';';
    }
    else {
      hadSideEffects = false;
      declarationString = null;
      expressionText = ParenthesesUtils.getPrecedence(switchExpression) > ParenthesesUtils.EQUALITY_PRECEDENCE
                       ? '(' + switchExpression.getText() + ')'
                       : switchExpression.getText();
    }

    final StringBuilder ifStatementBuilder = new StringBuilder();
    boolean firstBranch = true;
    SwitchStatementBranch defaultBranch = null;
    for (SwitchStatementBranch branch : allBranches) {
      if (branch.isDefault()) {
        defaultBranch = branch;
      }
      else {
        dumpBranch(branch, expressionText, firstBranch, useEquals, ifStatementBuilder, commentTracker);
        firstBranch = false;
      }
    }
    boolean unwrapDefault = false;
    if (defaultBranch != null) {
      unwrapDefault = defaultBranch.isAlwaysExecuted() || (switchStatement.getParent() instanceof PsiCodeBlock && !mayCompleteNormally);
      if (!unwrapDefault && defaultBranch.hasStatements()) {
        ifStatementBuilder.append("else ");
        dumpBody(defaultBranch, ifStatementBuilder, commentTracker);
      }
    }
    String ifStatementText = ifStatementBuilder.toString();
    if (ifStatementText.isEmpty()) {
      if (!unwrapDefault) return;
      ifStatementText = ";";
    }
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    PsiCodeBlock parent = ObjectUtils.tryCast(switchStatement.getParent(), PsiCodeBlock.class);
    if (unwrapDefault || hadSideEffects) {
      if (parent == null) {
        commentTracker.grabComments(switchStatement);
        switchStatement = BlockUtils.expandSingleStatementToBlockStatement(switchStatement);
        parent = (PsiCodeBlock)(switchStatement.getParent());
      }
    }
    JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(project);
    if (hadSideEffects) {
      final PsiStatement declarationStatement = factory.createStatementFromText(declarationString, switchStatement);
      javaCodeStyleManager.shortenClassReferences(parent.addBefore(declarationStatement, switchStatement));
    }
    final PsiStatement ifStatement = factory.createStatementFromText(ifStatementText, switchStatement);
    if (unwrapDefault) {
      PsiElement addedIf = parent.addBefore(ifStatement, switchStatement);
      StringBuilder sb = new StringBuilder();
      dumpBody(defaultBranch, sb, commentTracker);
      PsiBlockStatement defaultBody = (PsiBlockStatement)factory.createStatementFromText(sb.toString(), switchStatement);
      if (!BlockUtils.containsConflictingDeclarations(Objects.requireNonNull(switchStatement.getBody()), parent)) {
        commentTracker.grabComments(switchStatement);
        BlockUtils.inlineCodeBlock(switchStatement, defaultBody.getCodeBlock());
      }
      else {
        commentTracker.replace(switchStatement, defaultBody);
      }
      commentTracker.insertCommentsBefore(addedIf);
      if (ifStatementText.equals(";")) {
        addedIf.delete();
      }
      else {
        javaCodeStyleManager.shortenClassReferences(addedIf);
      }
    }
    else {
      javaCodeStyleManager.shortenClassReferences(commentTracker.replaceAndRestoreComments(switchStatement, ifStatement));
    }
  }

  @NotNull
  private static List<SwitchStatementBranch> extractBranches(CommentTracker commentTracker,
                                                             PsiCodeBlock body,
                                                             Set<PsiSwitchLabelStatementBase> fallThroughTargets) {
    final List<SwitchStatementBranch> openBranches = new ArrayList<>();
    final Set<PsiElement> declaredElements = new HashSet<>();
    final List<SwitchStatementBranch> allBranches = new ArrayList<>();
    SwitchStatementBranch currentBranch = null;
    final PsiElement[] children = body.getChildren();
    List<PsiSwitchLabelStatementBase> labels = PsiTreeUtil.getChildrenOfTypeAsList(body, PsiSwitchLabelStatementBase.class);
    boolean defaultAlwaysExecuted = !labels.isEmpty() &&
                                    Objects.requireNonNull(ContainerUtil.getLastItem(labels)).isDefaultCase() &&
                                    fallThroughTargets.containsAll(labels.subList(1, labels.size()));
    for (int i = 1; i < children.length - 1; i++) {
      final PsiElement statement = children[i];
      if (statement instanceof PsiSwitchLabelStatement) {
        final PsiSwitchLabelStatement label = (PsiSwitchLabelStatement)statement;
        if (currentBranch == null || !fallThroughTargets.contains(statement)) {
          openBranches.clear();
          currentBranch = new SwitchStatementBranch();
          currentBranch.addPendingDeclarations(declaredElements);
          allBranches.add(currentBranch);
          openBranches.add(currentBranch);
        }
        else if (currentBranch.hasStatements()) {
          currentBranch = new SwitchStatementBranch();
          allBranches.add(currentBranch);
          openBranches.add(currentBranch);
        }
        if (label.isDefaultCase() && defaultAlwaysExecuted) {
          openBranches.retainAll(Collections.singleton(currentBranch));
        }
        currentBranch.addCaseValues(label, defaultAlwaysExecuted, commentTracker);
      }
      else if (statement instanceof PsiSwitchLabeledRuleStatement) {
        openBranches.clear();
        PsiSwitchLabeledRuleStatement rule = (PsiSwitchLabeledRuleStatement)statement;
        currentBranch = new SwitchStatementBranch();

        PsiStatement ruleBody = rule.getBody();
        if (ruleBody != null) {
          currentBranch.addStatement(ruleBody);
        }
        currentBranch.addCaseValues(rule, defaultAlwaysExecuted, commentTracker);
        openBranches.add(currentBranch);
        allBranches.add(currentBranch);
      }
      else {
        if (statement instanceof PsiStatement) {
          if (statement instanceof PsiDeclarationStatement) {
            final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)statement;
            Collections.addAll(declaredElements, declarationStatement.getDeclaredElements());
          }
          for (SwitchStatementBranch branch : openBranches) {
            branch.addStatement((PsiStatement)statement);
          }
        }
        else {
          for (SwitchStatementBranch branch : openBranches) {
            if (statement instanceof PsiWhiteSpace) {
              branch.addWhiteSpace(statement);
            }
            else {
              branch.addComment(statement);
            }
          }
        }
      }
    }
    return allBranches;
  }

  private static Set<PsiSwitchLabelStatementBase> getFallThroughTargets(PsiCodeBlock body) {
    return StreamEx.of(body.getStatements())
      .pairMap((s1, s2) -> s2 instanceof PsiSwitchLabelStatement && !(s1 instanceof PsiSwitchLabeledRuleStatement) && 
                           ControlFlowUtils.statementMayCompleteNormally(s1) ? (PsiSwitchLabelStatement)s2 : null)
      .nonNull().collect(Collectors.toSet());
  }

  private static void dumpBranch(SwitchStatementBranch branch,
                                 String expressionText,
                                 boolean firstBranch,
                                 boolean useEquals,
                                 @NonNls StringBuilder out,
                                 CommentTracker commentTracker) {
    if (!firstBranch) {
      out.append("else ");
    }
    dumpCaseValues(expressionText, branch.getCaseValues(), useEquals, out);
    dumpBody(branch, out, commentTracker);
  }

  private static void dumpCaseValues(String expressionText,
                                     List<String> caseValues,
                                     boolean useEquals,
                                     @NonNls StringBuilder out) {
    out.append("if(");
    boolean firstCaseValue = true;
    for (String caseValue : caseValues) {
      if (!firstCaseValue) {
        out.append("||");
      }
      firstCaseValue = false;
      if (useEquals) {
        out.append(caseValue).append(".equals(").append(expressionText).append(')');
      }
      else if (caseValue.equals("true")) {
        out.append(expressionText);
      }
      else if (caseValue.equals("false")) {
        out.append("!(").append(expressionText).append(")");
      }
      else {
        out.append(expressionText).append("==").append(caseValue);
      }
    }
    out.append(')');
  }

  private static void dumpBody(SwitchStatementBranch branch, @NonNls StringBuilder out, CommentTracker commentTracker) {
    final List<PsiElement> bodyStatements = branch.getBodyElements();
    out.append('{');
    if (!bodyStatements.isEmpty()) {
      PsiElement firstBodyElement = bodyStatements.get(0);
      PsiElement prev = PsiTreeUtil.skipWhitespacesAndCommentsBackward(firstBodyElement);
      if (prev instanceof PsiSwitchLabelStatementBase) {
        PsiExpressionList values = ((PsiSwitchLabelStatementBase)prev).getCaseValues();
        if (values != null) {
          out.append(CommentTracker.commentsBetween(values, firstBodyElement));
        }
      }
    }
    for (PsiElement element : branch.getPendingDeclarations()) {
      if (ReferencesSearch.search(element, new LocalSearchScope(bodyStatements.toArray(PsiElement.EMPTY_ARRAY))).findFirst() != null) {
        if (element instanceof PsiVariable) {
          PsiVariable var = (PsiVariable)element;
          out.append(var.getType().getCanonicalText()).append(' ').append(var.getName()).append(';');
        } else {
          // Class
          out.append(element.getText());
        }
      }
    }

    for (PsiElement bodyStatement : bodyStatements) {
      if (bodyStatement instanceof PsiBlockStatement) {
        final PsiBlockStatement blockStatement = (PsiBlockStatement)bodyStatement;
        final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
        PsiElement start = PsiTreeUtil.skipWhitespacesForward(codeBlock.getFirstBodyElement());
        PsiElement end = PsiTreeUtil.skipWhitespacesBackward(codeBlock.getLastBodyElement());
        if (start != null && end != null && start != codeBlock.getRBrace()) {
          for (PsiElement child = start; child != null; child = child.getNextSibling()) {
            out.append(commentTracker.text(child));
            if (child == end) break;
          }
        }
      }
      else {
        out.append(commentTracker.text(bodyStatement));
      }
    }
    out.append("\n").append("}");
  }
}
