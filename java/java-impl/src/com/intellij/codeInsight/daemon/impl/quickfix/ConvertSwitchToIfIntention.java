// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiPrecedenceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class ConvertSwitchToIfIntention extends PsiUpdateModCommandAction<PsiSwitchStatement> {
  public ConvertSwitchToIfIntention(@NotNull PsiSwitchStatement switchStatement) {
    super(switchStatement);
  }

  @Override
  public @NotNull String getFamilyName() {
    return CommonQuickFixBundle.message("fix.replace.x.with.y", PsiKeyword.SWITCH, PsiKeyword.IF);
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiSwitchStatement statement) {
    return isAvailable(statement) ? Presentation.of(getFamilyName()).withFixAllOption(this) : null;
  }

  public static boolean isAvailable(@NotNull PsiSwitchStatement switchStatement) {
    final PsiCodeBlock body = switchStatement.getBody();
    return body != null && !body.isEmpty() && BreakConverter.from(switchStatement) != null && !mayFallThroughNonTerminalDefaultCase(body);
  }

  private static boolean mayFallThroughNonTerminalDefaultCase(PsiCodeBlock body) {
    List<PsiSwitchLabelStatementBase> labels = PsiTreeUtil.getChildrenOfTypeAsList(body, PsiSwitchLabelStatementBase.class);
    return StreamEx.of(labels).pairMap((prev, next) -> {
      if (SwitchUtils.isDefaultLabel(prev)) {
        Set<PsiSwitchLabelStatementBase> targets = getFallThroughTargets(body);
        return targets.contains(prev) || targets.contains(next);
      }
      return false;
    }).has(true);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiSwitchStatement statement, @NotNull ModPsiUpdater updater) {
    doProcessIntention(statement);
  }

  public static void doProcessIntention(@NotNull PsiSwitchStatement switchStatement) {
    final PsiExpression switchExpression = switchStatement.getExpression();
    if (switchExpression == null) {
      return;
    }
    final PsiType switchExpressionType = CommonJavaRefactoringUtil.getTypeByExpressionWithExpectedType(switchExpression);
    if (switchExpressionType == null) {
      return;
    }
    CommentTracker commentTracker = new CommentTracker();
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
    final List<SwitchStatementBranch> allBranches = extractBranches(body, fallThroughTargets);
    boolean needNullCheck = false;
    final boolean isSwitchOnString = switchExpressionType.equalsToText(CommonClassNames.JAVA_LANG_STRING);
    boolean useEquals = isSwitchOnString;
    if (!useEquals) {
      final PsiClass aClass = PsiUtil.resolveClassInType(switchExpressionType);
      if (aClass != null) {
        String fqn = aClass.getQualifiedName();
        useEquals = !aClass.isEnum() && (fqn == null || !TypeConversionUtil.isPrimitiveWrapper(fqn));
        Nullability nullability = NullabilityUtil.getExpressionNullability(switchExpression, true);
        needNullCheck =
          nullability != Nullability.NOT_NULL && !(switchExpressionType instanceof PsiPrimitiveType) && !hasNullCase(allBranches);
      }
    }

    final String declarationString;
    final boolean hadSideEffects;
    final String expressionText;
    final Project project = switchStatement.getProject();
    int totalCases = allBranches.stream().mapToInt(br -> br.getCaseElements().size()).sum();
    List<PsiExpression> sideEffectExpressions = SideEffectChecker.extractSideEffectExpressions(switchExpression);
    if (totalCases > 0) {
      commentTracker.markUnchanged(switchExpression);
    }
    if (totalCases > 1 && !sideEffectExpressions.isEmpty()) {
      hadSideEffects = true;

      final String variableName = new VariableNameGenerator(switchExpression, VariableKind.LOCAL_VARIABLE)
        .byExpression(switchExpression).byType(switchExpressionType).byName(isSwitchOnString ? "s" : "i").generate(true);
      expressionText = variableName;
      declarationString = switchExpressionType.getCanonicalText() + ' ' + variableName + " = " + switchExpression.getText() + ';';
    }
    else {
      hadSideEffects = totalCases == 0 && !sideEffectExpressions.isEmpty();
      declarationString = null;
      int exprPrecedence = useEquals ? ParenthesesUtils.METHOD_CALL_PRECEDENCE : ParenthesesUtils.EQUALITY_PRECEDENCE;
      expressionText = ParenthesesUtils.getPrecedence(switchExpression) > exprPrecedence
                       ? '(' + switchExpression.getText() + ')'
                       : switchExpression.getText();
    }

    final StringBuilder ifStatementBuilder = new StringBuilder();
    boolean firstBranch = true;
    SwitchStatementBranch defaultBranch = null;
    boolean java7plus = PsiUtil.getLanguageLevel(switchStatement).isAtLeast(LanguageLevel.JDK_1_7);
    if (switchStatement.getExpression() != null && TypeConversionUtil.isBooleanType(switchStatement.getExpression().getType())) {
      for (SwitchStatementBranch branch : allBranches) {
        List<SwitchStatementBranch.LabelElement> elements = branch.getCaseElements();
        if (elements.size() == 1) {
          PsiCaseLabelElement element = elements.get(0).element();
          if (element instanceof PsiExpression expression) {
            Object o = JavaPsiFacade.getInstance(expression.getProject()).getConstantEvaluationHelper()
              .computeConstantExpression(expression, false);
            if (o instanceof Boolean b && b == Boolean.FALSE) {
              defaultBranch = branch;
              break;
            }
          }
        }
      }
    }
    for (SwitchStatementBranch branch : allBranches) {
      if (branch == defaultBranch) continue;
      if (branch.isDefault()) {
        defaultBranch = branch;
      }
      else {
        dumpBranch(branch, expressionText, firstBranch, useEquals, needNullCheck && java7plus, switchExpressionType, ifStatementBuilder,
                   commentTracker);
        firstBranch = false;
      }
    }
    if (needNullCheck && !java7plus) {
      if (!firstBranch) {
        ifStatementBuilder.append("else ");
      }
      ifStatementBuilder.append("if(").append(expressionText).append("==null) { throw new NullPointerException(); }");
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
      if (declarationString == null) {
        sideEffectExpressions.forEach(commentTracker::markUnchanged);
        PsiStatement[] statements = StatementExtractor.generateStatements(sideEffectExpressions, switchExpression);
        for (PsiStatement statement : statements) {
          javaCodeStyleManager.shortenClassReferences(parent.addBefore(statement, switchStatement));
        }
      } else {
        final PsiStatement declarationStatement = factory.createStatementFromText(declarationString, switchStatement);
        javaCodeStyleManager.shortenClassReferences(parent.addBefore(declarationStatement, switchStatement));
      }
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

  private static boolean hasNullCase(@NotNull List<SwitchStatementBranch> allBranches) {
    return ContainerUtil.or(allBranches, br -> ContainerUtil.or(br.getCaseElements(), el -> el.element() instanceof PsiExpression expr &&
                                                                                            ExpressionUtils.isNullLiteral(expr)));
  }

  private static @NotNull List<SwitchStatementBranch> extractBranches(PsiCodeBlock body,
                                                                      Set<PsiSwitchLabelStatementBase> fallThroughTargets) {
    final List<SwitchStatementBranch> openBranches = new ArrayList<>();
    final Set<PsiElement> declaredElements = new HashSet<>();
    final List<SwitchStatementBranch> allBranches = new ArrayList<>();
    SwitchStatementBranch currentBranch = null;
    final PsiElement[] children = body.getChildren();
    List<PsiSwitchLabelStatementBase> labels = PsiTreeUtil.getChildrenOfTypeAsList(body, PsiSwitchLabelStatementBase.class);
    boolean defaultAlwaysExecuted = !labels.isEmpty() &&
                                    SwitchUtils.isDefaultLabel(ContainerUtil.getLastItem(labels)) &&
                                    fallThroughTargets.containsAll(labels.subList(1, labels.size()));
    for (int i = 1; i < children.length - 1; i++) {
      final PsiElement statement = children[i];
      if (statement instanceof PsiSwitchLabelStatement label) {
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
        if (SwitchUtils.isDefaultLabel(label) && defaultAlwaysExecuted) {
          openBranches.retainAll(Collections.singleton(currentBranch));
        }
        currentBranch.addCaseValues(label, defaultAlwaysExecuted);
      }
      else if (statement instanceof PsiSwitchLabeledRuleStatement rule) {
        openBranches.clear();
        currentBranch = new SwitchStatementBranch();

        PsiStatement ruleBody = rule.getBody();
        if (ruleBody != null) {
          currentBranch.addStatement(ruleBody);
        }
        currentBranch.addCaseValues(rule, defaultAlwaysExecuted);
        openBranches.add(currentBranch);
        allBranches.add(currentBranch);
      }
      else {
        if (statement instanceof PsiStatement) {
          if (statement instanceof PsiDeclarationStatement declarationStatement) {
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
      .pairMap((s1, s2) -> s2 instanceof PsiSwitchLabelStatement labelStatement && !(s1 instanceof PsiSwitchLabeledRuleStatement) &&
                           ControlFlowUtils.statementMayCompleteNormally(s1) ? labelStatement : null)
      .nonNull().collect(Collectors.toSet());
  }

  private static void dumpBranch(SwitchStatementBranch branch,
                                 String expressionText,
                                 boolean firstBranch,
                                 boolean useEquals,
                                 boolean useRequireNonNullMethod,
                                 @Nullable PsiType switchExpressionType,
                                 @NonNls StringBuilder out,
                                 CommentTracker commentTracker) {
    if (!firstBranch) {
      out.append("else ");
    }
    dumpCaseValues(expressionText, branch.getCaseElements(), firstBranch, useEquals, useRequireNonNullMethod, switchExpressionType,
                   commentTracker, out);
    dumpBody(branch, out, commentTracker);
  }

  private static void dumpCaseValues(String expressionText,
                                     List<SwitchStatementBranch.LabelElement> caseElements,
                                     boolean firstBranch,
                                     boolean useEquals,
                                     boolean useRequireNonNullMethod,
                                     @Nullable PsiType switchExpressionType,
                                     @NotNull CommentTracker commentTracker,
                                     @NonNls StringBuilder out) {
    out.append("if(");
    boolean firstCaseValue = true;
    for (SwitchStatementBranch.LabelElement element : caseElements) {
      if (!firstCaseValue) {
        out.append("||");
      }
      final String newExpressionText =
        firstBranch && firstCaseValue && useRequireNonNullMethod &&
        PsiPrimitiveType.getOptionallyUnboxedType(switchExpressionType) == null
        ? "java.util.Objects.requireNonNull(" + expressionText + ")"
        : expressionText;
      firstCaseValue = false;
      PsiCaseLabelElement caseElement = element.element();
      if (caseElement instanceof PsiExpression psiExpression) {
        PsiExpression caseExpression = PsiUtil.skipParenthesizedExprDown(psiExpression);
        String caseValue = getCaseValueText(caseExpression, commentTracker);
        if (useEquals && caseExpression != null && !(caseExpression.getType() instanceof PsiPrimitiveType)) {
          if (PsiPrecedenceUtil.getPrecedence(caseExpression) > PsiPrecedenceUtil.METHOD_CALL_PRECEDENCE) {
            caseValue = "(" + caseValue + ")";
          }
          out.append(expressionText).append(".equals(").append(caseValue).append(')');
        }
        else if (caseValue.equals("true")) {
          if (!useRequireNonNullMethod && !(switchExpressionType instanceof PsiPrimitiveType)) {
            out.append(newExpressionText).append("== Boolean.TRUE");
          }
          else {
            out.append(newExpressionText);
          }
        }
        else if (caseValue.equals("false")) {
          if (!useRequireNonNullMethod && !(switchExpressionType instanceof PsiPrimitiveType)) {
            out.append(newExpressionText).append("== Boolean.FALSE");
          }
          else {
            out.append("!(").append(newExpressionText).append(")");
          }
        }
        else {
          out.append(newExpressionText).append("==").append(caseValue);
        }
      }
      else {
        final String patternCondition;
        PsiExpression guard = element.guard();
        if (caseElement instanceof PsiPattern pattern) {
          patternCondition = createIfCondition(pattern, guard, newExpressionText, commentTracker);
        }
        else {
          patternCondition = null;
        }
        if (patternCondition != null) {
          out.append(patternCondition);
        }
        else {
          //incomplete/red code
          out.append(caseElement.getText());
        }
      }
    }
    out.append(')');
  }

  private static @Nullable String createIfCondition(PsiPattern pattern,
                                                    @Nullable PsiExpression guard,
                                                    String expressionText,
                                                    CommentTracker commentTracker) {
    String patternCondition = null;
    if (pattern instanceof PsiTypeTestPattern typeTestPattern) {
      patternCondition = createIfCondition(typeTestPattern, expressionText, commentTracker);
    }
    else if (pattern instanceof PsiDeconstructionPattern deconstructionPattern) {
      patternCondition = createIfCondition(deconstructionPattern, expressionText, commentTracker);
    }
    if (guard == null || patternCondition == null) return patternCondition;
    return patternCondition +
           "&&" +
           commentTracker.textWithComments(guard, ParenthesesUtils.AND_PRECEDENCE);
  }

  private static @NotNull String createIfCondition(PsiDeconstructionPattern deconstructionPattern,
                                                   String expressionText,
                                                   CommentTracker commentTracker) {
    return expressionText + " instanceof " + commentTracker.text(deconstructionPattern);
  }

  private static @Nullable String createIfCondition(PsiTypeTestPattern typeTestPattern,
                                                    String expressionText,
                                                    CommentTracker commentTracker) {
    PsiTypeElement checkType = typeTestPattern.getCheckType();
    if (checkType == null) return null;
    String typeText = commentTracker.textWithComments(checkType);
    PsiPatternVariable variable = typeTestPattern.getPatternVariable();
    if (variable == null) return null;
    PsiElement context = PsiTreeUtil.getParentOfType(variable, PsiSwitchStatement.class);
    boolean isUsedPatternVariable = VariableAccessUtils.variableIsUsed(variable, context);
    PsiIdentifier identifier = variable.getNameIdentifier();
    String variableName = isUsedPatternVariable ? commentTracker.textWithComments(identifier) : commentTracker.commentsBefore(identifier);
    return expressionText + " instanceof " + typeText + " " + variableName;
  }

  private static @NotNull String getCaseValueText(@Nullable PsiExpression value, @NotNull CommentTracker commentTracker) {
    if (value == null) {
      return "";
    }
    if (!(value instanceof PsiReferenceExpression referenceExpression)) {
      return commentTracker.text(value);
    }
    final PsiElement target = referenceExpression.resolve();

    if (!(target instanceof PsiEnumConstant enumConstant)) {
      return commentTracker.text(value);
    }
    final PsiClass aClass = enumConstant.getContainingClass();
    if (aClass == null) {
      return commentTracker.text(value);
    }

    String qualifiedName = ObjectUtils.notNull(aClass.getQualifiedName(), Objects.requireNonNull(aClass.getName()));
    return qualifiedName + '.' + commentTracker.text(referenceExpression);
  }

  private static void dumpBody(SwitchStatementBranch branch, @NonNls StringBuilder out, CommentTracker commentTracker) {
    final List<PsiElement> bodyStatements = branch.getBodyElements();
    out.append('{');
    if (!bodyStatements.isEmpty()) {
      PsiElement firstBodyElement = bodyStatements.get(0);
      PsiElement prev = PsiTreeUtil.skipWhitespacesAndCommentsBackward(firstBodyElement);
      if (prev instanceof PsiSwitchLabelStatementBase labelStatement) {
        PsiCaseLabelElementList values = labelStatement.getCaseLabelElementList();
        if (values != null) {
          out.append(CommentTracker.commentsBetween(values, firstBodyElement));
        }
      }
    }
    for (PsiElement element : branch.getPendingDeclarations()) {
      if (ReferencesSearch.search(element, new LocalSearchScope(bodyStatements.toArray(PsiElement.EMPTY_ARRAY))).findFirst() != null) {
        if (element instanceof PsiVariable var) {
          out.append(var.getType().getCanonicalText()).append(' ').append(var.getName()).append(';');
        }
        else {
          // Class
          out.append(element.getText());
        }
      }
    }

    for (PsiElement bodyStatement : bodyStatements) {
      if (bodyStatement instanceof PsiBlockStatement blockStatement) {
        final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
        PsiElement start = PsiTreeUtil.skipWhitespacesForward(codeBlock.getLBrace());
        PsiElement end = PsiTreeUtil.skipWhitespacesBackward(codeBlock.getRBrace());
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
