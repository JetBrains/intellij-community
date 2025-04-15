// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.migration;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.EnhancedSwitchMigrationInspection;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.impl.source.tree.java.PsiEmptyStatementImpl;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.JavaPsiPatternUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.*;
import com.siyeh.ig.psiutils.SwitchUtils.IfStatementBranch;
import one.util.streamex.StreamEx;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;

public final class IfCanBeSwitchInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public int minimumBranches = 3;

  @SuppressWarnings("PublicField")
  public boolean suggestIntSwitches = false;

  @SuppressWarnings("PublicField")
  public boolean suggestEnumSwitches = false;

  @SuppressWarnings("PublicField")
  public boolean onlySuggestNullSafe = true;

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("if.can.be.switch.problem.descriptor");
  }

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    if (infos.length == 0 || !(infos[0] instanceof Integer)) return null;
    int additionalIfStatementsCount = (Integer)infos[0];
    return new IfCanBeSwitchFix(additionalIfStatementsCount);
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return OptPane.pane(
      OptPane.number("minimumBranches", InspectionGadgetsBundle.message("if.can.be.switch.minimum.branch.option"), 1, 100),
      OptPane.checkbox("suggestIntSwitches", InspectionGadgetsBundle.message("if.can.be.switch.int.option")),
      OptPane.checkbox("suggestEnumSwitches", InspectionGadgetsBundle.message("if.can.be.switch.enum.option")),
      OptPane.checkbox("onlySuggestNullSafe", InspectionGadgetsBundle.message("if.can.be.switch.null.safe.option"))
    );
  }

  public void setOnlySuggestNullSafe(boolean onlySuggestNullSafe) {
    this.onlySuggestNullSafe = onlySuggestNullSafe;
  }

  public static @IntentionFamilyName @NotNull String getReplaceWithSwitchFixName(){
    return CommonQuickFixBundle.message("fix.replace.x.with.y", JavaKeywords.IF, JavaKeywords.SWITCH);
  }

  private static class IfCanBeSwitchFix extends PsiUpdateModCommandQuickFix {

    private final int myAdditionalIfStatementsCount;

    IfCanBeSwitchFix(int additionalIfStatementsCount) {
      this.myAdditionalIfStatementsCount = additionalIfStatementsCount;
    }

    @Override
    public @NotNull String getFamilyName() {
      return getReplaceWithSwitchFixName();
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      final PsiElement element = startElement.getParent();
      if (!(element instanceof PsiIfStatement ifStatement)) {
        return;
      }
      ifStatement = concatenateIfStatements(ifStatement);
      if (PsiUtil.isAvailable(JavaFeature.PATTERNS_IN_SWITCH, ifStatement)) {
        for (PsiIfStatement ifStatementInChain : getAllConditionalBranches(ifStatement)) {
          replaceCastsWithPatternVariable(ifStatementInChain);
        }
      }
      replaceIfWithSwitch(ifStatement, updater);
    }

    private @NotNull PsiIfStatement concatenateIfStatements(@NotNull PsiIfStatement originalIfStatement) {
      if (myAdditionalIfStatementsCount == 0) return originalIfStatement;
      StringBuilder sb = new StringBuilder();
      CommentTracker commentTracker = new CommentTracker();
      PsiIfStatement currentStatement = originalIfStatement;
      int currentAdditionalIfStatementsCount = 0;
      List<PsiElement> toDeleteElements = new ArrayList<>();
      boolean addText = true;
      while (true) {
        if (addText) {
          String text = commentTracker.text(currentStatement);
          sb.append(text);
        }
        PsiStatement elseBranch = currentStatement.getElseBranch();
        if (elseBranch instanceof PsiIfStatement nextIfStatement) {
          addText = false;
          currentStatement = nextIfStatement;
          continue;
        }
        if (elseBranch == null && currentAdditionalIfStatementsCount < myAdditionalIfStatementsCount) {
          PsiIfStatement upperIf =  currentStatement;
          while (upperIf.getParent() instanceof PsiIfStatement parentIfStatement &&
                 parentIfStatement.getElseBranch() == upperIf) {
            upperIf = parentIfStatement;
          }

          PsiElement nextElement = PsiTreeUtil.skipWhitespacesAndCommentsForward(upperIf);
          if (nextElement instanceof PsiIfStatement nextIfStatement) {
            PsiElement nextSibling = upperIf.getNextSibling();
            while (nextSibling != null && nextSibling != nextIfStatement) {
              sb.append(commentTracker.text(nextSibling));
              if (!(nextSibling instanceof PsiWhiteSpace)) {
                toDeleteElements.add(nextSibling);
              }
              nextSibling = nextSibling.getNextSibling();
            }
            addText = true;
            sb.append("\n else \n");
            currentStatement = nextIfStatement;
            currentAdditionalIfStatementsCount++;
            toDeleteElements.add(nextIfStatement);
            continue;
          }
        }
        break;
      }
      for (PsiElement toDelete : toDeleteElements) {
        toDelete.delete();
      }
      PsiElement replaced = commentTracker.replace(originalIfStatement, sb.toString());
      return replaced instanceof PsiIfStatement ? (PsiIfStatement)replaced : originalIfStatement;
    }
  }

  private static List<PsiIfStatement> getAllConditionalBranches(PsiIfStatement ifStatement){
    List<PsiIfStatement> ifStatements = new ArrayList<>();
    while (ifStatement != null) {
      ifStatements.add(ifStatement);
      PsiStatement elseBranch = ifStatement.getElseBranch();
      if (elseBranch instanceof PsiIfStatement) {
        ifStatement = (PsiIfStatement) elseBranch;
      } else {
        ifStatement = null;
      }
    }
    return ifStatements;
  }

  private static void replaceCastsWithPatternVariable(PsiIfStatement ifStatement){
    PsiInstanceOfExpression targetInstanceOf =
      PsiTreeUtil.findChildOfType(ifStatement.getCondition(), PsiInstanceOfExpression.class, false);
    if (targetInstanceOf == null) return;
    if (targetInstanceOf.getPattern() != null) return;
    PsiTypeElement type = targetInstanceOf.getCheckType();
    if (type == null) return;

    List<PsiTypeCastExpression> relatedCastExpressions = new ArrayList<>(getRelatesCastExpressions(ifStatement.getThenBranch(), targetInstanceOf));

    collectRelatedCastsFromIfCondition(ifStatement, targetInstanceOf, relatedCastExpressions);
    PsiLocalVariable castedVariable = null;
    for (PsiTypeCastExpression castExpression : relatedCastExpressions) {
      castedVariable = findCastedLocalVariable(castExpression);
      if (castedVariable != null) break;
    }

    String name = castedVariable != null
                  ? castedVariable.getName()
                  : new VariableNameGenerator(targetInstanceOf, VariableKind.LOCAL_VARIABLE).byType(type.getType()).generate(true);

    CommentTracker ct = new CommentTracker();
    for (PsiTypeCastExpression castExpression : relatedCastExpressions) {
      ct.replace(skipParenthesizedExprUp(castExpression), name);
    }
    if (castedVariable != null) {
      ct.delete(castedVariable);
    }
    ct.replaceExpressionAndRestoreComments(
      targetInstanceOf,
      ct.text(targetInstanceOf.getOperand()) + " instanceof " + ct.text(type) + " " + name
    );
  }

  private static void collectRelatedCastsFromIfCondition(PsiIfStatement ifStatement,
                                                         PsiInstanceOfExpression targetInstanceOf,
                                                         List<PsiTypeCastExpression> relatedCastExpressions) {
    PsiElement current = targetInstanceOf;
    while (true) {
      PsiElement parent = current.getParent();
      if (parent == null || parent == ifStatement) {
        break;
      }
      if (!(parent instanceof PsiPolyadicExpression polyadicExpression) ||
          polyadicExpression.getOperationTokenType() != JavaTokenType.ANDAND) {
        break;
      }
      PsiExpression[] operands = polyadicExpression.getOperands();
      int index = Arrays.asList(operands).indexOf(current);
      if (index == -1 || index == operands.length - 1) {
        break;
      }
      for (int i = index; i < operands.length; i++) {
        relatedCastExpressions.addAll(getRelatesCastExpressions(operands[i], targetInstanceOf));
      }
      current = parent;
    }
  }

  private static @Unmodifiable @NotNull List<PsiTypeCastExpression> getRelatesCastExpressions(PsiElement expression, PsiInstanceOfExpression targetInstanceOf) {
    return SyntaxTraverser.psiTraverser(expression)
      .filter(PsiTypeCastExpression.class)
      .filter(cast -> InstanceOfUtils.findPatternCandidate(cast) == targetInstanceOf)
      .toList();
  }

  private static @Nullable PsiLocalVariable findCastedLocalVariable(PsiTypeCastExpression castExpression) {
    PsiLocalVariable variable = PsiTreeUtil.getParentOfType(castExpression, PsiLocalVariable.class);
    if (variable == null) return null;
    PsiExpression initializer = PsiUtil.skipParenthesizedExprDown(variable.getInitializer());
    if (initializer != castExpression) return null;
    PsiElement scope = PsiUtil.getVariableCodeBlock(variable, null);
    if (scope == null) return null;
    if (!ControlFlowUtil.isEffectivelyFinal(variable, scope)) return null;
    return variable;
  }

  private static PsiElement skipParenthesizedExprUp(@NotNull PsiElement expression) {
    while (expression.getParent() instanceof PsiParenthesizedExpression) {
      expression = expression.getParent();
    }
    return expression;
  }

  private static void replaceIfWithSwitch(PsiIfStatement ifStatement, @NotNull ModPsiUpdater updater) {
    boolean breaksNeedRelabeled = false;
    PsiStatement breakTarget = null;
    String newLabel = "";
    if (ControlFlowUtils.statementContainsNakedBreak(ifStatement)) {
      breakTarget = PsiTreeUtil.getParentOfType(ifStatement, PsiLoopStatement.class, PsiSwitchStatement.class);
      if (breakTarget != null) {
        final PsiElement parent = breakTarget.getParent();
        if (parent instanceof PsiLabeledStatement labeledStatement) {
          newLabel = labeledStatement.getLabelIdentifier().getText();
          breakTarget = labeledStatement;
        }
        else {
          newLabel = SwitchUtils.findUniqueLabelName(ifStatement, "label");
        }
        breaksNeedRelabeled = true;
      }
    }
    final PsiIfStatement statementToReplace = ifStatement;
    final PsiExpression switchExpression = SwitchUtils.getSwitchSelectorExpression(ifStatement.getCondition());
    if (switchExpression == null) {
      return;
    }
    final List<IfStatementBranch> branches = new ArrayList<>(20);
    while (true) {
      final PsiExpression condition = ifStatement.getCondition();
      final PsiStatement thenBranch = ifStatement.getThenBranch();
      final IfStatementBranch ifBranch = new IfStatementBranch(thenBranch, false);
      extractCaseExpressions(condition, switchExpression, ifBranch);
      if (!branches.isEmpty()) {
        extractIfComments(ifStatement, ifBranch);
      }
      extractStatementComments(thenBranch, ifBranch);
      branches.add(ifBranch);
      final PsiStatement elseBranch = ifStatement.getElseBranch();
      if (elseBranch instanceof PsiIfStatement) {
        ifStatement = (PsiIfStatement)elseBranch;
      }
      else if (elseBranch == null) {
        break;
      }
      else {
        final IfStatementBranch elseIfBranch = new IfStatementBranch(elseBranch, true);
        final PsiKeyword elseKeyword = ifStatement.getElseElement();
        extractIfComments(elseKeyword, elseIfBranch);
        extractStatementComments(elseBranch, elseIfBranch);
        branches.add(elseIfBranch);
        break;
      }
    }

    if (ContainerUtil.or(branches, branch -> branch.hasPattern() && !(switchExpression.getType() instanceof PsiPrimitiveType)) ||
        SwitchUtils.isExtendedSwitchSelectorType(switchExpression.getType())) {
      final boolean hasDefaultElse = ContainerUtil.exists(branches, (branch) -> branch.isElse());
      if (!hasDefaultElse && !hasUnconditionalPatternCheck(ifStatement, switchExpression)) {
        branches.add(new IfStatementBranch(new PsiEmptyStatementImpl(), true));
      }
    }
    if (PsiUtil.isAvailable(JavaFeature.PATTERNS_IN_SWITCH, switchExpression)){
      if (getNullability(switchExpression) != Nullability.NOT_NULL && findNullCheckedOperand(statementToReplace) == null) {
        final IfStatementBranch defaultBranch = ContainerUtil.find(branches, (branch) -> branch.isElse());
        final PsiElementFactory factory = PsiElementFactory.getInstance(ifStatement.getProject());
        final PsiExpression condition = factory.createExpressionFromText("null", switchExpression.getContext());
        if (defaultBranch != null){
          if (switchExpressionCanBeNullInsideDefault(switchExpression, defaultBranch)) {
            defaultBranch.addCaseExpression(condition);
          }
        }
        else {
          IfStatementBranch nullBranch = new IfStatementBranch(new PsiEmptyStatementImpl(), !hasUnconditionalPatternCheck(ifStatement, switchExpression));
          nullBranch.addCaseExpression(condition);
          branches.add(nullBranch);
        }
      }
    }

    final @NonNls StringBuilder switchStatementText = new StringBuilder();
    switchStatementText.append("switch(").append(switchExpression.getText()).append("){");
    final PsiType type = switchExpression.getType();
    final boolean castToInt = type != null && type.equalsToText(CommonClassNames.JAVA_LANG_INTEGER);
    for (IfStatementBranch branch : branches) {
      boolean hasConflicts = false;
      for (IfStatementBranch testBranch : branches) {
        if (branch == testBranch) {
          continue;
        }
        if (branch.topLevelDeclarationsConflictWith(testBranch)) {
          hasConflicts = true;
        }
      }
      dumpBranch(branch, castToInt, hasConflicts, breaksNeedRelabeled, newLabel, switchExpression, switchStatementText);
    }
    switchStatementText.append('}');
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(ifStatement.getProject());
    final PsiStatement newStatement = factory.createStatementFromText(switchStatementText.toString(), ifStatement);
    final PsiSwitchStatement replacement = (PsiSwitchStatement)statementToReplace.replace(newStatement);
    updater.moveCaretTo(replacement);
    if (PsiUtil.isAvailable(JavaFeature.ENHANCED_SWITCH, replacement)) {
      final EnhancedSwitchMigrationInspection.SwitchReplacer replacer = EnhancedSwitchMigrationInspection.findSwitchReplacer(replacement);
      if (replacer != null) {
        replacer.replace(replacement);
      }
    }
    if (breaksNeedRelabeled) {
      final PsiLabeledStatement labeledStatement = (PsiLabeledStatement)factory.createStatementFromText(newLabel + ":;", null);
      final PsiStatement statement = labeledStatement.getStatement();
      assert statement != null;
      statement.replace(breakTarget);
      breakTarget.replace(labeledStatement);
    }
  }

  private static boolean switchExpressionCanBeNullInsideDefault(@NotNull PsiExpression switchExpression,
                                                                @NotNull IfStatementBranch branch) {
    EquivalenceChecker equivalenceChecker = EquivalenceChecker.getCanonicalPsiEquivalence();
    var visitor = new JavaRecursiveElementVisitor() {

      private boolean isNull = true;

      @Override
      public void visitElement(@NotNull PsiElement element) {
        if (!isNull) {
          return;
        }
        super.visitElement(element);
      }

      @Override
      public void visitExpression(@NotNull PsiExpression expression) {
        PsiStatement statement = PsiTreeUtil.getParentOfType(expression, PsiStatement.class);
        if (statement != null &&
            //check only high-level statements
            statement.getParent() instanceof PsiCodeBlock codeBlock &&
            codeBlock.getParent() == branch.getStatement() &&
            equivalenceChecker.expressionsAreEquivalent(switchExpression, expression) &&
            getNullability(expression) == Nullability.NOT_NULL) {
          isNull = false;
        }
        super.visitExpression(expression);
      }
    };
    visitor.visitElement(branch.getStatement());
    return visitor.isNull;
  }


  @SafeVarargs
  public static @Nullable <T extends PsiElement> T getPrevSiblingOfType(@Nullable PsiElement element, @NotNull Class<T> aClass,
                                                                        Class<? extends PsiElement> @NotNull ... stopAt) {
    if (element == null) {
      return null;
    }
    PsiElement sibling = element.getPrevSibling();
    while (sibling != null && !aClass.isInstance(sibling)) {
      for (Class<? extends PsiElement> stopClass : stopAt) {
        if (stopClass.isInstance(sibling)) {
          return null;
        }
      }
      sibling = sibling.getPrevSibling();
    }
    //noinspection unchecked
    return (T)sibling;
  }

  private static void extractIfComments(PsiElement element, IfStatementBranch out) {
    PsiComment comment = getPrevSiblingOfType(element, PsiComment.class, PsiStatement.class);
    while (comment != null) {
      out.addComment(getCommentText(comment));
      comment = getPrevSiblingOfType(comment, PsiComment.class, PsiStatement.class);
    }
  }

  private static void extractStatementComments(PsiElement element, IfStatementBranch out) {
    PsiComment comment = getPrevSiblingOfType(element, PsiComment.class, PsiStatement.class, PsiKeyword.class);
    while (comment != null) {
      out.addStatementComment(getCommentText(comment));
      comment = getPrevSiblingOfType(comment, PsiComment.class, PsiStatement.class, PsiKeyword.class);
    }
  }

  private static String getCommentText(PsiComment comment) {
    final PsiElement sibling = comment.getPrevSibling();
    if (sibling instanceof PsiWhiteSpace) {
      final String whiteSpaceText = sibling.getText();
      return whiteSpaceText.startsWith("\n") ? whiteSpaceText.substring(1) + comment.getText() : comment.getText();
    }
    else {
      return comment.getText();
    }
  }

  private static void extractCaseExpressions(PsiExpression expression, PsiExpression switchExpression, IfStatementBranch branch) {
    if (expression instanceof PsiMethodCallExpression methodCallExpression) {
      if (SwitchUtils.STRING_IS_EMPTY.test(methodCallExpression)) {
        final PsiElementFactory factory = PsiElementFactory.getInstance(methodCallExpression.getProject());
        final PsiExpression caseWithEmptyText = factory.createExpressionFromText("\"\"", switchExpression.getContext());
        branch.addCaseExpression(caseWithEmptyText);
        return;
      }
      final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      final PsiExpression argument = arguments[0];
      final PsiExpression secondArgument = arguments.length > 1 ? arguments[1] : null;
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
      if (EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(switchExpression, argument)) {
        branch.addCaseExpression(secondArgument == null ? qualifierExpression : secondArgument);
      }
      else {
        branch.addCaseExpression(argument);
      }
    }
    else if (expression instanceof PsiInstanceOfExpression) {
      branch.addCaseExpression(expression);
    }
    else if (expression instanceof PsiPolyadicExpression polyadicExpression) {
      final PsiExpression[] operands = polyadicExpression.getOperands();
      final IElementType tokenType = polyadicExpression.getOperationTokenType();
      if (JavaTokenType.OROR.equals(tokenType)) {
        for (PsiExpression operand : operands) {
          extractCaseExpressions(operand, switchExpression, branch);
        }
      } else if (JavaTokenType.ANDAND.equals(tokenType)) {
        branch.addCaseExpression(polyadicExpression);
      }
      else if (operands.length == 2) {
        final PsiExpression lhs = operands[0];
        final PsiExpression rhs = operands[1];
        if (polyadicExpression.getOperationTokenType() != JavaTokenType.EQEQ) {
          branch.addCaseExpression(polyadicExpression);
        }
        else if (EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(switchExpression, rhs)) {
          branch.addCaseExpression(lhs);
        }
        else {
          branch.addCaseExpression(rhs);
        }
      }
    }
    else if (expression instanceof PsiParenthesizedExpression parenthesizedExpression) {
      final PsiExpression contents = parenthesizedExpression.getExpression();
      extractCaseExpressions(contents, switchExpression, branch);
    }
  }

  private static void dumpBranch(IfStatementBranch branch, boolean castToInt, boolean wrap, boolean renameBreaks, String breakLabelName,
                                 @NotNull PsiExpression switchExpression, @NonNls StringBuilder switchStatementText) {
    dumpComments(branch.getComments(), switchStatementText);
    for (PsiExpression caseExpression : branch.getCaseExpressions()) {
      if (caseExpression instanceof PsiLiteralExpression literalExpression && PsiTypes.nullType().equals(literalExpression.getType())) {
        switchStatementText.append("case null:");
      }
      else {
        switchStatementText.append("case ").append(getCaseLabelText(caseExpression, switchExpression, castToInt)).append(": ");
      }
    }
    if (branch.isElse()) {
      switchStatementText.append("default: ");
    }
    dumpComments(branch.getStatementComments(), switchStatementText);
    dumpBody(branch.getStatement(), wrap, renameBreaks, breakLabelName, switchStatementText);
  }

  private static @NonNls String getCaseLabelText(PsiExpression expression,
                                                 @NotNull PsiExpression switchExpression,
                                                 boolean castToInt) {
    if (expression instanceof PsiReferenceExpression referenceExpression) {
      final PsiElement target = referenceExpression.resolve();
      if (target instanceof PsiEnumConstant enumConstant) {
        PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(switchExpression.getType());
        Set<PsiEnumConstant> constants = psiClass != null && psiClass.isEnum() ?
                                         StreamEx.of(psiClass.getFields()).select(PsiEnumConstant.class).toSet() : Set.of();
        if (constants.contains(enumConstant)) {
          return enumConstant.getName();
        }
        else {
          PsiClass containingClass = enumConstant.getContainingClass();
          return containingClass != null ? containingClass.getQualifiedName() + "." + enumConstant.getName() : enumConstant.getName();
        }
      }
    }
    final String patternCaseText = SwitchUtils.createPatternCaseText(expression);
    if (patternCaseText != null) return patternCaseText;
    if (castToInt) {
      final PsiType type = expression.getType();
      if (!PsiTypes.intType().equals(type)) {
        /*
        because
        Integer a = 1;
        switch (a) {
            case (byte)7:
        }
        does not compile with javac (but does with Eclipse)
        */
        return "(int)" + expression.getText();
      }
    }
    return expression.getText();
  }

  private static void dumpComments(List<String> comments, StringBuilder switchStatementText) {
    if (comments.isEmpty()) {
      return;
    }
    switchStatementText.append('\n');
    for (String comment : comments) {
      switchStatementText.append(comment).append('\n');
    }
  }

  private static void dumpBody(PsiStatement bodyStatement, boolean wrap, boolean renameBreaks, String breakLabelName,
                               @NonNls StringBuilder switchStatementText) {
    if (wrap) {
      switchStatementText.append('{');
    }
    if (bodyStatement instanceof PsiBlockStatement) {
      final PsiCodeBlock codeBlock = ((PsiBlockStatement)bodyStatement).getCodeBlock();
      final PsiElement[] children = codeBlock.getChildren();
      //skip the first and last members, to unwrap the block
      for (int i = 1; i < children.length - 1; i++) {
        final PsiElement child = children[i];
        appendElement(child, renameBreaks, breakLabelName, switchStatementText);
      }
    }
    else if (bodyStatement != null) {
      appendElement(bodyStatement, renameBreaks, breakLabelName, switchStatementText);
    }
    if (ControlFlowUtils.statementMayCompleteNormally(bodyStatement)) {
      switchStatementText.append("break;");
    }
    if (wrap) {
      switchStatementText.append('}');
    }
  }

  private static void appendElement(PsiElement element, boolean renameBreakElements, String breakLabelString, @NonNls StringBuilder switchStatementText) {
    final String text = element.getText();
    if (!renameBreakElements) {
      switchStatementText.append(text);
    }
    else if (element instanceof PsiBreakStatement) {
      final PsiIdentifier labelIdentifier = ((PsiBreakStatement)element).getLabelIdentifier();
      if (labelIdentifier == null) {
        PsiElement child = element.getFirstChild();
        switchStatementText.append(child.getText()).append(" ").append(breakLabelString);
        child = child.getNextSibling();
        while (child != null) {
          switchStatementText.append(child.getText());
          child = child.getNextSibling();
        }
        return;
      }
      else {
        switchStatementText.append(text);
      }
    }
    else if (element instanceof PsiBlockStatement || element instanceof PsiCodeBlock || element instanceof PsiIfStatement) {
      final PsiElement[] children = element.getChildren();
      for (final PsiElement child : children) {
        appendElement(child, true, breakLabelString, switchStatementText);
      }
    }
    else {
      switchStatementText.append(text);
    }
    final PsiElement lastChild = element.getLastChild();
    if (lastChild instanceof PsiComment && ((PsiComment)lastChild).getTokenType() == JavaTokenType.END_OF_LINE_COMMENT) {
      switchStatementText.append('\n');
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new IfCanBeSwitchVisitor();
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    defaultWriteSettings(node, "onlySuggestNullSafe");
    writeBooleanOption(node, "onlySuggestNullSafe", true);
  }

  private class IfCanBeSwitchVisitor extends BaseInspectionVisitor {

    @Override
    public void visitIfStatement(@NotNull PsiIfStatement statement) {
      super.visitIfStatement(statement);
      final PsiElement parent = statement.getParent();
      if (parent instanceof PsiIfStatement) {
        return;
      }
      final PsiExpression condition = statement.getCondition();
      final LanguageLevel languageLevel = PsiUtil.getLanguageLevel(statement);
      final PsiExpression switchExpression = SwitchUtils.getSwitchSelectorExpression(condition);
      if (switchExpression == null) {
        return;
      }
      boolean isPatternMatch = SwitchUtils.canBePatternSwitchCase(condition, switchExpression);
      int branchCount = 0;
      final Set<Object> switchCaseValues = new HashSet<>();
      PsiIfStatement branch = statement;
      int additionalIfStatementCount = 0;

      //otherwise, there may be problems in batch modes
      PsiElement previousElement = PsiTreeUtil.skipWhitespacesAndCommentsBackward(branch);
      if (previousElement instanceof PsiIfStatement previousPsiIfStatement &&
          SwitchUtils.canBeSwitchCase(previousPsiIfStatement.getCondition(), switchExpression, languageLevel, switchCaseValues, isPatternMatch)) {
        return;
      }

      boolean currentIsNextIf = false;
      while (true) {
        branchCount++;
        if (!currentIsNextIf && !SwitchUtils.canBeSwitchCase(branch.getCondition(), switchExpression, languageLevel, switchCaseValues, isPatternMatch)) {
          return;
        }
        currentIsNextIf = false;
        final PsiStatement elseBranch = branch.getElseBranch();
        if (!(elseBranch instanceof PsiIfStatement)) {
          if (elseBranch == null) {
            PsiIfStatement upperIf = branch;
            while (upperIf.getParent() instanceof PsiIfStatement parentIfStatement &&
                   parentIfStatement.getElseBranch() == upperIf) {
              upperIf = parentIfStatement;
            }
            PsiElement nextElement = PsiTreeUtil.skipWhitespacesAndCommentsForward(upperIf);
            if (nextElement instanceof PsiIfStatement nextIfStatement &&
                !ControlFlowUtils.statementMayCompleteNormally(branch.getThenBranch())) {
              if (SwitchUtils.canBeSwitchCase(nextIfStatement.getCondition(), switchExpression, languageLevel, switchCaseValues, isPatternMatch)) {
                branch = nextIfStatement;
                currentIsNextIf = true;
                additionalIfStatementCount++;
                continue;
              }
            }
          }
          break;
        }
        branch = (PsiIfStatement)elseBranch;
      }

      final ProblemHighlightType highlightType;
      if (shouldHighlight(statement, switchExpression, switchCaseValues) && branchCount >= minimumBranches) {
        highlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
      }
      else {
        if (!isOnTheFly()) return;
        highlightType = ProblemHighlightType.INFORMATION;
      }
      registerError(statement.getFirstChild(), highlightType, additionalIfStatementCount);
    }

    private boolean shouldHighlight(@NotNull PsiIfStatement ifStatement,
                                    @NotNull PsiExpression switchExpression,
                                    @NotNull Set<Object> switchCaseValues) {
      final PsiType type = switchExpression.getType();
      if (!suggestIntSwitches) {
        if (type instanceof PsiClassType) {
          if (type.equalsToText(CommonClassNames.JAVA_LANG_INTEGER) ||
              type.equalsToText(CommonClassNames.JAVA_LANG_SHORT) ||
              type.equalsToText(CommonClassNames.JAVA_LANG_BYTE) ||
              type.equalsToText(CommonClassNames.JAVA_LANG_CHARACTER)) {
            return false;
          }
        }
        else if (PsiTypes.intType().equals(type) || PsiTypes.shortType().equals(type) || PsiTypes.byteType().equals(type) || PsiTypes.charType()
          .equals(type)) {
          return false;
        }
      }
      if (type instanceof PsiClassType) {
        if (!suggestEnumSwitches && TypeConversionUtil.isEnumType(type)) {
          return false;
        }
        Nullability nullability = getNullability(switchExpression);
        if (PsiUtil.isAvailable(JavaFeature.PATTERNS_IN_SWITCH, switchExpression) &&
            !ClassUtils.isPrimitive(switchExpression.getType())) {
          if (hasDefaultElse(ifStatement) || findNullCheckedOperand(ifStatement) != null) {
            nullability = Nullability.NOT_NULL;
          }
        }
        if (nullability == Nullability.NULLABLE) {
          return false;
        }
        if (onlySuggestNullSafe && nullability != Nullability.NOT_NULL) {
          return false;
        }
      }
      int countUnconditionalPatterns = SwitchUtils.countUnconditionalPatterns(switchExpression, switchCaseValues);
      if (hasDefaultElse(ifStatement)) {
        countUnconditionalPatterns++;
      }
      if (countUnconditionalPatterns > 1) return false;
      return !SideEffectChecker.mayHaveSideEffects(switchExpression);
    }
  }

  private static boolean hasUnconditionalPatternCheck(PsiIfStatement ifStatement, PsiExpression switchExpression){
    final PsiType type = switchExpression.getType();
    if (type == null) return false;

    PsiIfStatement currentIfInChain = ifStatement;
    while (currentIfInChain != null) {
      final PsiExpression condition = PsiUtil.skipParenthesizedExprDown(currentIfInChain.getCondition());
      if (condition instanceof PsiPolyadicExpression polyadicExpression) {
        if (JavaTokenType.OROR.equals(polyadicExpression.getOperationTokenType())) {
          if (ContainerUtil.exists(polyadicExpression.getOperands(), (operand) -> hasUnconditionalPatternCheck(type, operand))) {
            return true;
          }
        }
      }
      if (hasUnconditionalPatternCheck(type, condition)) {
        return true;
      }
      final PsiStatement elseBranch = currentIfInChain.getElseBranch();
      if (elseBranch instanceof PsiIfStatement) {
        currentIfInChain = (PsiIfStatement)elseBranch;
      } else {
        currentIfInChain = null;
      }
    }

    return false;
  }

  private static boolean hasUnconditionalPatternCheck(PsiType type, PsiExpression check) {
    final PsiCaseLabelElement pattern = SwitchUtils.createPatternFromExpression(check);
    if (pattern == null) return false;
    return JavaPsiPatternUtil.isUnconditionalForType(pattern, type);
  }

  private static Nullability getNullability(PsiExpression expression) {
    // expression.equals("string") -> expression == NOT_NULL
    if (ExpressionUtils.getCallForQualifier(expression) != null) {
      return Nullability.NOT_NULL;
    }
    // inferred nullability
    Nullability normal = NullabilityUtil.getExpressionNullability(expression, false);
    Nullability dataflow = NullabilityUtil.getExpressionNullability(expression, true);
    if (normal == Nullability.NOT_NULL || dataflow == Nullability.NOT_NULL) {
      return Nullability.NOT_NULL;
    }
    if (normal == Nullability.NULLABLE || dataflow == Nullability.NULLABLE) {
      return Nullability.NULLABLE;
    }
    return Nullability.UNKNOWN;
  }

  private static PsiExpression findNullCheckedOperand(PsiIfStatement ifStatement) {
    final PsiExpression condition = PsiUtil.skipParenthesizedExprDown(ifStatement.getCondition());
    if (condition instanceof PsiPolyadicExpression polyadicExpression) {
      if (JavaTokenType.OROR.equals(polyadicExpression.getOperationTokenType())) {
        for (PsiExpression operand : polyadicExpression.getOperands()) {
          final PsiExpression nullCheckedExpression = SwitchUtils.findNullCheckedOperand(PsiUtil.skipParenthesizedExprDown(operand));
          if (nullCheckedExpression != null) return nullCheckedExpression;
        }
      }
    }
    final PsiExpression nullCheckedOperand = SwitchUtils.findNullCheckedOperand(condition);
    if (nullCheckedOperand != null) return nullCheckedOperand;
    final PsiStatement elseBranch = ifStatement.getElseBranch();
    if (elseBranch instanceof PsiIfStatement) {
      return findNullCheckedOperand((PsiIfStatement)elseBranch);
    } else {
      return null;
    }
  }

  private static boolean hasDefaultElse(PsiIfStatement ifStatement) {
    final PsiStatement elseBranch = ifStatement.getElseBranch();
    if (elseBranch == null) return false;
    if (elseBranch instanceof PsiIfStatement) {
      return hasDefaultElse((PsiIfStatement)elseBranch);
    } else {
      return true;
    }
  }
}
