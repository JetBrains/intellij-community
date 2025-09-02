// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actions.EnterAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.util.RefactoringChangeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.VariableNameGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public final class CreateLocalVarFromInstanceofAction extends BaseIntentionAction {
  private static final Logger LOG = Logger.getInstance(CreateLocalVarFromInstanceofAction.class);

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    PsiInstanceOfExpression instanceOfExpression = getInstanceOfExpressionAtCaret(psiFile, editor.getCaretModel().getOffset());
    if (instanceOfExpression == null) return false;
    PsiTypeElement checkType = instanceOfExpression.getCheckType();
    if (checkType == null) return false;
    if (instanceOfExpression.getPattern() != null) return false;
    PsiExpression operand = instanceOfExpression.getOperand();
    PsiType operandType = operand.getType();
    if (TypeConversionUtil.isPrimitiveAndNotNull(operandType)) return false;
    PsiType type = checkType.getType();
    String castTo = type.getPresentableText();
    setText(QuickFixBundle.message("create.local.from.instanceof.usage.text", castTo, operand.getText()));

    PsiStatement statement = PsiTreeUtil.getParentOfType(instanceOfExpression, PsiStatement.class);
    boolean insideIf = statement instanceof PsiIfStatement
                       && PsiTreeUtil.isAncestor(((PsiIfStatement)statement).getCondition(), instanceOfExpression, false);
    boolean insideWhile = statement instanceof PsiWhileStatement
                          && PsiTreeUtil.isAncestor(((PsiWhileStatement)statement).getCondition(), instanceOfExpression, false);
    return (insideIf || insideWhile) && !isAlreadyCastedTo(type, instanceOfExpression, statement);
  }

  static boolean isAlreadyCastedTo(final PsiType type, final PsiInstanceOfExpression instanceOfExpression, final PsiStatement statement) {
    boolean negated = isNegated(instanceOfExpression);
    PsiElement anchor = null;
    if (negated) {
      PsiElement parent = statement.getParent();
      if (parent instanceof PsiCodeBlock) {
        PsiStatement[] statements = ((PsiCodeBlock)parent).getStatements();
        int i = ArrayUtil.find(statements, statement);
        anchor = i != -1 && i < statements.length - 1 ? statements[i+1] : null;
      }
    }
    else {
      anchor = statement instanceof PsiIfStatement ? ((PsiIfStatement)statement).getThenBranch() : ((PsiWhileStatement)statement).getBody();
    }
    if (anchor instanceof PsiBlockStatement) {
      anchor = ((PsiBlockStatement)anchor).getCodeBlock();
    }
    if (anchor instanceof PsiCodeBlock) {
      PsiStatement[] statements = ((PsiCodeBlock)anchor).getStatements();
      if (statements.length == 0) return false;
      anchor = statements[0];
    }
    if (anchor instanceof PsiDeclarationStatement) {
      PsiElement[] declaredElements = ((PsiDeclarationStatement)anchor).getDeclaredElements();
      for (PsiElement element : declaredElements) {
        if (!(element instanceof PsiLocalVariable)) continue;
        PsiExpression initializer = ((PsiLocalVariable)element).getInitializer();
        if (!(initializer instanceof PsiTypeCastExpression typeCastExpression)) continue;

        final PsiExpression operand = typeCastExpression.getOperand();
        if (operand != null &&
            !EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(operand, instanceOfExpression.getOperand())) continue;
        PsiTypeElement castTypeElement = typeCastExpression.getCastType();
        if (castTypeElement == null) continue;
        PsiType castType = castTypeElement.getType();
        if (castType.equals(type)) return true;
      }
    }
    return false;
  }

  static @Nullable PsiInstanceOfExpression getInstanceOfExpressionAtCaret(PsiFile psiFile, int caretOffset) {
    PsiElement element = psiFile.findElementAt(caretOffset);
    if (element == null) return null;
    PsiInstanceOfExpression expression = PsiTreeUtil.getParentOfType(element, PsiInstanceOfExpression.class);
    if (expression != null) {
      return expression;
    }
    if (caretOffset > 0) {
      PsiInstanceOfExpression prev = PsiTreeUtil.getParentOfType(psiFile.findElementAt(caretOffset - 1), PsiInstanceOfExpression.class);
      if (prev != null) {
        return prev;
      }
    }
    PsiStatement statement = PsiTreeUtil.getParentOfType(element, PsiIfStatement.class, PsiWhileStatement.class);
    if (statement instanceof PsiIfStatement) {
      final PsiExpression condition = ((PsiIfStatement)statement).getCondition();
      return retrieveInstanceOfFromCondition(element, statement, condition);
    }
    else if (statement instanceof PsiWhileStatement) {
      final PsiExpression condition = ((PsiWhileStatement)statement).getCondition();
      return retrieveInstanceOfFromCondition(element, statement, condition);
    }
    return null;
  }

  private static @Nullable PsiInstanceOfExpression retrieveInstanceOfFromCondition(PsiElement element,
                                                                                   PsiStatement statement,
                                                                                   PsiExpression condition) {
    if (condition instanceof PsiInstanceOfExpression instanceOf) {
      if (insideEmptyBlockOrRef(statement, element, instanceOf)) {
        return instanceOf;
      }
    } else if (condition instanceof PsiPolyadicExpression polyadic) {
      final PsiExpression[] operands = polyadic.getOperands();
      if (polyadic.getOperationTokenType() == JavaTokenType.ANDAND) {
        PsiInstanceOfExpression expr = null;
        for (PsiExpression operand : operands) {
          if (operand instanceof PsiInstanceOfExpression instanceOf) {
            if (expr != null) {
              expr = null;
              break;
            }
            expr = instanceOf;
          }
        }
        if (expr != null && insideEmptyBlockOrRef(statement, element, expr)) {
          return expr;
        }
      }
    }
    return null;
  }

  private static boolean insideEmptyBlockOrRef(PsiStatement stmt, PsiElement elementAtCaret, PsiInstanceOfExpression instanceOfExpression) {
    PsiBlockStatement block = PsiTreeUtil.getParentOfType(elementAtCaret, PsiBlockStatement.class);
    if (block != null && block.getParent() == stmt) {
      final PsiStatement[] statements = block.getCodeBlock().getStatements();
      if (statements.length == 0) {
        return true;
      }
      if (statements.length == 1 && statements[0] instanceof PsiExpressionStatement) {
        return replaceReference(instanceOfExpression, (PsiExpressionStatement)statements[0]);
      }
    }
    return false;
  }

  private static boolean replaceReference(PsiInstanceOfExpression instanceOfExpression, PsiExpressionStatement statement) {
    if (isNegated(instanceOfExpression)) return false;
    final PsiExpression expression = statement.getExpression();
    final PsiExpression operand = instanceOfExpression.getOperand();
    if (operand instanceof PsiReferenceExpression && expression instanceof PsiReferenceExpression &&
        ((PsiReferenceExpression)operand).resolve() == ((PsiReferenceExpression)expression).resolve()){
      return true;
    }
    return false;
  }

  @Override
  public void invoke(final @NotNull Project project, final Editor editor, final PsiFile psiFile) {
    PsiInstanceOfExpression instanceOfExpression = getInstanceOfExpressionAtCaret(psiFile, editor.getCaretModel().getOffset());
    assert instanceOfExpression.getContainingFile() == psiFile : instanceOfExpression.getContainingFile() + "; file=" + psiFile;
    try {
      final PsiStatement statementInside = isNegated(instanceOfExpression) ? null : getExpressionStatementInside(editor, instanceOfExpression.getOperand());
      PsiDeclarationStatement decl = createLocalVariableDeclaration(instanceOfExpression, statementInside);
      if (decl == null) return;
      decl = (PsiDeclarationStatement)CodeStyleManager.getInstance(project).reformat(decl);
      decl = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(decl);
      if (decl == null) return;

      PsiLocalVariable localVariable = (PsiLocalVariable)decl.getDeclaredElements()[0];
      PsiExpression initializer = Objects.requireNonNull(localVariable.getInitializer());
      List<String> names = new VariableNameGenerator(initializer, VariableKind.LOCAL_VARIABLE).byExpression(initializer)
        .byType(localVariable.getType()).generateAll(true);
      PsiIdentifier identifier = Objects.requireNonNull(localVariable.getNameIdentifier());

      TemplateBuilderImpl builder = new TemplateBuilderImpl(localVariable);
      builder.setEndVariableAfter(localVariable.getNameIdentifier());

      Template template = generateTemplate(project, names);
      Editor newEditor = CodeInsightUtil.positionCursor(project, psiFile, identifier);
      if (newEditor == null) return;
      TextRange range = localVariable.getNameIdentifier().getTextRange();
      newEditor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());

      CreateFromUsageBaseFix.startTemplate(newEditor, template, project, new TemplateEditingAdapter() {

        @Override
        public void beforeTemplateFinished(@NotNull TemplateState state, Template template) {
          final TextResult value = state.getVariableValue("");
          assert value != null;

          final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(project).getResolveHelper();
          final PsiVariable target = resolveHelper.resolveAccessibleReferencedVariable(value.getText(), instanceOfExpression);
          if (target instanceof PsiField field) {
            final CaretModel caretModel = editor.getCaretModel();
            final PsiElement elementAt = psiFile.findElementAt(caretModel.getOffset());
            final PsiDeclarationStatement declarationStatement = PsiTreeUtil.getParentOfType(elementAt, PsiDeclarationStatement.class);
            if (declarationStatement != null) {
              final PsiLocalVariable variable = (PsiLocalVariable)declarationStatement.getDeclaredElements()[0];
              final PsiExpression initializer = variable.getInitializer();
              assert initializer != null;
              ApplicationManager.getApplication().runWriteAction(() -> {
                initializer.accept(new JavaRecursiveElementVisitor() {
                  @Override
                  public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
                    final PsiExpression qualifierExpression = expression.getQualifierExpression();
                    if (qualifierExpression != null) {
                      qualifierExpression.accept(this);
                    }
                    else if (expression.resolve() == variable) {
                      RefactoringChangeUtil.qualifyReference(expression, field, field.hasModifierProperty(PsiModifier.STATIC)
                                                                                ? field.getContainingClass()
                                                                                : null);
                    }
                  }
                });
              });
              PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.getDocument());
            }
          }
        }

        @Override
        public void templateFinished(@NotNull Template template, boolean brokenOff) {
          Runnable action = () -> {
            PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());

            CaretModel caretModel = editor.getCaretModel();
            PsiElement elementAt = psiFile.findElementAt(caretModel.getOffset());
            PsiDeclarationStatement declarationStatement = PsiTreeUtil.getParentOfType(elementAt, PsiDeclarationStatement.class);
            if (declarationStatement != null) {
              caretModel.moveToOffset(declarationStatement.getTextRange().getEndOffset());
            }
            new EnterAction().getHandler().execute(editor, null, null);
          };
          if (psiFile.isPhysical()) {
            ApplicationManager.getApplication().runWriteAction(action);
          } else {
            action.run();
          }
        }
      });
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private static @Nullable PsiStatement getExpressionStatementInside(Editor editor, @NotNull PsiExpression operand) {
    return getExpressionStatementInside(editor.getCaretModel().getOffset(), operand);
  }

  static @Nullable PsiStatement getExpressionStatementInside(int offset, @NotNull PsiExpression operand) {
    PsiElement elementAt = operand.getContainingFile().findElementAt(offset);

    PsiBlockStatement blockStatement = PsiTreeUtil.getParentOfType(elementAt, PsiBlockStatement.class);
    if (blockStatement == null) {
      final PsiIfStatement ifStm = PsiTreeUtil.getParentOfType(elementAt, PsiIfStatement.class);
      if (ifStm != null) {
        final PsiStatement thenBranch = ifStm.getThenBranch();
        if (thenBranch instanceof PsiBlockStatement) {
          blockStatement = (PsiBlockStatement)thenBranch;
        }
      } else {
        final PsiWhileStatement whileStatement = PsiTreeUtil.getParentOfType(elementAt, PsiWhileStatement.class);
        if (whileStatement != null) {
          final PsiStatement body = whileStatement.getBody();
          if (body instanceof PsiBlockStatement) {
            blockStatement = (PsiBlockStatement)body;
          }
        }
      }
    }

    if (blockStatement != null) {
      final PsiStatement[] statements = blockStatement.getCodeBlock().getStatements();
      if (statements.length == 1 &&
          statements[0] instanceof PsiExpressionStatement &&
          PsiEquivalenceUtil.areElementsEquivalent(((PsiExpressionStatement)statements[0]).getExpression(), operand)) {
        return statements[0];
      }
    }
    return null;
  }

  private static @Nullable PsiDeclarationStatement createLocalVariableDeclaration(final PsiInstanceOfExpression instanceOfExpression,
                                                                                  final PsiStatement statementInside) throws IncorrectOperationException {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(instanceOfExpression.getProject());
    PsiTypeCastExpression cast = (PsiTypeCastExpression)factory.createExpressionFromText("(a)b", instanceOfExpression);
    PsiType castType = instanceOfExpression.getCheckType().getType();
    Objects.requireNonNull(cast.getCastType()).replace(factory.createTypeElement(castType));
    Objects.requireNonNull(cast.getOperand()).replace(instanceOfExpression.getOperand());
    PsiDeclarationStatement decl = factory.createVariableDeclarationStatement("xxx", castType, cast);
    final Boolean createFinals = JavaRefactoringSettings.getInstance().INTRODUCE_LOCAL_CREATE_FINALS;
    if (createFinals != null) {
      final PsiElement[] declaredElements = decl.getDeclaredElements();
      LOG.assertTrue(declaredElements.length == 1);
      LOG.assertTrue(declaredElements[0] instanceof PsiLocalVariable);
      final PsiModifierList modifierList = ((PsiLocalVariable)declaredElements[0]).getModifierList();
      LOG.assertTrue(modifierList != null);
      modifierList.setModifierProperty(PsiModifier.FINAL, createFinals.booleanValue());
    }
    if (statementInside != null) {
      return (PsiDeclarationStatement)statementInside.replace(decl);
    } else {
      return  (PsiDeclarationStatement)insertAtAnchor(instanceOfExpression, decl);
    }
  }

  static @Nullable PsiElement insertAtAnchor(final PsiInstanceOfExpression instanceOfExpression, PsiElement toInsert) throws IncorrectOperationException {
    boolean negated = isNegated(instanceOfExpression);
    PsiStatement statement = PsiTreeUtil.getParentOfType(instanceOfExpression, PsiStatement.class);
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(toInsert.getProject());
    PsiElement anchorAfter = null;
    PsiBlockStatement emptyBlockStatement = (PsiBlockStatement)factory.createStatementFromText("{}", instanceOfExpression);
    if (statement instanceof PsiIfStatement ifStatement) {
      if (negated) {
        PsiStatement elseBranch = ifStatement.getElseBranch();
        if (elseBranch == null) {
          anchorAfter = ifStatement;
        }
        else if (!(elseBranch instanceof PsiBlockStatement)) {
          emptyBlockStatement.getCodeBlock().add(elseBranch);
          PsiBlockStatement newBranch = (PsiBlockStatement)elseBranch.replace(emptyBlockStatement);
          reformatNewCodeBlockBraces(ifStatement.getElseElement(), newBranch);
          anchorAfter = newBranch.getCodeBlock().getLBrace();
        }
        else {
          anchorAfter = ((PsiBlockStatement)elseBranch).getCodeBlock().getLBrace();
        }
      }
      else {
        PsiStatement thenBranch = ifStatement.getThenBranch();
        if (thenBranch == null) {
          ifStatement.setThenBranch(emptyBlockStatement);
          PsiBlockStatement then = (PsiBlockStatement)ifStatement.getThenBranch();
          reformatNewCodeBlockBraces(ifStatement.getCondition(), then);
          anchorAfter = then.getCodeBlock().getLBrace();
        }
        else if (!(thenBranch instanceof PsiBlockStatement)) {
          emptyBlockStatement.getCodeBlock().add(thenBranch);
          PsiBlockStatement newBranch = (PsiBlockStatement)thenBranch.replace(emptyBlockStatement);
          reformatNewCodeBlockBraces(ifStatement.getCondition(), newBranch);
          anchorAfter = newBranch.getCodeBlock().getLBrace();
        }
        else {
          anchorAfter = ((PsiBlockStatement)thenBranch).getCodeBlock().getLBrace();
        }
      }
    }
    if (statement instanceof PsiWhileStatement whileStatement) {
      LOG.assertTrue(whileStatement.getLParenth() != null);
      LOG.assertTrue(whileStatement.getCondition() != null);
      if (whileStatement.getRParenth() == null) {
        PsiWhileStatement statementPattern = (PsiWhileStatement)factory.createStatementFromText("while (){}", instanceOfExpression);
        whileStatement.addAfter(statementPattern.getRParenth(), whileStatement.getCondition());
      }
      if (negated) {
        anchorAfter = whileStatement;
      }
      else {
        PsiStatement body = whileStatement.getBody();
        if (body == null) {
          whileStatement.add(emptyBlockStatement);
        }
        else if (!(body instanceof PsiBlockStatement)) {
          emptyBlockStatement.getCodeBlock().add(body);
          whileStatement.getBody().replace(emptyBlockStatement);
        }
        anchorAfter = ((PsiBlockStatement)whileStatement.getBody()).getCodeBlock().getLBrace();
      }
    }

    if (anchorAfter == null) {
      return null;
    }
    PsiElement nextSibling = anchorAfter.getNextSibling();
    while (nextSibling != null) {
      if (nextSibling instanceof PsiWhiteSpace) {
        final String text = nextSibling.getText();
        if (StringUtil.countNewLines(text) > 1) {
          final PsiElement newWhitespace = PsiParserFacade.getInstance(nextSibling.getProject())
            .createWhiteSpaceFromText(text.substring(0, text.lastIndexOf('\n')));
          nextSibling.replace(newWhitespace);
          break;
        }
        nextSibling = nextSibling.getNextSibling();
        continue;
      }
      else if (!isValidDeclarationStatement(nextSibling) && !(nextSibling instanceof PsiComment)) {
        break;
      }
      anchorAfter = nextSibling;
      nextSibling = anchorAfter.getNextSibling();
    }
    return anchorAfter.getParent().addAfter(toInsert, anchorAfter);
  }

  private static boolean isValidDeclarationStatement(PsiElement nextSibling) {
    if (!(nextSibling instanceof PsiDeclarationStatement declarationStatement)) {
      return false;
    }
    final PsiElement[] elements = declarationStatement.getDeclaredElements();
    if (elements.length == 0) {
      return false;
    }
    final PsiElement lastElement = elements[elements.length - 1];
    return !(lastElement instanceof PsiClass) && PsiUtil.isJavaToken(lastElement.getLastChild(), JavaTokenType.SEMICOLON);
  }

  private static void reformatNewCodeBlockBraces(final PsiElement start, final PsiBlockStatement end)
    throws IncorrectOperationException {
    CodeStyleManager.getInstance(end.getProject()).reformatRange(end.getContainingFile(),
                                                                 start.getTextRange().getEndOffset(),
                                                                 end.getTextRange().getStartOffset());
  }

  static boolean isNegated(final PsiInstanceOfExpression instanceOfExpression) {
    PsiElement element = instanceOfExpression.getParent();
    while (element instanceof PsiParenthesizedExpression) {
      element = element.getParent();
    }
    return element instanceof PsiPrefixExpression && ((PsiPrefixExpression)element).getOperationTokenType() == JavaTokenType.EXCL;
  }

  private static Template generateTemplate(Project project, List<String> names) {
    final TemplateManager templateManager = TemplateManager.getInstance(project);
    final Template template = templateManager.createTemplate("", "");
    template.setToReformat(true);

    final Result result = new TextResult(names.get(0));

    Expression expr = new ConstantNode(result).withLookupStrings(
      names.size() > 1 ? ArrayUtil.toStringArray(names) : ArrayUtilRt.EMPTY_STRING_ARRAY);
    template.addVariable("", expr, expr, true);

    return template;
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("create.local.from.instanceof.usage.family");
  }
}
