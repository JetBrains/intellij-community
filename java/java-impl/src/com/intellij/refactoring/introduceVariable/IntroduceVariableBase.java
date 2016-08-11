/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.refactoring.introduceVariable;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.unwrap.ScopeHighlighter;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.featureStatistics.ProductivityFeatureNames;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.lang.LanguageRefactoringSupport;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pass;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
import com.intellij.psi.impl.source.jsp.jspJava.JspCodeBlock;
import com.intellij.psi.impl.source.jsp.jspJava.JspHolderMethod;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.impl.source.tree.java.ReplaceExpressionUtil;
import com.intellij.psi.scope.processor.VariablesProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.util.*;
import com.intellij.refactoring.*;
import com.intellij.refactoring.introduce.inplace.AbstractInplaceIntroducer;
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser;
import com.intellij.refactoring.introduceField.ElementToWorkOn;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.listeners.RefactoringEventListener;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.FieldConflictsResolver;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.occurrences.ExpressionOccurrenceManager;
import com.intellij.refactoring.util.occurrences.NotInSuperCallOccurrenceFilter;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author dsl
 * Date: Nov 15, 2002
 */
public abstract class IntroduceVariableBase extends IntroduceHandlerBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.introduceVariable.IntroduceVariableBase");
  @NonNls private static final String PREFER_STATEMENTS_OPTION = "introduce.variable.prefer.statements";
  @NonNls private static final String REFACTORING_ID = "refactoring.extractVariable";
  
  protected static final String REFACTORING_NAME = RefactoringBundle.message("introduce.variable.title");
  public static final Key<Boolean> NEED_PARENTHESIS = Key.create("NEED_PARENTHESIS");
  private JavaVariableInplaceIntroducer myInplaceIntroducer;

  public static SuggestedNameInfo getSuggestedName(@Nullable PsiType type, @NotNull final PsiExpression expression) {
    return getSuggestedName(type, expression, expression);
  }

  public static SuggestedNameInfo getSuggestedName(@Nullable PsiType type,
                                                   @NotNull final PsiExpression expression,
                                                   final PsiElement anchor) {
    final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(expression.getProject());
    final SuggestedNameInfo nameInfo = codeStyleManager.suggestVariableName(VariableKind.LOCAL_VARIABLE, null, expression, type);
    final String[] strings = JavaCompletionUtil
      .completeVariableNameForRefactoring(codeStyleManager, type, VariableKind.LOCAL_VARIABLE, nameInfo);
    final SuggestedNameInfo.Delegate delegate = new SuggestedNameInfo.Delegate(strings, nameInfo);
    return codeStyleManager.suggestUniqueVariableName(delegate, anchor, true);
  }

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file, DataContext dataContext) {
    final SelectionModel selectionModel = editor.getSelectionModel();
    if (!selectionModel.hasSelection()) {
      final int offset = editor.getCaretModel().getOffset();
      final PsiElement[] statementsInRange = findStatementsAtOffset(editor, file, offset);

      //try line selection
      if (statementsInRange.length == 1 && selectLineAtCaret(offset, statementsInRange)) {
        selectionModel.selectLineAtCaret();
        final PsiExpression expressionInRange =
          findExpressionInRange(project, file, selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
        if (expressionInRange == null || getErrorMessage(expressionInRange) != null) {
          selectionModel.removeSelection();
        }
      }

      if (!selectionModel.hasSelection()) {
        final List<PsiExpression> expressions = collectExpressions(file, editor, offset);
        if (expressions.isEmpty()) {
          selectionModel.selectLineAtCaret();
        } else if (!isChooserNeeded(expressions)) {
          final TextRange textRange = expressions.get(0).getTextRange();
          selectionModel.setSelection(textRange.getStartOffset(), textRange.getEndOffset());
        }
        else {
          IntroduceTargetChooser.showChooser(editor, expressions,
            new Pass<PsiExpression>(){
              public void pass(final PsiExpression selectedValue) {
                invoke(project, editor, file, selectedValue.getTextRange().getStartOffset(), selectedValue.getTextRange().getEndOffset());
              }
            },
            new PsiExpressionTrimRenderer.RenderFunction(), "Expressions", preferredSelection(statementsInRange, expressions), ScopeHighlighter.NATURAL_RANGER);
          return;
        }
      }
    }
    if (invoke(project, editor, file, selectionModel.getSelectionStart(), selectionModel.getSelectionEnd()) &&
        LookupManager.getActiveLookup(editor) == null) {
      selectionModel.removeSelection();
    }
  }

  public static boolean isChooserNeeded(List<PsiExpression> exprs) {
    if (exprs.size() == 1) {
      final PsiExpression expression = exprs.get(0);
      return expression instanceof PsiNewExpression && ((PsiNewExpression)expression).getAnonymousClass() != null;
    }
    return true;
  }
  
  public static boolean selectLineAtCaret(int offset, PsiElement[] statementsInRange) {
    TextRange range = statementsInRange[0].getTextRange();
    if (statementsInRange[0] instanceof PsiExpressionStatement) {
      range = ((PsiExpressionStatement)statementsInRange[0]).getExpression().getTextRange();
    }

    return range.getStartOffset() > offset ||
           range.getEndOffset() <= offset ||
           isPreferStatements();
  }

  public static int preferredSelection(PsiElement[] statementsInRange, List<PsiExpression> expressions) {
    int selection;
    if (statementsInRange.length == 1 &&
        statementsInRange[0] instanceof PsiExpressionStatement &&
        PsiUtilCore.hasErrorElementChild(statementsInRange[0])) {
      selection = expressions.indexOf(((PsiExpressionStatement)statementsInRange[0]).getExpression());
    } else {
      PsiExpression expression = expressions.get(0);
      if (expression instanceof PsiReferenceExpression && ((PsiReferenceExpression)expression).resolve() instanceof PsiLocalVariable) {
        selection = 1;
      }
      else {
        selection = -1;
      }
    }
    return selection;
  }

  public static boolean isPreferStatements() {
    return Boolean.valueOf(PropertiesComponent.getInstance().getBoolean(PREFER_STATEMENTS_OPTION)) || Registry.is(PREFER_STATEMENTS_OPTION, false);
  }

  public static List<PsiExpression> collectExpressions(final PsiFile file,
                                                       final Editor editor,
                                                       final int offset) {
    return collectExpressions(file, editor, offset, false);
  }

  public static List<PsiExpression> collectExpressions(final PsiFile file,
                                                       final Editor editor,
                                                       final int offset,
                                                       boolean acceptVoid) {
    return collectExpressions(file, editor.getDocument(), offset, acceptVoid);
  }

  public static List<PsiExpression> collectExpressions(final PsiFile file,
                                                       final Document document,
                                                       final int offset,
                                                       boolean acceptVoid) {
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
      if (text.charAt(correctedOffset) == ';') {//initially caret on the end of line
        correctedOffset--;
      }
      if (correctedOffset < 0 || text.charAt(correctedOffset) != ')') {
        correctedOffset = offset;
      }
    }
    final PsiElement elementAtCaret = file.findElementAt(correctedOffset);
    final List<PsiExpression> expressions = new ArrayList<>();
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
      if (!expressions.contains(expression) && !(expression instanceof PsiParenthesizedExpression) && !(expression instanceof PsiSuperExpression) &&
          (acceptVoid || !PsiType.VOID.equals(expression.getType()))) {
        if (expression instanceof PsiMethodReferenceExpression) {
          expressions.add(expression);
        }
        else if (!(expression instanceof PsiAssignmentExpression)) {
          if (!(expression instanceof PsiReferenceExpression)) {
            expressions.add(expression);
          }
          else {
            if (!(expression.getParent() instanceof PsiMethodCallExpression)) {
              final PsiElement resolve = ((PsiReferenceExpression)expression).resolve();
              if (!(resolve instanceof PsiClass) && !(resolve instanceof PsiPackage)) {
                expressions.add(expression);
              }
            }
          }
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
    FeatureUsageTracker.getInstance().triggerFeatureUsed(ProductivityFeatureNames.REFACTORING_INTRODUCE_VARIABLE);
    PsiDocumentManager.getInstance(project).commitAllDocuments();


    return invokeImpl(project, findExpressionInRange(project, file, startOffset, endOffset), editor);
  }

  private static PsiExpression findExpressionInRange(Project project, PsiFile file, int startOffset, int endOffset) {
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
    return tempExpr;
  }

  /**
   * @return can return NotNull value although extraction will fail: reason could be retrieved from {@link #getErrorMessage(PsiExpression)}
   */
  public static PsiExpression getSelectedExpression(final Project project, PsiFile file, int startOffset, int endOffset) {
    final InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(project);
    PsiElement elementAtStart = file.findElementAt(startOffset);
    if (elementAtStart == null || elementAtStart instanceof PsiWhiteSpace || elementAtStart instanceof PsiComment) {
      final PsiElement element = PsiTreeUtil.skipSiblingsForward(elementAtStart, PsiWhiteSpace.class, PsiComment.class);
      if (element != null) {
        startOffset = element.getTextOffset();
        elementAtStart = file.findElementAt(startOffset);
      }
      if (elementAtStart == null) {
        if (injectedLanguageManager.isInjectedFragment(file)) {
          return getSelectionFromInjectedHost(project, file, injectedLanguageManager, startOffset, endOffset);
        } else {
          return null;
        }
      }
      startOffset = elementAtStart.getTextOffset();
    }
    PsiElement elementAtEnd = file.findElementAt(endOffset - 1);
    if (elementAtEnd == null || elementAtEnd instanceof PsiWhiteSpace || elementAtEnd instanceof PsiComment) {
      elementAtEnd = PsiTreeUtil.skipSiblingsBackward(elementAtEnd, PsiWhiteSpace.class, PsiComment.class);
      if (elementAtEnd == null) return null;
      endOffset = elementAtEnd.getTextRange().getEndOffset();
    }

    if (endOffset <= startOffset) return null;

    PsiElement elementAt = PsiTreeUtil.findCommonParent(elementAtStart, elementAtEnd);
    final PsiExpression containingExpression = PsiTreeUtil.getParentOfType(elementAt, PsiExpression.class, false);

    if (containingExpression != null && containingExpression == elementAtEnd && startOffset == containingExpression.getTextOffset()) {
      return containingExpression;
    }
    
    if (containingExpression == null || containingExpression instanceof PsiLambdaExpression) {
      if (injectedLanguageManager.isInjectedFragment(file)) {
        return getSelectionFromInjectedHost(project, file, injectedLanguageManager, startOffset, endOffset);
      }
      elementAt = null;
    }
    final PsiLiteralExpression literalExpression = PsiTreeUtil.getParentOfType(elementAt, PsiLiteralExpression.class);

    final PsiLiteralExpression startLiteralExpression = PsiTreeUtil.getParentOfType(elementAtStart, PsiLiteralExpression.class);
    final PsiLiteralExpression endLiteralExpression = PsiTreeUtil.getParentOfType(file.findElementAt(endOffset), PsiLiteralExpression.class);

    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
    String text = null;
    PsiExpression tempExpr;
    try {
      text = file.getText().subSequence(startOffset, endOffset).toString();
      String prefix = null;
      if (startLiteralExpression != null) {
        final int startExpressionOffset = startLiteralExpression.getTextOffset();
        if (startOffset == startExpressionOffset + 1) {
          text = "\"" + text;
        } else if (startOffset > startExpressionOffset + 1){
          prefix = "\" + ";
          text = "\"" + text;
        }
      }

      String suffix = null;
      if (endLiteralExpression != null) {
        final int endExpressionOffset = endLiteralExpression.getTextOffset() + endLiteralExpression.getTextLength();
        if (endOffset == endExpressionOffset - 1) {
          text += "\"";
        } else if (endOffset < endExpressionOffset - 1) {
          suffix = " + \"";
          text += "\"";
        }
      }

      if (literalExpression != null && text.equals(literalExpression.getText())) return literalExpression;

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

      if (parent != null) {
        tempExpr.putUserData(ElementToWorkOn.PARENT, parent);
      }
      else {
        PsiErrorElement errorElement = elementAtStart instanceof PsiErrorElement
                                       ? (PsiErrorElement)elementAtStart
                                       : PsiTreeUtil.getNextSiblingOfType(elementAtStart, PsiErrorElement.class);
        if (errorElement == null) {
          errorElement = PsiTreeUtil.getParentOfType(elementAtStart, PsiErrorElement.class);
        }
        if (errorElement == null) return null;
        if (!(errorElement.getParent() instanceof PsiClass)) return null;
        tempExpr.putUserData(ElementToWorkOn.PARENT, errorElement);
        tempExpr.putUserData(ElementToWorkOn.OUT_OF_CODE_BLOCK, Boolean.TRUE);
      }

      final String fakeInitializer = "intellijidearulezzz";
      final int[] refIdx = new int[1];
      final PsiElement toBeExpression = createReplacement(fakeInitializer, project, prefix, suffix, parent, rangeMarker, refIdx);
      toBeExpression.accept(errorsVisitor);
      if (hasErrors[0]) return null;
      if (literalExpression != null && toBeExpression instanceof PsiExpression) {
        PsiType type = ((PsiExpression)toBeExpression).getType();
        if (type != null && !type.equals(literalExpression.getType())) {
          return null;
        }
      }

      final PsiReferenceExpression refExpr = PsiTreeUtil.getParentOfType(toBeExpression.findElementAt(refIdx[0]), PsiReferenceExpression.class);
      if (refExpr == null) return null;
      if (toBeExpression == refExpr && refIdx[0] > 0) {
        return null;
      }
      if (ReplaceExpressionUtil.isNeedParenthesis(refExpr.getNode(), tempExpr.getNode())) {
        tempExpr.putCopyableUserData(NEED_PARENTHESIS, Boolean.TRUE);
        return tempExpr;
      }
    }
    catch (IncorrectOperationException e) {
      if (elementAt instanceof PsiExpressionList) {
        final PsiElement parent = elementAt.getParent();
        return parent instanceof PsiCallExpression ? createArrayCreationExpression(text, startOffset, endOffset, (PsiCallExpression)parent) : null;
      }
      return null;
    }

    return tempExpr;
  }

  private static PsiExpression getSelectionFromInjectedHost(Project project,
                                                            PsiFile file,
                                                            InjectedLanguageManager injectedLanguageManager, int startOffset, int endOffset) {
    final PsiLanguageInjectionHost injectionHost = injectedLanguageManager.getInjectionHost(file);
    return getSelectedExpression(project, injectionHost.getContainingFile(), injectedLanguageManager.injectedToHost(file, startOffset), injectedLanguageManager.injectedToHost(file, endOffset));
  }

  @Nullable
  public static String getErrorMessage(PsiExpression expr) {
    final Boolean needParenthesis = expr.getCopyableUserData(NEED_PARENTHESIS);
    if (needParenthesis != null && needParenthesis.booleanValue()) {
      return "Extracting selected expression would change the semantic of the whole expression.";
    }
    return null;
  }

  private static PsiExpression createArrayCreationExpression(String text, int startOffset, int endOffset, PsiCallExpression parent) {
    if (text == null || parent == null) return null;
    final String[] varargsExpressions = text.split("s*,s*");
    if (varargsExpressions.length > 1) {
      final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(parent.getProject());
      final JavaResolveResult resolveResult = parent.resolveMethodGenerics();
      final PsiMethod psiMethod = (PsiMethod)resolveResult.getElement();
      if (psiMethod == null || !psiMethod.isVarArgs()) return null;
      final PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
      final PsiParameter varargParameter = parameters[parameters.length - 1];
      final PsiType type = varargParameter.getType();
      LOG.assertTrue(type instanceof PsiEllipsisType);
      final PsiArrayType psiType = (PsiArrayType)((PsiEllipsisType)type).toArrayType();
      final PsiExpression[] args = parent.getArgumentList().getExpressions();
      final PsiSubstitutor psiSubstitutor = resolveResult.getSubstitutor();

      if (args.length < parameters.length || startOffset < args[parameters.length - 1].getTextRange().getStartOffset()) return null;

      final PsiFile containingFile = parent.getContainingFile();

      PsiElement startElement = containingFile.findElementAt(startOffset);
      while (startElement != null && startElement.getParent() != parent.getArgumentList()) {
        startElement = startElement.getParent();
      }
      if (startElement == null || startOffset > startElement.getTextOffset()) return null;

      PsiElement endElement = containingFile.findElementAt(endOffset - 1);
      while (endElement != null && endElement.getParent() != parent.getArgumentList()) {
        endElement = endElement.getParent();
      }
      if (endElement == null || endOffset < endElement.getTextRange().getEndOffset()) return null;

      final PsiType componentType = TypeConversionUtil.erasure(psiSubstitutor.substitute(psiType.getComponentType()));
      try {
        final PsiExpression expressionFromText =
          elementFactory.createExpressionFromText("new " + componentType.getCanonicalText() + "[]{" + text + "}", parent);
        final RangeMarker rangeMarker =
        FileDocumentManager.getInstance().getDocument(containingFile.getVirtualFile()).createRangeMarker(startOffset, endOffset);
        expressionFromText.putUserData(ElementToWorkOn.TEXT_RANGE, rangeMarker);
        expressionFromText.putUserData(ElementToWorkOn.PARENT, parent);
        return expressionFromText;
      }
      catch (IncorrectOperationException e) {
        return null;
      }
    }
    return null;
  }

  protected boolean invokeImpl(final Project project, final PsiExpression expr,
                               final Editor editor) {
    if (expr != null) {
      final String errorMessage = getErrorMessage(expr);
      if (errorMessage != null) {
        showErrorMessage(project, editor, RefactoringBundle.getCannotRefactorMessage(errorMessage));
        return false;
      }
    }

    if (expr != null && expr.getParent() instanceof PsiExpressionStatement) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed("refactoring.introduceVariable.incompleteStatement");
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("expression:" + expr);
    }

    if (expr == null || !expr.isPhysical()) {
      if (ReassignVariableUtil.reassign(editor)) return false;
      if (expr == null) {
        String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("selected.block.should.represent.an.expression"));
        showErrorMessage(project, editor, message);
        return false;
      }
    }


    final PsiType originalType = RefactoringUtil.getTypeByExpressionWithExpectedType(expr);
    if (originalType == null || LambdaUtil.notInferredType(originalType)) {
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

    final PsiElement anchorStatement = RefactoringUtil.getParentStatement(physicalElement != null ? physicalElement : expr, false);

    if (anchorStatement == null) {
      return parentStatementNotFound(project, editor);
    }
    if (checkAnchorBeforeThisOrSuper(project, editor, anchorStatement, REFACTORING_NAME, HelpID.INTRODUCE_VARIABLE)) return false;

    final PsiElement tempContainer = anchorStatement.getParent();

    if (!(tempContainer instanceof PsiCodeBlock) && !RefactoringUtil.isLoopOrIf(tempContainer) && !(tempContainer instanceof PsiLambdaExpression) && (tempContainer.getParent() instanceof PsiLambdaExpression)) {
      String message = RefactoringBundle.message("refactoring.is.not.supported.in.the.current.context", REFACTORING_NAME);
      showErrorMessage(project, editor, message);
      return false;
    }

    if(!NotInSuperCallOccurrenceFilter.INSTANCE.isOK(expr)) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("cannot.introduce.variable.in.super.constructor.call"));
      showErrorMessage(project, editor, message);
      return false;
    }

    final PsiFile file = anchorStatement.getContainingFile();
    LOG.assertTrue(file != null, "expr.getContainingFile() == null");
    final PsiElement nameSuggestionContext = editor == null ? null : file.findElementAt(editor.getCaretModel().getOffset());
    final RefactoringSupportProvider supportProvider = LanguageRefactoringSupport.INSTANCE.forLanguage(expr.getLanguage());
    final boolean isInplaceAvailableOnDataContext =
      supportProvider != null &&
      editor.getSettings().isVariableInplaceRenameEnabled() &&
      supportProvider.isInplaceIntroduceAvailable(expr, nameSuggestionContext) &&
      (!ApplicationManager.getApplication().isUnitTestMode() || isInplaceAvailableInTestMode()) &&
      !isInJspHolderMethod(expr);

    if (isInplaceAvailableOnDataContext) {
      final MultiMap<PsiElement, String> conflicts = new MultiMap<>();
      checkInLoopCondition(expr, conflicts);
      if (!conflicts.isEmpty()) {
        showErrorMessage(project, editor, StringUtil.join(conflicts.values(), "<br>"));
        return false;
      }
    }

    final ExpressionOccurrenceManager occurrenceManager = createOccurrenceManager(expr, tempContainer);
    final PsiExpression[] occurrences = occurrenceManager.getOccurrences();
    final PsiElement anchorStatementIfAll = occurrenceManager.getAnchorStatementForAll();


    final List<PsiExpression> nonWrite = new ArrayList<>();
    boolean cantReplaceAll = false;
    boolean cantReplaceAllButWrite = false;
    for (PsiExpression occurrence : occurrences) {
      if (!RefactoringUtil.isAssignmentLHS(occurrence)) {
        nonWrite.add(occurrence);
      } else if (isFinalVariableOnLHS(occurrence)) {
        cantReplaceAll = true;
      } else if (!nonWrite.isEmpty()){
        cantReplaceAllButWrite = true;
        cantReplaceAll = true;
      }
    }
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, file)) return false;

    final LinkedHashMap<OccurrencesChooser.ReplaceChoice, List<PsiExpression>> occurrencesMap = ContainerUtil.newLinkedHashMap();
    occurrencesMap.put(OccurrencesChooser.ReplaceChoice.NO, Collections.singletonList(expr));
    final boolean hasWriteAccess = occurrences.length > nonWrite.size() && occurrences.length > 1;
    if (hasWriteAccess && !cantReplaceAllButWrite) {
      occurrencesMap.put(OccurrencesChooser.ReplaceChoice.NO_WRITE, nonWrite);
    }

    if (occurrences.length > 1 && !cantReplaceAll) {
      occurrencesMap.put(OccurrencesChooser.ReplaceChoice.ALL, Arrays.asList(occurrences));
    }

    final boolean inFinalContext = occurrenceManager.isInFinalContext();
    final InputValidator validator = new InputValidator(this, project, anchorStatementIfAll, anchorStatement, occurrenceManager);
    final TypeSelectorManagerImpl typeSelectorManager = new TypeSelectorManagerImpl(project, originalType, expr, occurrences);
    final boolean[] wasSucceed = new boolean[]{true};
    final Pass<OccurrencesChooser.ReplaceChoice> callback = new Pass<OccurrencesChooser.ReplaceChoice>() {
      @Override
      public void pass(final OccurrencesChooser.ReplaceChoice choice) {
        if (choice != null) {
          final boolean replaceAll = choice == OccurrencesChooser.ReplaceChoice.ALL || choice == OccurrencesChooser.ReplaceChoice.NO_WRITE;
          typeSelectorManager.setAllOccurrences(replaceAll);

          final PsiElement chosenAnchor =
            chooseAnchor(replaceAll, choice == OccurrencesChooser.ReplaceChoice.NO_WRITE, nonWrite, anchorStatementIfAll, anchorStatement);
          final IntroduceVariableSettings settings =
            getSettings(project, editor, expr, occurrences, typeSelectorManager, inFinalContext, hasWriteAccess, validator, chosenAnchor, choice);
          
          final boolean cantChangeFinalModifier = (hasWriteAccess || inFinalContext) && choice == OccurrencesChooser.ReplaceChoice.ALL;

          final boolean noWrite = choice == OccurrencesChooser.ReplaceChoice.NO_WRITE;
          final List<PsiExpression> allOccurrences = new ArrayList<>();
          for (PsiExpression occurrence : occurrences) {
            if (expr.equals(occurrence) && expr.getParent() instanceof PsiExpressionStatement) continue;
            if (choice == OccurrencesChooser.ReplaceChoice.ALL || (noWrite && !PsiUtil.isAccessedForWriting(occurrence)) ||  expr.equals(occurrence)) {
              allOccurrences.add(occurrence);
            }
          }
          myInplaceIntroducer = new JavaVariableInplaceIntroducer(project,
                                            settings,
                                            chosenAnchor,
                                            editor, expr, cantChangeFinalModifier,
                                            allOccurrences.toArray(new PsiExpression[allOccurrences.size()]),
                                            typeSelectorManager,
                                            REFACTORING_NAME);
          if (myInplaceIntroducer.startInplaceIntroduceTemplate()) {
            return;
          }
        }

        CommandProcessor.getInstance().executeCommand(
          project,
          () -> {
            final Editor topLevelEditor ;
            if (!InjectedLanguageManager.getInstance(project).isInjectedFragment(anchorStatement.getContainingFile())) {
              topLevelEditor = InjectedLanguageUtil.getTopLevelEditor(editor);
            } else {
              topLevelEditor = editor;
            }

            PsiVariable variable = null;
            try {
              final IntroduceVariableSettings settings =
                getSettings(project, topLevelEditor, expr, occurrences, typeSelectorManager, inFinalContext, hasWriteAccess, validator, anchorStatement, choice);
              if (!settings.isOK()) {
                wasSucceed[0] = false;
                return;
              }

              final RefactoringEventData beforeData = new RefactoringEventData();
              beforeData.addElement(expr);
              project.getMessageBus()
                .syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC).refactoringStarted(REFACTORING_ID, beforeData);

              final PsiElement chosenAnchor =
                chooseAnchor(settings.isReplaceAllOccurrences(), hasWriteAccess, nonWrite, anchorStatementIfAll, anchorStatement);

              variable = ApplicationManager.getApplication().runWriteAction(
                introduce(project, expr, topLevelEditor, chosenAnchor, occurrences, settings));
            }
            finally {
              final RefactoringEventData afterData = new RefactoringEventData();
              afterData.addElement(variable);
              project.getMessageBus()
                .syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC).refactoringDone(REFACTORING_ID, afterData);
            }
          }, REFACTORING_NAME, null);
      }
    };

    if (!isInplaceAvailableOnDataContext) {
      callback.pass(null);
    }
    else {
      OccurrencesChooser.ReplaceChoice choice = getOccurrencesChoice();
      if (choice != null) {
        callback.pass(choice);
      } else {
        OccurrencesChooser.<PsiExpression>simpleChooser(editor).showChooser(callback, occurrencesMap);
      }
    }
    return wasSucceed[0];
  }
  
  protected OccurrencesChooser.ReplaceChoice getOccurrencesChoice() {
    return null;
  }

  protected static PsiElement chooseAnchor(boolean allOccurences,
                                           boolean hasWriteAccess,
                                           List<PsiExpression> nonWrite,
                                           PsiElement anchorStatementIfAll,
                                           PsiElement anchorStatement) {
    if (allOccurences) {
      if (hasWriteAccess) {
        return RefactoringUtil.getAnchorElementForMultipleExpressions(nonWrite.toArray(new PsiExpression[nonWrite.size()]), null);
      }
      else {
        return anchorStatementIfAll;
      }
    }
    else {
      return anchorStatement;
    }
  }

  protected boolean isInplaceAvailableInTestMode() {
    return false;
  }

  private static ExpressionOccurrenceManager createOccurrenceManager(PsiExpression expr, PsiElement tempContainer) {
    boolean skipForStatement = true;
    final PsiForStatement forStatement = PsiTreeUtil.getParentOfType(expr, PsiForStatement.class);
    if (forStatement != null) {
      final VariablesProcessor variablesProcessor = new VariablesProcessor(false) {
        @Override
        protected boolean check(PsiVariable var, ResolveState state) {
          return PsiTreeUtil.isAncestor(forStatement.getInitialization(), var, true);
        }
      };
      PsiScopesUtil.treeWalkUp(variablesProcessor, expr, null);
      skipForStatement = variablesProcessor.size() == 0;
    }

    PsiElement containerParent = tempContainer;
    PsiElement lastScope = tempContainer;
    while (true) {
      if (containerParent instanceof PsiFile) break;
      if (containerParent instanceof PsiMethod) break;
      // allow to find occurrences outside lambda as we allow this for loops, ifs, etc
      // if (containerParent instanceof PsiLambdaExpression) break;
      if (!skipForStatement && containerParent instanceof PsiForStatement) break;
      containerParent = containerParent.getParent();
      if (containerParent instanceof PsiCodeBlock) {
        lastScope = containerParent;
      }
    }

    return new ExpressionOccurrenceManager(expr, lastScope, NotInSuperCallOccurrenceFilter.INSTANCE);
  }

  private static boolean isInJspHolderMethod(PsiExpression expr) {
    final PsiElement parent1 = expr.getParent();
    if (parent1 == null) {
      return false;
    }
    final PsiElement parent2 = parent1.getParent();
    if (!(parent2 instanceof JspCodeBlock)) return false;
    final PsiElement parent3 = parent2.getParent();
    return parent3 instanceof JspHolderMethod;
  }

  public static Computable<PsiVariable> introduce(final Project project,
                                                  final PsiExpression expr,
                                                  final Editor editor,
                                                  final PsiElement anchorStatement,
                                                  final PsiExpression[] occurrences,
                                                  final IntroduceVariableSettings settings) {
    final PsiElement container = anchorStatement.getParent();
    PsiElement child = anchorStatement;
    final boolean isInsideLoop = RefactoringUtil.isLoopOrIf(container);
    if (!isInsideLoop) {
      child = locateAnchor(child);
      if (isFinalVariableOnLHS(expr)) {
        child = child.getNextSibling();
      }
    }
    final PsiElement anchor = child == null ? anchorStatement : child;

    boolean tempDeleteSelf = false;
    final boolean replaceSelf = settings.isReplaceLValues() || !RefactoringUtil.isAssignmentLHS(expr);
    final PsiElement exprParent = expr.getParent();
    if (!isInsideLoop) {
      if (exprParent instanceof PsiExpressionStatement && anchor.equals(anchorStatement)) {
        PsiElement parent = exprParent.getParent();
        if (parent instanceof PsiCodeBlock ||
            //fabrique
            parent instanceof PsiCodeFragment) {
          tempDeleteSelf = true;
        }
      }
      tempDeleteSelf &= replaceSelf;
    }
    final boolean deleteSelf = tempDeleteSelf;
    final boolean replaceLoop = isInsideLoop ? exprParent instanceof PsiExpressionStatement 
                                             : container instanceof PsiLambdaExpression && exprParent == container;


    final int col = editor != null ? editor.getCaretModel().getLogicalPosition().column : 0;
    final int line = editor != null ? editor.getCaretModel().getLogicalPosition().line : 0;
    if (deleteSelf) {
      if (editor != null) {
        LogicalPosition pos = new LogicalPosition(line, col);
        editor.getCaretModel().moveToLogicalPosition(pos);
      }
    }

    final PsiCodeBlock newDeclarationScope = PsiTreeUtil.getParentOfType(container, PsiCodeBlock.class, false);
    final FieldConflictsResolver fieldConflictsResolver = new FieldConflictsResolver(settings.getEnteredName(), newDeclarationScope);
    return new Computable<PsiVariable>() {
      @Override
      public PsiVariable compute() {
        try {
          PsiStatement statement = null;
          if (!isInsideLoop && deleteSelf) {
            statement = (PsiStatement)exprParent;
          }

          final PsiExpression expr1 = fieldConflictsResolver.fixInitializer(expr);
          PsiExpression initializer = RefactoringUtil.unparenthesizeExpression(expr1);
          final SmartTypePointer selectedType = SmartTypePointerManager.getInstance(project).createSmartTypePointer(
            settings.getSelectedType());
          initializer = simplifyVariableInitializer(initializer, selectedType.getType());

          PsiDeclarationStatement declaration = JavaPsiFacade.getInstance(project).getElementFactory()
            .createVariableDeclarationStatement(settings.getEnteredName(), selectedType.getType(), initializer, container);
          if (!isInsideLoop) {
            declaration = addDeclaration(declaration, initializer);
            LOG.assertTrue(expr1.isValid());
            if (deleteSelf) {
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

          PsiExpression ref = JavaPsiFacade.getInstance(project).getElementFactory().createExpressionFromText(settings.getEnteredName(), null);
          if (settings.isReplaceAllOccurrences()) {
            ArrayList<PsiElement> array = new ArrayList<>();
            for (PsiExpression occurrence : occurrences) {
              if (deleteSelf && occurrence.equals(expr)) continue;
              if (occurrence.equals(expr)) {
                occurrence = expr1;
              }
              if (occurrence != null) {
                occurrence = RefactoringUtil.outermostParenthesizedExpression(occurrence);
              }
              if (settings.isReplaceLValues() || !RefactoringUtil.isAssignmentLHS(occurrence)) {
                array.add(replace(occurrence, ref, project));
              }
            }

            if (!deleteSelf && replaceSelf && expr1 instanceof PsiPolyadicExpression && expr1.isValid() && !expr1.isPhysical() ) {
              array.add(replace(expr1, ref, project));
            }

            if (editor != null) {
              final PsiElement[] replacedOccurences = PsiUtilCore.toPsiElementArray(array);
              highlightReplacedOccurences(project, editor, replacedOccurences);
            }
          } else {
            if (!deleteSelf && replaceSelf) {
              replace(expr1, ref, project);
            }
          }

          declaration = (PsiDeclarationStatement) RefactoringUtil.putStatementInLoopBody(declaration, container, anchorStatement, replaceSelf && replaceLoop);
          declaration = (PsiDeclarationStatement)JavaCodeStyleManager.getInstance(project).shortenClassReferences(declaration);
          PsiVariable var = (PsiVariable) declaration.getDeclaredElements()[0];
          PsiUtil.setModifierProperty(var, PsiModifier.FINAL, settings.isDeclareFinal());
          fieldConflictsResolver.fix();
          return var;
        } catch (IncorrectOperationException e) {
          LOG.error(e);
        }
        return null;
      }

      private PsiDeclarationStatement addDeclaration(PsiDeclarationStatement declaration, PsiExpression initializer) {
        if (anchor instanceof PsiDeclarationStatement) {
          final PsiElement[] declaredElements = ((PsiDeclarationStatement)anchor).getDeclaredElements();
          if (declaredElements.length > 1) {
            final int [] usedFirstVar = new int[] {-1};
            initializer.accept(new JavaRecursiveElementWalkingVisitor() {
              @Override
              public void visitReferenceExpression(PsiReferenceExpression expression) {
                final int i = ArrayUtilRt.find(declaredElements, expression.resolve());
                if (i > -1) {
                  usedFirstVar[0] = Math.max(i, usedFirstVar[0]);
                }
                super.visitReferenceExpression(expression);
              }
            });
            if (usedFirstVar[0] > -1) {
              final PsiVariable psiVariable = (PsiVariable)declaredElements[usedFirstVar[0]];
              psiVariable.normalizeDeclaration();
              final PsiDeclarationStatement parDeclarationStatement = PsiTreeUtil.getParentOfType(psiVariable, PsiDeclarationStatement.class);
              return (PsiDeclarationStatement)container.addAfter(declaration, parDeclarationStatement);
            }
          }
        }
        return  (PsiDeclarationStatement) container.addBefore(declaration, anchor);
      }
    };
  }

  private static boolean isFinalVariableOnLHS(PsiExpression expr) {
    if (expr instanceof PsiReferenceExpression && RefactoringUtil.isAssignmentLHS(expr)) {
      final PsiElement resolve = ((PsiReferenceExpression)expr).resolve();
      if (resolve instanceof PsiVariable &&
          ((PsiVariable)resolve).hasModifierProperty(PsiModifier.FINAL)) { //should be inserted after assignment
        return true;
      }
    }
    return false;
  }

  public static PsiExpression simplifyVariableInitializer(final PsiExpression initializer,
                                                          final PsiType expectedType) {

    if (initializer instanceof PsiTypeCastExpression) {
      PsiExpression operand = ((PsiTypeCastExpression)initializer).getOperand();
      if (operand != null) {
        PsiType operandType = operand.getType();
        if (operandType != null && TypeConversionUtil.isAssignable(expectedType, operandType)) {
          return operand;
        }
      }
    }
    else if (initializer instanceof PsiNewExpression) {
      final PsiNewExpression newExpression = (PsiNewExpression)initializer;
      if (newExpression.getArrayInitializer() != null) {
        return newExpression.getArrayInitializer();
      }
      else {
        final PsiExpression tryToDetectDiamondNewExpr = ((PsiVariable)JavaPsiFacade.getElementFactory(initializer.getProject())
          .createVariableDeclarationStatement("x", expectedType, initializer, initializer).getDeclaredElements()[0])
          .getInitializer();
        if (tryToDetectDiamondNewExpr instanceof PsiNewExpression &&
            PsiDiamondTypeUtil.canCollapseToDiamond((PsiNewExpression)tryToDetectDiamondNewExpr,
                                                    (PsiNewExpression)tryToDetectDiamondNewExpr,
                                                    expectedType)) {
          final PsiElement paramList = PsiDiamondTypeUtil
            .replaceExplicitWithDiamond(newExpression.getClassOrAnonymousClassReference().getParameterList());
          return PsiTreeUtil.getParentOfType(paramList, PsiNewExpression.class);
        }
      }
    }
    return initializer;
  }

  public static PsiElement replace(final PsiExpression expr1, final PsiExpression ref, final Project project)
    throws IncorrectOperationException {
    final PsiExpression expr2;
    if (expr1 instanceof PsiArrayInitializerExpression &&
      expr1.getParent() instanceof PsiNewExpression) {
      expr2 = (PsiNewExpression) expr1.getParent();
    } else {
      expr2 = RefactoringUtil.outermostParenthesizedExpression(expr1);
    }
    if (expr2.isPhysical() || expr1.getUserData(ElementToWorkOn.REPLACE_NON_PHYSICAL) != null) {
      return expr2.replace(ref);
    }
    else {
      final String prefix  = expr1.getUserData(ElementToWorkOn.PREFIX);
      final String suffix  = expr1.getUserData(ElementToWorkOn.SUFFIX);
      final PsiElement parent = expr1.getUserData(ElementToWorkOn.PARENT);
      final RangeMarker rangeMarker = expr1.getUserData(ElementToWorkOn.TEXT_RANGE);

      LOG.assertTrue(parent != null, expr1);
      return parent.replace(createReplacement(ref.getText(), project, prefix, suffix, parent, rangeMarker, new int[1]));
    }
  }

  private static PsiElement createReplacement(final String refText, final Project project,
                                                 final String prefix,
                                                 final String suffix,
                                                 final PsiElement parent, final RangeMarker rangeMarker, int[] refIdx) {
    String text = refText;
    if (parent != null) {
      final String allText = parent.getContainingFile().getText();
      final TextRange parentRange = parent.getTextRange();

      LOG.assertTrue(parentRange.getStartOffset() <= rangeMarker.getStartOffset(), parent + "; prefix:" + prefix + "; suffix:" + suffix);
      String beg = allText.substring(parentRange.getStartOffset(), rangeMarker.getStartOffset());
      if (StringUtil.stripQuotesAroundValue(beg).trim().length() == 0 && prefix == null) beg = "";

      LOG.assertTrue(rangeMarker.getEndOffset() <= parentRange.getEndOffset(), parent + "; prefix:" + prefix + "; suffix:" + suffix);
      String end = allText.substring(rangeMarker.getEndOffset(), parentRange.getEndOffset());
      if (StringUtil.stripQuotesAroundValue(end).trim().length() == 0 && suffix == null) end = "";

      final String start = beg + (prefix != null ? prefix : "");
      refIdx[0] = start.length();
      text = start + refText + (suffix != null ? suffix : "") + end;
    }
    final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    return parent instanceof PsiStatement ? factory.createStatementFromText(text, parent) :
                                            parent instanceof PsiCodeBlock ? factory.createCodeBlockFromText(text, parent) 
                                                                           : factory.createExpressionFromText(text, parent);
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

  protected static void highlightReplacedOccurences(Project project, Editor editor, PsiElement[] replacedOccurences){
    if (editor == null) return;
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    HighlightManager highlightManager = HighlightManager.getInstance(project);
    EditorColorsManager colorsManager = EditorColorsManager.getInstance();
    TextAttributes attributes = colorsManager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    highlightManager.addOccurrenceHighlights(editor, replacedOccurences, attributes, true, null);
    WindowManager.getInstance().getStatusBar(project).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
  }

  protected abstract void showErrorMessage(Project project, Editor editor, String message);


  protected boolean reportConflicts(MultiMap<PsiElement,String> conflicts, Project project, IntroduceVariableSettings settings){
    return false;
  }

  public IntroduceVariableSettings getSettings(Project project, Editor editor,
                                               PsiExpression expr, PsiExpression[] occurrences,
                                               final TypeSelectorManagerImpl typeSelectorManager,
                                               boolean declareFinalIfAll,
                                               boolean anyAssignmentLHS,
                                               final InputValidator validator,
                                               PsiElement anchor,
                                               final OccurrencesChooser.ReplaceChoice replaceChoice) {
    final boolean replaceAll =
      replaceChoice == OccurrencesChooser.ReplaceChoice.ALL || replaceChoice == OccurrencesChooser.ReplaceChoice.NO_WRITE;
    final SuggestedNameInfo suggestedName = getSuggestedName(typeSelectorManager.getDefaultType(), expr, anchor);
    final String variableName = suggestedName.names.length > 0 ? suggestedName.names[0] : "";
    final boolean declareFinal = replaceAll && declareFinalIfAll || !anyAssignmentLHS && createFinals(project);
    final boolean replaceWrite = anyAssignmentLHS && replaceChoice == OccurrencesChooser.ReplaceChoice.ALL;
    return new IntroduceVariableSettings() {
      @Override
      public String getEnteredName() {
        return variableName;
      }

      @Override
      public boolean isReplaceAllOccurrences() {
        return replaceAll;
      }

      @Override
      public boolean isDeclareFinal() {
        return declareFinal;
      }

      @Override
      public boolean isReplaceLValues() {
        return replaceWrite;
      }

      @Override
      public PsiType getSelectedType() {
        final PsiType selectedType = typeSelectorManager.getTypeSelector().getSelectedType();
        return selectedType != null ? selectedType : typeSelectorManager.getDefaultType();
      }

      @Override
      public boolean isOK() {
        return true;
      }
    };
  }

  public static boolean createFinals(Project project) {
    final Boolean createFinals = JavaRefactoringSettings.getInstance().INTRODUCE_LOCAL_CREATE_FINALS;
    return createFinals == null ? CodeStyleSettingsManager.getSettings(project).GENERATE_FINAL_LOCALS : createFinals.booleanValue();
  }

  public static boolean checkAnchorBeforeThisOrSuper(final Project project,
                                                     final Editor editor,
                                                     final PsiElement tempAnchorElement,
                                                     final String refactoringName,
                                                     final String helpID) {
    if (tempAnchorElement instanceof PsiExpressionStatement) {
      PsiExpression enclosingExpr = ((PsiExpressionStatement)tempAnchorElement).getExpression();
      if (enclosingExpr instanceof PsiMethodCallExpression) {
        PsiMethod method = ((PsiMethodCallExpression)enclosingExpr).resolveMethod();
        if (method != null && method.isConstructor()) {
          //This is either 'this' or 'super', both must be the first in the respective contructor
          String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("invalid.expression.context"));
          CommonRefactoringUtil.showErrorHint(project, editor, message, refactoringName, helpID);
          return true;
        }
      }
    }
    return false;
  }

  public interface Validator {
    boolean isOK(IntroduceVariableSettings dialog);
  }

  public static void checkInLoopCondition(PsiExpression occurence, MultiMap<PsiElement, String> conflicts) {
    final PsiElement loopForLoopCondition = RefactoringUtil.getLoopForLoopCondition(occurence);
    if (loopForLoopCondition == null) return;
    final List<PsiVariable> referencedVariables = RefactoringUtil.collectReferencedVariables(occurence);
    final List<PsiVariable> modifiedInBody = new ArrayList<>();
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

  @Override
  public AbstractInplaceIntroducer getInplaceIntroducer() {
    return myInplaceIntroducer;
  }
}
