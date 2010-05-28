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

/**
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: Nov 15, 2002
 * Time: 5:21:33 PM
 * To change this template use Options | File Templates.
 */
package com.intellij.refactoring.introduceVariable;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pass;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.tree.java.ReplaceExpressionUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.IntroduceHandlerBase;
import com.intellij.refactoring.IntroduceTargetChooser;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.introduceField.ElementToWorkOn;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.FieldConflictsResolver;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.occurences.ExpressionOccurenceManager;
import com.intellij.refactoring.util.occurences.NotInSuperCallOccurenceFilter;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public abstract class IntroduceVariableBase extends IntroduceHandlerBase implements RefactoringActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.introduceVariable.IntroduceVariableBase");
  @NonNls private static final String PREFER_STATEMENTS_OPTION = "introduce.variable.prefer.statements";

  protected static String REFACTORING_NAME = RefactoringBundle.message("introduce.variable.title");

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file, DataContext dataContext) {
    if (!editor.getSelectionModel().hasSelection()) {
      final int offset = editor.getCaretModel().getOffset();
      final PsiElement[] statementsInRange = findStatementsAtOffset(editor, file, offset);
      if (statementsInRange.length == 1 && (PsiUtil.hasErrorElementChild(statementsInRange[0]) || isPreferStatements())) {
        editor.getSelectionModel().selectLineAtCaret();
      } else {
        final List<PsiExpression> expressions = collectExpressions(file, editor, offset, statementsInRange);
        if (expressions.isEmpty()) {
          editor.getSelectionModel().selectLineAtCaret();
        } else if (expressions.size() == 1) {
          final TextRange textRange = expressions.get(0).getTextRange();
          editor.getSelectionModel().setSelection(textRange.getStartOffset(), textRange.getEndOffset());
        }
        else {
          IntroduceTargetChooser.showChooser(editor, expressions,
            new Pass<PsiExpression>(){
              public void pass(final PsiExpression selectedValue) {
                invoke(project, editor, file, selectedValue.getTextRange().getStartOffset(), selectedValue.getTextRange().getEndOffset());
              }
            },
            new PsiExpressionTrimRenderer.RenderFunction());
          return;
        }
      }
    }
    if (invoke(project, editor, file, editor.getSelectionModel().getSelectionStart(), editor.getSelectionModel().getSelectionEnd())) {
      editor.getSelectionModel().removeSelection();
    }
  }

  public static boolean isPreferStatements() {
    return Boolean.valueOf(PropertiesComponent.getInstance().getOrInit(PREFER_STATEMENTS_OPTION, "false")).booleanValue();
  }

  public static List<PsiExpression> collectExpressions(final PsiFile file, final Editor editor, final int offset, final PsiElement... statementsInRange) {
    Document document = editor.getDocument();
    CharSequence text = document.getCharsSequence();
    int correctedOffset = offset;
    int textLength = document.getTextLength();
    if (offset >= textLength) {
      correctedOffset = textLength - 1;
    }
    else if (!Character.isJavaIdentifierPart(text.charAt(offset))) {
      correctedOffset--;
    }
    if (correctedOffset < 0) {
      correctedOffset = offset;
    }
    else if (!Character.isJavaIdentifierPart(text.charAt(correctedOffset))) {
      if (text.charAt(correctedOffset) != ')') {
        correctedOffset = offset;
      }
    }
    final PsiElement elementAtCaret = file.findElementAt(correctedOffset);
    final List<PsiExpression> expressions = new ArrayList<PsiExpression>();
    /*for (PsiElement element : statementsInRange) {
      if (element instanceof PsiExpressionStatement) {
        final PsiExpression expression = ((PsiExpressionStatement)element).getExpression();
        if (expression.getType() != PsiType.VOID) {
          expressions.add(expression);
        }
      }
    }*/
    PsiExpression expression = PsiTreeUtil.getParentOfType(elementAtCaret, PsiExpression.class);
    while (expression != null) {
      if (!expressions.contains(expression) && !(expression instanceof PsiParenthesizedExpression) && !(expression instanceof PsiSuperExpression) && expression.getType() != PsiType.VOID) {
        if (!(expression instanceof PsiReferenceExpression && (expression.getParent() instanceof PsiMethodCallExpression ||
                                                               ((PsiReferenceExpression)expression).resolve() instanceof PsiClass))
            && !(expression instanceof PsiAssignmentExpression)) {
          expressions.add(expression);
        }
      }
      expression = PsiTreeUtil.getParentOfType(expression, PsiExpression.class);
    }
    return expressions;
  }

  public static PsiElement[] findStatementsAtOffset(final Editor editor, final PsiFile file, final int offset) {
    final Document document = editor.getDocument();
    final int lineNumber = document.getLineNumber(offset);
    final int lineStart = document.getLineStartOffset(lineNumber);
    final int lineEnd = document.getLineEndOffset(lineNumber);

    return CodeInsightUtil.findStatementsInRange(file, lineStart, lineEnd);
  }

  private boolean invoke(final Project project, final Editor editor, PsiFile file, int startOffset, int endOffset) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("refactoring.introduceVariable");
    PsiDocumentManager.getInstance(project).commitAllDocuments();


    PsiExpression tempExpr = CodeInsightUtil.findExpressionInRange(file, startOffset, endOffset);
    if (tempExpr == null) {
      PsiElement[] statements = CodeInsightUtil.findStatementsInRange(file, startOffset, endOffset);
      if (statements.length == 1) {
        if (statements[0] instanceof PsiExpressionStatement) {
          tempExpr = ((PsiExpressionStatement) statements[0]).getExpression();
        } else if (statements[0] instanceof PsiReturnStatement) {
          tempExpr = ((PsiReturnStatement)statements[0]).getReturnValue();
        }
      }
    }

    if (tempExpr == null) {
      tempExpr = getSelectedExpression(project, file, startOffset, endOffset);
    }
    return invokeImpl(project, tempExpr, editor);
  }

  public static PsiExpression getSelectedExpression(final Project project, final PsiFile file, final int startOffset, final int endOffset) {

    final PsiElement elementAtStart = file.findElementAt(startOffset);
    if (elementAtStart == null) return null;
    final PsiElement elementAtEnd = file.findElementAt(endOffset - 1);
    if (elementAtEnd == null) return null;

    PsiExpression tempExpr;
    final PsiElement elementAt = PsiTreeUtil.findCommonParent(elementAtStart, elementAtEnd);
    if (PsiTreeUtil.getParentOfType(elementAt, PsiExpression.class, false) == null) return null;
    final PsiLiteralExpression literalExpression = PsiTreeUtil.getParentOfType(elementAt, PsiLiteralExpression.class);

    final PsiLiteralExpression startLiteralExpression = PsiTreeUtil.getParentOfType(elementAtStart, PsiLiteralExpression.class);
    final PsiLiteralExpression endLiteralExpression = PsiTreeUtil.getParentOfType(file.findElementAt(endOffset), PsiLiteralExpression.class);

    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
    try {
      String text = file.getText().subSequence(startOffset, endOffset).toString();
      String prefix = null;
      String suffix = null;
      String stripped = text;
      if (startLiteralExpression != null) {
        final int startExpressionOffset = startLiteralExpression.getTextOffset();
        if (startOffset == startExpressionOffset) {
          if (StringUtil.startsWithChar(text, '\"') || StringUtil.startsWithChar(text, '\'')) {
            stripped = text.substring(1);
          }
        } else if (startOffset == startExpressionOffset + 1) {
          text = "\"" + text;
        } else if (startOffset > startExpressionOffset + 1){
          prefix = "\" + ";
          text = "\"" + text;
        }
      }

      if (endLiteralExpression != null) {
        final int endExpressionOffset = endLiteralExpression.getTextOffset() + endLiteralExpression.getTextLength();
        if (endOffset == endExpressionOffset ) {
          if (StringUtil.endsWithChar(stripped, '\"') || StringUtil.endsWithChar(stripped, '\'')) {
            stripped = stripped.substring(0, stripped.length() - 1);
          }
        } else if (endOffset == endExpressionOffset - 1) {
          text += "\"";
        } else if (endOffset < endExpressionOffset - 1) {
          suffix = " + \"";
          text += "\"";
        }
      }

      boolean primitive = false;
      if (stripped.equals("true") || stripped.equals("false")) {
        primitive = true;
      }
      else {
        try {
          Integer.parseInt(stripped);
          primitive = true;
        }
        catch (NumberFormatException e1) {
          //then not primitive
        }
      }

      if (primitive) {
        text = stripped;
      }

      final PsiElement parent = literalExpression != null ? literalExpression : elementAt;
      tempExpr = elementFactory.createExpressionFromText(text, parent);

      final boolean [] hasErrors = new boolean[1];
      final JavaRecursiveElementWalkingVisitor errorsVisitor = new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitElement(final PsiElement element) {
          if (hasErrors[0]) {
            return;
          }
          super.visitElement(element);
        }

        @Override
        public void visitErrorElement(final PsiErrorElement element) {
          hasErrors[0] = true;
        }
      };
      tempExpr.accept(errorsVisitor);
      if (hasErrors[0]) return null;

      tempExpr.putUserData(ElementToWorkOn.PREFIX, prefix);
      tempExpr.putUserData(ElementToWorkOn.SUFFIX, suffix);

      final RangeMarker rangeMarker =
        FileDocumentManager.getInstance().getDocument(file.getVirtualFile()).createRangeMarker(startOffset, endOffset);
      tempExpr.putUserData(ElementToWorkOn.TEXT_RANGE, rangeMarker);

      tempExpr.putUserData(ElementToWorkOn.PARENT, parent);

      final String fakeInitializer = "intellijidearulezzz";
      final int[] refIdx = new int[1];
      final PsiExpression toBeExpression = createReplacement(fakeInitializer, project, prefix, suffix, parent, rangeMarker, refIdx);
      toBeExpression.accept(errorsVisitor);
      if (hasErrors[0]) return null;

      final PsiReferenceExpression refExpr = PsiTreeUtil.getParentOfType(toBeExpression.findElementAt(refIdx[0]), PsiReferenceExpression.class);
      assert refExpr != null;
      if (ReplaceExpressionUtil.isNeedParenthesis(refExpr.getNode(), tempExpr.getNode())) {
        return null;
      }
    }
    catch (IncorrectOperationException e) {
      return null;
    }

    return tempExpr;
  }

  protected boolean invokeImpl(final Project project, final PsiExpression expr,
                               final Editor editor) {
    if (expr != null && expr.getParent() instanceof PsiExpressionStatement) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed("refactoring.introduceVariable.incompleteStatement");
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("expression:" + expr);
    }

    if (expr == null) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("selected.block.should.represent.an.expression"));
      showErrorMessage(project, editor, message);
      return false;
    }

    final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();


    PsiType originalType = RefactoringUtil.getTypeByExpressionWithExpectedType(expr);
    if (originalType == null) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("unknown.expression.type"));
      showErrorMessage(project, editor, message);
      return false;
    }

    if (PsiType.VOID.equals(originalType)) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("selected.expression.has.void.type"));
      showErrorMessage(project, editor, message);
      return false;
    }


    final PsiElement physicalElement = expr.getUserData(ElementToWorkOn.PARENT);

    PsiElement anchorStatement = RefactoringUtil.getParentStatement(physicalElement != null ? physicalElement : expr, false);

    if (anchorStatement == null) {
      return parentStatementNotFound(project, editor);
    }
    if (anchorStatement instanceof PsiExpressionStatement) {
      PsiExpression enclosingExpr = ((PsiExpressionStatement)anchorStatement).getExpression();
      if (enclosingExpr instanceof PsiMethodCallExpression) {
        PsiMethod method = ((PsiMethodCallExpression)enclosingExpr).resolveMethod();
        if (method != null && method.isConstructor()) {
          //This is either 'this' or 'super', both must be the first in the respective contructor
          String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("invalid.expression.context"));
          showErrorMessage(project, editor, message);
          return false;
        }
      }
    }

    PsiElement tempContainer = anchorStatement.getParent();

    if (!(tempContainer instanceof PsiCodeBlock) && !isLoopOrIf(tempContainer)) {
      String message = RefactoringBundle.message("refactoring.is.not.supported.in.the.current.context", REFACTORING_NAME);
      showErrorMessage(project, editor, message);
      return false;
    }

    if(!NotInSuperCallOccurenceFilter.INSTANCE.isOK(expr)) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("cannot.introduce.variable.in.super.constructor.call"));
      showErrorMessage(project, editor, message);
      return false;
    }

    final PsiFile file = anchorStatement.getContainingFile();
    LOG.assertTrue(file != null, "expr.getContainingFile() == null");

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, file)) return false;

    PsiElement containerParent = tempContainer;
    PsiElement lastScope = tempContainer;
    while (true) {
      if (containerParent instanceof PsiFile) break;
      if (containerParent instanceof PsiMethod) break;
      containerParent = containerParent.getParent();
      if (containerParent instanceof PsiCodeBlock) {
        lastScope = containerParent;
      }
    }

    ExpressionOccurenceManager occurenceManager = new ExpressionOccurenceManager(expr, lastScope,
                                                                                 NotInSuperCallOccurenceFilter.INSTANCE);
    final PsiExpression[] occurrences = occurenceManager.getOccurences();
    final PsiElement anchorStatementIfAll = occurenceManager.getAnchorStatementForAll();
    boolean declareFinalIfAll = occurenceManager.isInFinalContext();


    boolean anyAssignmentLHS = false;
    for (PsiExpression occurrence : occurrences) {
      if (RefactoringUtil.isAssignmentLHS(occurrence)) {
        anyAssignmentLHS = true;
        break;
      }
    }


    IntroduceVariableSettings settings = getSettings(project, editor, expr, occurrences, anyAssignmentLHS, declareFinalIfAll,
                                                     originalType,
                                                     new TypeSelectorManagerImpl(project, originalType, expr, occurrences),
                                                     new InputValidator(this, project, anchorStatementIfAll, anchorStatement, occurenceManager));

    if (!settings.isOK()) {
      return false;
    }

    final String variableName = settings.getEnteredName();

    final PsiType type = settings.getSelectedType();
    final boolean replaceAll = settings.isReplaceAllOccurrences();
    final boolean replaceWrite = settings.isReplaceLValues();
    final boolean declareFinal = replaceAll && declareFinalIfAll || settings.isDeclareFinal();
    if (replaceAll) {
      anchorStatement = anchorStatementIfAll;
      tempContainer = anchorStatement.getParent();
    }

    final PsiElement container = tempContainer;

    PsiElement child = anchorStatement;
    if (!isLoopOrIf(container)) {
      child = locateAnchor(child);
    }
    final PsiElement anchor = child == null ? anchorStatement : child;

    boolean tempDeleteSelf = false;
    final boolean replaceSelf = replaceWrite || !RefactoringUtil.isAssignmentLHS(expr);
    if (!isLoopOrIf(container)) {
      if (expr.getParent() instanceof PsiExpressionStatement && anchor.equals(anchorStatement)) {
        PsiStatement statement = (PsiStatement) expr.getParent();
        PsiElement parent = statement.getParent();
        if (parent instanceof PsiCodeBlock ||
            //fabrique
            parent instanceof PsiCodeFragment) {
          tempDeleteSelf = true;
        }
      }
      tempDeleteSelf &= replaceSelf;
    }
    final boolean deleteSelf = tempDeleteSelf;


    final int col = editor != null ? editor.getCaretModel().getLogicalPosition().column : 0;
    final int line = editor != null ? editor.getCaretModel().getLogicalPosition().line : 0;
    if (deleteSelf) {
      if (editor != null) {
        LogicalPosition pos = new LogicalPosition(line, col);
        editor.getCaretModel().moveToLogicalPosition(pos);
      }
    }

    final PsiCodeBlock newDeclarationScope = PsiTreeUtil.getParentOfType(container, PsiCodeBlock.class, false);
    final FieldConflictsResolver fieldConflictsResolver = new FieldConflictsResolver(variableName, newDeclarationScope);

    final PsiElement finalAnchorStatement = anchorStatement;
    final Runnable runnable = new Runnable() {
      public void run() {
        try {
          PsiStatement statement = null;
          final boolean isInsideLoop = isLoopOrIf(container);
          if (!isInsideLoop && deleteSelf) {
            statement = (PsiStatement) expr.getParent();
          }
          final PsiExpression expr1 = fieldConflictsResolver.fixInitializer(expr);
          PsiExpression initializer = RefactoringUtil.unparenthesizeExpression(expr1);
          if (expr1 instanceof PsiNewExpression) {
            final PsiNewExpression newExpression = (PsiNewExpression)expr1;
            if (newExpression.getArrayInitializer() != null) {
              initializer = newExpression.getArrayInitializer();
            }
          }
          PsiDeclarationStatement declaration = factory.createVariableDeclarationStatement(variableName, type, initializer);
          if (!isInsideLoop) {
            declaration = (PsiDeclarationStatement) container.addBefore(declaration, anchor);
            LOG.assertTrue(expr1.isValid());
            if (deleteSelf) { // never true
              final PsiElement lastChild = statement.getLastChild();
              if (lastChild instanceof PsiComment) { // keep trailing comment
                declaration.addBefore(lastChild, null);
              }
              statement.delete();
              if (editor != null) {
                LogicalPosition pos = new LogicalPosition(line, col);
                editor.getCaretModel().moveToLogicalPosition(pos);
                editor.getCaretModel().moveToOffset(declaration.getTextRange().getEndOffset());
                editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
                editor.getSelectionModel().removeSelection();
              }
            }
          }

          PsiExpression ref = factory.createExpressionFromText(variableName, null);
          if (replaceAll) {
            ArrayList<PsiElement> array = new ArrayList<PsiElement>();
            for (PsiExpression occurrence : occurrences) {
              if (deleteSelf && occurrence.equals(expr)) continue;
              if (occurrence.equals(expr)) {
                occurrence = expr1;
              }
              if (occurrence != null) {
                occurrence = RefactoringUtil.outermostParenthesizedExpression(occurrence);
              }
              if (replaceWrite || !RefactoringUtil.isAssignmentLHS(occurrence)) {
                array.add(replace(occurrence, ref, project));
              }
            }

            if (editor != null) {
              final PsiElement[] replacedOccurences = array.toArray(new PsiElement[array.size()]);
              highlightReplacedOccurences(project, editor, replacedOccurences);
            }
          } else {
            if (!deleteSelf && replaceSelf) {
              replace(expr1, ref, project);
            }
          }

          declaration = (PsiDeclarationStatement) putStatementInLoopBody(declaration, container, finalAnchorStatement);
          declaration = (PsiDeclarationStatement)JavaCodeStyleManager.getInstance(project).shortenClassReferences(declaration);
          PsiVariable var = (PsiVariable) declaration.getDeclaredElements()[0];
          PsiUtil.setModifierProperty(var, PsiModifier.FINAL, declareFinal);

          fieldConflictsResolver.fix();
        } catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    };

    CommandProcessor.getInstance().executeCommand(
      project,
      new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(runnable);
        }
      }, REFACTORING_NAME, null);
    return true;
  }

  public static PsiElement replace(final PsiExpression expr1, final PsiExpression ref, final Project project)
    throws IncorrectOperationException {
    final PsiExpression expr2 = RefactoringUtil.outermostParenthesizedExpression(expr1);
    if (expr2.isPhysical()) {
      return expr2.replace(ref);
    }
    else {
      final String prefix  = expr1.getUserData(ElementToWorkOn.PREFIX);
      final String suffix  = expr1.getUserData(ElementToWorkOn.SUFFIX);
      final PsiElement parent = expr1.getUserData(ElementToWorkOn.PARENT);
      final RangeMarker rangeMarker = expr1.getUserData(ElementToWorkOn.TEXT_RANGE);

      return parent.replace(createReplacement(ref.getText(), project, prefix, suffix, parent, rangeMarker, new int[1]));
    }
  }

  private static PsiExpression createReplacement(final String refText, final Project project,
                                                 final String prefix,
                                                 final String suffix,
                                                 final PsiElement parent, final RangeMarker rangeMarker, int[] refIdx) {
    final String allText = parent.getContainingFile().getText();
    final TextRange parentRange = parent.getTextRange();

    String beg = allText.substring(parentRange.getStartOffset(), rangeMarker.getStartOffset());
    if (StringUtil.stripQuotesAroundValue(beg).trim().length() == 0 && prefix == null) beg = "";

    String end = allText.substring(rangeMarker.getEndOffset(), parentRange.getEndOffset());
    if (StringUtil.stripQuotesAroundValue(end).trim().length() == 0 && suffix == null) end = "";

    final String start = beg + (prefix != null ? prefix : "");
    refIdx[0] = start.length();
    final String text = start + refText + (suffix != null ? suffix : "") + end;
    return JavaPsiFacade.getInstance(project).getElementFactory().createExpressionFromText(text, parent);
  }

  public static PsiStatement putStatementInLoopBody(PsiStatement declaration, PsiElement container, PsiElement finalAnchorStatement)
    throws IncorrectOperationException {
    if(isLoopOrIf(container)) {
      PsiStatement loopBody = getLoopBody(container, finalAnchorStatement);
      PsiStatement loopBodyCopy = loopBody != null ? (PsiStatement) loopBody.copy() : null;
      PsiBlockStatement blockStatement = (PsiBlockStatement)JavaPsiFacade.getInstance(container.getProject()).getElementFactory()
        .createStatementFromText("{}", null);
      blockStatement = (PsiBlockStatement) CodeStyleManager.getInstance(container.getProject()).reformat(blockStatement);
      final PsiElement prevSibling = loopBody.getPrevSibling();
      if(prevSibling instanceof PsiWhiteSpace) {
        final PsiElement pprev = prevSibling.getPrevSibling();
        if (!(pprev instanceof PsiComment) || !((PsiComment)pprev).getTokenType().equals(JavaTokenType.END_OF_LINE_COMMENT)) {
          prevSibling.delete();
        }
      }
      blockStatement = (PsiBlockStatement) loopBody.replace(blockStatement);
      final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
      declaration = (PsiStatement) codeBlock.add(declaration);
      JavaCodeStyleManager.getInstance(declaration.getProject()).shortenClassReferences(declaration);
      if (loopBodyCopy != null) codeBlock.add(loopBodyCopy);
    }
    return declaration;
  }

  private boolean parentStatementNotFound(final Project project, Editor editor) {
    String message = RefactoringBundle.message("refactoring.is.not.supported.in.the.current.context", REFACTORING_NAME);
    showErrorMessage(project, editor, message);
    return false;
  }

  protected boolean invokeImpl(Project project, PsiLocalVariable localVariable, Editor editor) {
    throw new UnsupportedOperationException();
  }

  private static PsiElement locateAnchor(PsiElement child) {
    while (child != null) {
      PsiElement prev = child.getPrevSibling();
      if (prev instanceof PsiStatement) break;
      if (prev instanceof PsiJavaToken && ((PsiJavaToken)prev).getTokenType() == JavaTokenType.LBRACE) break;
      child = prev;
    }

    while (child instanceof PsiWhiteSpace || child instanceof PsiComment) {
      child = child.getNextSibling();
    }
    return child;
  }

  protected abstract void highlightReplacedOccurences(Project project, Editor editor, PsiElement[] replacedOccurences);

  protected abstract IntroduceVariableSettings getSettings(Project project, Editor editor, PsiExpression expr, final PsiElement[] occurrences,
                                                           boolean anyAssignmentLHS, final boolean declareFinalIfAll, final PsiType type,
                                                           TypeSelectorManagerImpl typeSelectorManager, InputValidator validator);

  protected abstract void showErrorMessage(Project project, Editor editor, String message);

  @Nullable
  private static PsiStatement getLoopBody(PsiElement container, PsiElement anchorStatement) {
    if(container instanceof PsiLoopStatement) {
      return ((PsiLoopStatement) container).getBody();
    }
    else if (container instanceof PsiIfStatement) {
      final PsiStatement thenBranch = ((PsiIfStatement)container).getThenBranch();
      if (thenBranch != null && PsiTreeUtil.isAncestor(thenBranch, anchorStatement, false)) {
        return thenBranch;
      }
      final PsiStatement elseBranch = ((PsiIfStatement)container).getElseBranch();
      if (elseBranch != null && PsiTreeUtil.isAncestor(elseBranch, anchorStatement, false)) {
        return elseBranch;
      }
      LOG.assertTrue(false);
    }
    LOG.assertTrue(false);
    return null;
  }


  public static boolean isLoopOrIf(PsiElement element) {
    return element instanceof PsiLoopStatement || element instanceof PsiIfStatement;
  }

  public interface Validator {
    boolean isOK(IntroduceVariableSettings dialog);
  }

  protected abstract boolean reportConflicts(MultiMap<PsiElement,String> conflicts, final Project project, IntroduceVariableSettings dialog);


  public static void checkInLoopCondition(PsiExpression occurence, MultiMap<PsiElement, String> conflicts) {
    final PsiElement loopForLoopCondition = RefactoringUtil.getLoopForLoopCondition(occurence);
    if (loopForLoopCondition == null) return;
    final List<PsiVariable> referencedVariables = RefactoringUtil.collectReferencedVariables(occurence);
    final List<PsiVariable> modifiedInBody = new ArrayList<PsiVariable>();
    for (PsiVariable psiVariable : referencedVariables) {
      if (RefactoringUtil.isModifiedInScope(psiVariable, loopForLoopCondition)) {
        modifiedInBody.add(psiVariable);
      }
    }

    if (!modifiedInBody.isEmpty()) {
      for (PsiVariable variable : modifiedInBody) {
        final String message = RefactoringBundle.message("is.modified.in.loop.body", RefactoringUIUtil.getDescription(variable, false));
        conflicts.putValue(variable, CommonRefactoringUtil.capitalize(message));
      }
      conflicts.putValue(occurence, RefactoringBundle.message("introducing.variable.may.break.code.logic"));
    }
  }


}
