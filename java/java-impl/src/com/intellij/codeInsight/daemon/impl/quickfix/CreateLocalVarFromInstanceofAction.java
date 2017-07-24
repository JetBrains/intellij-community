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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.template.*;
import com.intellij.ide.DataManager;
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
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author cdr
 */
public class CreateLocalVarFromInstanceofAction extends BaseIntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.CreateLocalVarFromInstanceofAction");

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    PsiInstanceOfExpression instanceOfExpression = getInstanceOfExpressionAtCaret(editor, file);
    if (instanceOfExpression == null) return false;
    PsiTypeElement checkType = instanceOfExpression.getCheckType();
    if (checkType == null) return false;
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

  private static boolean isAlreadyCastedTo(final PsiType type, final PsiInstanceOfExpression instanceOfExpression, final PsiStatement statement) {
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
        if (!(initializer instanceof PsiTypeCastExpression)) continue;

        final PsiTypeCastExpression typeCastExpression = (PsiTypeCastExpression)initializer;
        final PsiExpression operand = typeCastExpression.getOperand();
        if (operand != null && !PsiEquivalenceUtil.areElementsEquivalent(operand, instanceOfExpression.getOperand())) continue;
        PsiTypeElement castTypeElement = typeCastExpression.getCastType();
        if (castTypeElement == null) continue;
        PsiType castType = castTypeElement.getType();
        if (castType.equals(type)) return true;
      }
    }
    return false;
  }

  @Nullable
  static PsiInstanceOfExpression getInstanceOfExpressionAtCaret(Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    if (element == null) return null;
    PsiInstanceOfExpression expression = PsiTreeUtil.getParentOfType(element, PsiInstanceOfExpression.class);
    if (expression != null) {
      return expression;
    }
    PsiStatement statement = PsiTreeUtil.getParentOfType(element, PsiIfStatement.class, PsiWhileStatement.class);
    if (statement instanceof PsiIfStatement) {
      final PsiExpression condition = ((PsiIfStatement)statement).getCondition();
      return retrieveInstanceOfFromCondition(editor, element, statement, condition);
    }
    else if (statement instanceof PsiWhileStatement) {
      final PsiExpression condition = ((PsiWhileStatement)statement).getCondition();
      return retrieveInstanceOfFromCondition(editor, element, statement, condition);
    }
    return null;
  }

  @Nullable
  private static PsiInstanceOfExpression retrieveInstanceOfFromCondition(Editor editor,
                                                                         PsiElement element,
                                                                         PsiStatement statement,
                                                                         PsiExpression condition) {
    if (condition instanceof PsiInstanceOfExpression) {
      if (atSameLine(condition, editor) || insideEmptyBlockOrRef(statement, element, (PsiInstanceOfExpression)condition)) {
        return (PsiInstanceOfExpression)condition;
      }
    } else if (condition instanceof PsiPolyadicExpression) {
      final PsiExpression[] operands = ((PsiPolyadicExpression)condition).getOperands();
      if (((PsiPolyadicExpression)condition).getOperationTokenType() ==  JavaTokenType.ANDAND) {
        PsiInstanceOfExpression expr = null;
        for (PsiExpression operand : operands) {
          if (operand instanceof PsiInstanceOfExpression) {
            if (expr != null) {
              expr = null;
              break;
            }
            expr = (PsiInstanceOfExpression)operand;
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

  private static boolean atSameLine(final PsiExpression condition, final Editor editor) {
    int line = editor.getCaretModel().getLogicalPosition().line;
    return editor.getDocument().getLineNumber(condition.getTextOffset()) == line;
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) {
    PsiInstanceOfExpression instanceOfExpression = getInstanceOfExpressionAtCaret(editor, file);
    assert instanceOfExpression.getContainingFile() == file : instanceOfExpression.getContainingFile() + "; file="+file;
    try {
      final PsiStatement statementInside = isNegated(instanceOfExpression) ? null : getExpressionStatementInside(file, editor, instanceOfExpression.getOperand());
      PsiDeclarationStatement decl = createLocalVariableDeclaration(instanceOfExpression, statementInside);
      if (decl == null) return;
      decl = (PsiDeclarationStatement)CodeStyleManager.getInstance(project).reformat(decl);
      decl = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(decl);

      PsiLocalVariable localVariable = (PsiLocalVariable)decl.getDeclaredElements()[0];
      TemplateBuilderImpl builder = new TemplateBuilderImpl(localVariable);
      builder.setEndVariableAfter(localVariable.getNameIdentifier());

      Template template = generateTemplate(project, localVariable.getInitializer(), localVariable.getType());

      Editor newEditor = CreateFromUsageBaseFix.positionCursor(project, file, localVariable.getNameIdentifier());
      if (newEditor == null) return;
      TextRange range = localVariable.getNameIdentifier().getTextRange();
      newEditor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());

      CreateFromUsageBaseFix.startTemplate(newEditor, template, project, new TemplateEditingAdapter() {
        @Override
        public void templateFinished(Template template, boolean brokenOff) {
          ApplicationManager.getApplication().runWriteAction(() -> {
            PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());

            CaretModel caretModel = editor.getCaretModel();
            PsiElement elementAt = file.findElementAt(caretModel.getOffset());
            PsiDeclarationStatement declarationStatement = PsiTreeUtil.getParentOfType(elementAt, PsiDeclarationStatement.class);
            if (declarationStatement != null) {
              caretModel.moveToOffset(declarationStatement.getTextRange().getEndOffset());
            }
            new EnterAction().actionPerformed(editor, DataManager.getInstance().getDataContext());
          });
        }
      });
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @Nullable
  protected static PsiStatement getExpressionStatementInside(PsiFile file, Editor editor, @NotNull PsiExpression operand) {
    PsiElement elementAt = file.findElementAt(editor.getCaretModel().getOffset());

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

  @Nullable
  private static PsiDeclarationStatement createLocalVariableDeclaration(final PsiInstanceOfExpression instanceOfExpression,
                                                                        final PsiStatement statementInside) throws IncorrectOperationException {
    PsiElementFactory factory = JavaPsiFacade.getInstance(instanceOfExpression.getProject()).getElementFactory();
    PsiTypeCastExpression cast = (PsiTypeCastExpression)factory.createExpressionFromText("(a)b", instanceOfExpression);
    PsiType castType = instanceOfExpression.getCheckType().getType();
    cast.getCastType().replace(factory.createTypeElement(castType));
    cast.getOperand().replace(instanceOfExpression.getOperand());
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

  @Nullable
  static PsiElement insertAtAnchor(final PsiInstanceOfExpression instanceOfExpression, PsiElement toInsert) throws IncorrectOperationException {
    boolean negated = isNegated(instanceOfExpression);
    PsiStatement statement = PsiTreeUtil.getParentOfType(instanceOfExpression, PsiStatement.class);
    PsiElementFactory factory = JavaPsiFacade.getInstance(toInsert.getProject()).getElementFactory();
    PsiElement anchorAfter = null;
    PsiBlockStatement emptyBlockStatement = (PsiBlockStatement)factory.createStatementFromText("{}", instanceOfExpression);
    if (statement instanceof PsiIfStatement) {
      PsiIfStatement ifStatement = (PsiIfStatement)statement;
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
    if (statement instanceof PsiWhileStatement) {
      PsiWhileStatement whileStatement = (PsiWhileStatement)statement;
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
          final PsiElement newWhitespace = PsiParserFacade.SERVICE.getInstance(nextSibling.getProject())
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
    if (!(nextSibling instanceof PsiDeclarationStatement)) {
      return false;
    }
    final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)nextSibling;
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

  protected static boolean isNegated(final PsiInstanceOfExpression instanceOfExpression) {
    PsiElement element = instanceOfExpression.getParent();
    while (element instanceof PsiParenthesizedExpression) {
      element = element.getParent();
    }
    return element instanceof PsiPrefixExpression && ((PsiPrefixExpression)element).getOperationTokenType() == JavaTokenType.EXCL;
  }

  private static Template generateTemplate(Project project, PsiExpression initializer, PsiType type) {
    final TemplateManager templateManager = TemplateManager.getInstance(project);
    final Template template = templateManager.createTemplate("", "");
    template.setToReformat(true);

    final SuggestedNameInfo suggestedNameInfo = IntroduceVariableBase.getSuggestedName(type, initializer, initializer);

    Set<LookupElement> itemSet = new LinkedHashSet<>();
    for (String name : suggestedNameInfo.names) {
      itemSet.add(LookupElementBuilder.create(name));
    }
    final LookupElement[] lookupItems = itemSet.toArray(new LookupElement[itemSet.size()]);
    final Result result = suggestedNameInfo.names.length == 0 ? null : new TextResult(suggestedNameInfo.names[0]);

    Expression expr = new Expression() {
      @Override
      public LookupElement[] calculateLookupItems(ExpressionContext context) {
        return lookupItems.length > 1 ? lookupItems : null;
      }

      @Override
      public Result calculateResult(ExpressionContext context) {
        return result;
      }

      @Override
      public Result calculateQuickResult(ExpressionContext context) {
        return result;
      }
    };
    template.addVariable("", expr, expr, true);

    return template;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("create.local.from.instanceof.usage.family");
  }
}
