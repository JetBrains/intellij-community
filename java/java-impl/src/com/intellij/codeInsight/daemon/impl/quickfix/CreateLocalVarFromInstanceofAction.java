/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.codeInsight.CodeInsightUtilBase;
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
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author cdr
 */
public class CreateLocalVarFromInstanceofAction extends BaseIntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.CreateLocalVarFromInstanceofAction");

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    PsiInstanceOfExpression instanceOfExpression = getInstanceOfExpressionAtCaret(editor, file);
    if (instanceOfExpression == null) return false;
    PsiTypeElement checkType = instanceOfExpression.getCheckType();
    if (checkType == null) return false;
    PsiType type = checkType.getType();
    String castTo = type.getPresentableText();
    setText(QuickFixBundle.message("create.local.from.instanceof.usage.text", castTo, instanceOfExpression.getOperand().getText()));

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

        PsiTypeElement castTypeElement = ((PsiTypeCastExpression)initializer).getCastType();
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
      PsiExpression condition = ((PsiIfStatement)statement).getCondition();
      if (condition instanceof PsiInstanceOfExpression) {
        if (atSameLine(condition, editor) || insideEmptyBlockOfStatement(statement, element)) {
          return (PsiInstanceOfExpression)condition;
        }
      }
    }
    else if (statement instanceof PsiWhileStatement) {
      PsiExpression condition = ((PsiWhileStatement)statement).getCondition();
      if (condition instanceof PsiInstanceOfExpression) {
        if (atSameLine(condition, editor) || insideEmptyBlockOfStatement(statement, element)) {
          return (PsiInstanceOfExpression)condition;
        }
      }
    }
    return null;
  }

  private static boolean insideEmptyBlockOfStatement(PsiStatement stmt, PsiElement elementAtCaret) {
    PsiBlockStatement block = PsiTreeUtil.getParentOfType(elementAtCaret, PsiBlockStatement.class);
    return block != null && block.getParent() == stmt && block.getCodeBlock().getStatements().length == 0;
  }

  private static boolean atSameLine(final PsiExpression condition, final Editor editor) {
    int line = editor.getCaretModel().getLogicalPosition().line;
    return editor.getDocument().getLineNumber(condition.getTextOffset()) == line;
  }

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) {
    if (!CodeInsightUtilBase.prepareFileForWrite(file)) return;

    PsiInstanceOfExpression instanceOfExpression = getInstanceOfExpressionAtCaret(editor, file);
    assert instanceOfExpression.getContainingFile() == file : instanceOfExpression.getContainingFile() + "; file="+file;
    try {
      final PsiDeclarationStatement decl = createLocalVariableDeclaration(instanceOfExpression);
      if (decl == null) return;

      PsiLocalVariable localVariable = (PsiLocalVariable)decl.getDeclaredElements()[0];
      TemplateBuilderImpl builder = new TemplateBuilderImpl(localVariable);
      builder.setEndVariableAfter(localVariable.getNameIdentifier());

      Template template = generateTemplate(project, localVariable.getInitializer(), localVariable.getType());

      Editor newEditor = CreateFromUsageBaseFix.positionCursor(project, file, localVariable.getNameIdentifier());
      TextRange range = localVariable.getNameIdentifier().getTextRange();
      newEditor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());

      CreateFromUsageBaseFix.startTemplate(newEditor, template, project, new TemplateEditingAdapter() {
        public void templateFinished(Template template, boolean brokenOff) {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());

              CaretModel caretModel = editor.getCaretModel();
              PsiElement elementAt = file.findElementAt(caretModel.getOffset());
              PsiDeclarationStatement declarationStatement = PsiTreeUtil.getParentOfType(elementAt, PsiDeclarationStatement.class);
              if (declarationStatement != null) {
                caretModel.moveToOffset(declarationStatement.getTextRange().getEndOffset());
              }
              new EnterAction().actionPerformed(editor, DataManager.getInstance().getDataContext());
            }
          });
        }
      });
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @Nullable
  private static PsiDeclarationStatement createLocalVariableDeclaration(final PsiInstanceOfExpression instanceOfExpression) throws IncorrectOperationException {
    PsiElementFactory factory = JavaPsiFacade.getInstance(instanceOfExpression.getProject()).getElementFactory();
    PsiTypeCastExpression cast = (PsiTypeCastExpression)factory.createExpressionFromText("(a)b", instanceOfExpression);
    PsiType castType = instanceOfExpression.getCheckType().getType();
    cast.getCastType().replace(factory.createTypeElement(castType));
    cast.getOperand().replace(instanceOfExpression.getOperand());
    PsiDeclarationStatement decl = factory.createVariableDeclarationStatement("xxx", castType, cast);
    PsiDeclarationStatement element = (PsiDeclarationStatement)insertAtAnchor(instanceOfExpression, decl);
    return CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(element);
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
    return anchorAfter.getParent().addAfter(toInsert, anchorAfter);
  }

  private static void reformatNewCodeBlockBraces(final PsiElement start, final PsiBlockStatement end)
    throws IncorrectOperationException {
    CodeStyleManager.getInstance(end.getProject()).reformatRange(end.getContainingFile(),
                                                                 start.getTextRange().getEndOffset(),
                                                                 end.getTextRange().getStartOffset());
  }

  private static boolean isNegated(final PsiInstanceOfExpression instanceOfExpression) {
    PsiElement element = instanceOfExpression.getParent();
    while (element instanceof PsiParenthesizedExpression) {
      element = element.getParent();
    }
    return element instanceof PsiPrefixExpression && ((PsiPrefixExpression)element).getOperationSign().getTokenType() == JavaTokenType.EXCL;
  }

  private static Template generateTemplate(Project project, PsiExpression initializer, PsiType type) {
    final TemplateManager templateManager = TemplateManager.getInstance(project);
    final Template template = templateManager.createTemplate("", "");
    template.setToReformat(true);

    SuggestedNameInfo suggestedNameInfo = JavaCodeStyleManager.getInstance(project).suggestVariableName(VariableKind.LOCAL_VARIABLE, null,
                                                                                                    initializer, type);
    List<String> uniqueNames = new ArrayList<String>();
    for (String name : suggestedNameInfo.names) {
      if (PsiUtil.isVariableNameUnique(name, initializer)) {
        uniqueNames.add(name);
      }
    }
    if (uniqueNames.isEmpty() && suggestedNameInfo.names.length != 0) {
      String baseName = suggestedNameInfo.names[0];
      String name = JavaCodeStyleManager.getInstance(project).suggestUniqueVariableName(baseName, initializer, true);
      uniqueNames.add(name);
    }

    Set<LookupElement> itemSet = new LinkedHashSet<LookupElement>();
    for (String name : uniqueNames) {
      itemSet.add(LookupElementBuilder.create(name));
    }
    final LookupElement[] lookupItems = itemSet.toArray(new LookupElement[itemSet.size()]);
    final Result result = uniqueNames.isEmpty() ? null : new TextResult(uniqueNames.get(0));

    Expression expr = new Expression() {
      public LookupElement[] calculateLookupItems(ExpressionContext context) {
        return lookupItems.length > 1 ? lookupItems : null;
      }

      public Result calculateResult(ExpressionContext context) {
        return result;
      }

      public Result calculateQuickResult(ExpressionContext context) {
        return result;
      }
    };
    template.addVariable("", expr, expr, true);

    return template;
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("create.local.from.instanceof.usage.family");
  }
}
