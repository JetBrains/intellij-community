// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.introduceVariable;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.unwrap.ScopeHighlighter;
import com.intellij.codeInspection.RemoveRedundantTypeArgumentsUtil;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.featureStatistics.ProductivityFeatureNames;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.LanguageRefactoringSupport;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pass;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
import com.intellij.psi.impl.source.jsp.jspJava.JspCodeBlock;
import com.intellij.psi.impl.source.jsp.jspJava.JspHolderMethod;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.impl.source.tree.java.ReplaceExpressionUtil;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.*;
import com.intellij.refactoring.*;
import com.intellij.refactoring.chainCall.ChainCallExtractor;
import com.intellij.refactoring.introduce.inplace.AbstractInplaceIntroducer;
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser;
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser.ReplaceChoice;
import com.intellij.refactoring.introduceField.ElementToWorkOn;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.listeners.RefactoringEventListener;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.occurrences.ExpressionOccurrenceManager;
import com.intellij.refactoring.util.occurrences.NotInSuperCallOccurrenceFilter;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import com.siyeh.ipp.psiutils.ErrorUtil;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author dsl
 */
public abstract class IntroduceVariableBase extends IntroduceHandlerBase {
  public static class JavaReplaceChoice implements OccurrencesChooser.BaseReplaceChoice {
    public static final JavaReplaceChoice NO = new JavaReplaceChoice(ReplaceChoice.NO, null, false);
    public static final JavaReplaceChoice NO_WRITE = new JavaReplaceChoice(ReplaceChoice.NO_WRITE, null, false);
    public static final JavaReplaceChoice ALL = new JavaReplaceChoice(ReplaceChoice.ALL, null, false);

    private final String myDescription;
    private final boolean myChain;
    private final ReplaceChoice myChoice;

    JavaReplaceChoice(@NotNull ReplaceChoice choice, @Nullable @Nls String description, boolean chain) {
      myChoice = choice;
      myDescription = description;
      myChain = chain;
    }

    @Override
    public boolean isAll() {
      return myChoice.isAll();
    }

    public boolean isChain() {
      return myChain;
    }
    
    public PsiExpression[] filter(ExpressionOccurrenceManager manager) {
      switch (myChoice) {
        case NO:
          return new PsiExpression[]{manager.getMainOccurence()};
        case NO_WRITE:
          return StreamEx.of(manager.getOccurrences()).filter(expr -> !PsiUtil.isAccessedForWriting(expr)).toArray(PsiExpression.EMPTY_ARRAY);
        case ALL:
          return manager.getOccurrences();
        default:
          throw new IllegalStateException("Unexpected value: " + myChoice);
      }
    }

    @Override
    public String formatDescription(int occurrencesCount) {
      return myDescription == null ? myChoice.formatDescription(occurrencesCount) : myDescription;
    }

    @Override
    public String toString() {
      // For debug/test purposes
      return formatDescription(0);
    }

    @NotNull
    private static IntroduceVariableBase.JavaReplaceChoice allOccurrencesInside(PsiElement parent,
                                                                                int sameKeywordCount,
                                                                                String finalKeyword) {
      return new JavaReplaceChoice(ReplaceChoice.ALL, null, false) {
        @Override
        public PsiExpression[] filter(ExpressionOccurrenceManager manager) {
          return StreamEx.of(manager.getOccurrences()).filter(expr -> PsiTreeUtil.isAncestor(parent, expr, true))
            .toArray(PsiExpression.EMPTY_ARRAY);
        }

        @Override
        public String formatDescription(int occurrencesCount) {
          return JavaRefactoringBundle.message("replace.occurrences.inside.statement", occurrencesCount, finalKeyword, sameKeywordCount);
        }
      };
    }
  }

  private static final Logger LOG = Logger.getInstance(IntroduceVariableBase.class);
  @NonNls private static final String PREFER_STATEMENTS_OPTION = "introduce.variable.prefer.statements";
  @NonNls private static final String REFACTORING_ID = "refactoring.extractVariable";

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

  @Override
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
        final List<PsiExpression> expressions = ContainerUtil
          .filter(collectExpressions(file, editor, offset), expression ->
            RefactoringUtil.getParentStatement(expression, false) != null ||
            PsiTreeUtil.getParentOfType(expression, PsiField.class, true, PsiStatement.class) != null);
        if (expressions.isEmpty()) {
          selectionModel.selectLineAtCaret();
        } else if (!isChooserNeeded(expressions)) {
          final TextRange textRange = expressions.get(0).getTextRange();
          selectionModel.setSelection(textRange.getStartOffset(), textRange.getEndOffset());
        }
        else {
          IntroduceTargetChooser.showChooser(editor, expressions,
            new Pass<PsiExpression>(){
              @Override
              public void pass(final PsiExpression selectedValue) {
                invoke(project, editor, file, selectedValue.getTextRange().getStartOffset(), selectedValue.getTextRange().getEndOffset());
              }
            },
            new PsiExpressionTrimRenderer.RenderFunction(), RefactoringBundle.message("introduce.target.chooser.expressions.title"), preferredSelection(statementsInRange, expressions), ScopeHighlighter.NATURAL_RANGER);
          return;
        }
      }
    }
    if (invoke(project, editor, file, selectionModel.getSelectionStart(), selectionModel.getSelectionEnd()) &&
        LookupManager.getActiveLookup(editor) == null) {
      selectionModel.removeSelection();
    }
  }

  public static boolean isChooserNeeded(List<? extends PsiExpression> expressions) {
    if (expressions.size() == 1) {
      final PsiExpression expression = expressions.get(0);
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

  public static int preferredSelection(PsiElement[] statementsInRange, List<? extends PsiExpression> expressions) {
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
      if (correctedOffset < 0 || text.charAt(correctedOffset) != ')' && text.charAt(correctedOffset) != '.') {
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
        if (isExtractable(expression)) {
          expressions.add(expression);
        }
      }
      expression = PsiTreeUtil.getParentOfType(expression, PsiExpression.class);
    }
    return expressions;
  }

  public static boolean isExtractable(PsiExpression expression) {
    if (expression instanceof PsiMethodReferenceExpression) {
      return true;
    }
    else if (!(expression instanceof PsiAssignmentExpression)) {
      if (!(expression instanceof PsiReferenceExpression)) {
        return true;
      }
      else {
        if (!(expression.getParent() instanceof PsiMethodCallExpression)) {
          final PsiElement resolve = ((PsiReferenceExpression)expression).resolve();
          if (!(resolve instanceof PsiClass) && !(resolve instanceof PsiPackage)) {
            return true;
          }
        }
      }
    }
    return false;
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
        }
        else if (statements[0] instanceof PsiReturnStatement) {
          tempExpr = ((PsiReturnStatement)statements[0]).getReturnValue();
        }
        else if (statements[0] instanceof PsiSwitchStatement) {
          PsiExpression expr = JavaPsiFacade.getElementFactory(project).createExpressionFromText(statements[0].getText(), statements[0]);
          TextRange range = statements[0].getTextRange();
          final RangeMarker rangeMarker = FileDocumentManager.getInstance().getDocument(file.getVirtualFile()).createRangeMarker(range);
          expr.putUserData(ElementToWorkOn.TEXT_RANGE, rangeMarker);
          expr.putUserData(ElementToWorkOn.PARENT, statements[0]);
          return expr;
        }
      }
    }

    if (tempExpr == null) {
      tempExpr = getSelectedExpression(project, file, startOffset, endOffset);
    }
    return isExtractable(tempExpr) ? tempExpr : null;
  }

  /**
   * @return can return NotNull value although extraction will fail: reason could be retrieved from {@link #getErrorMessage(PsiExpression)}
   */
  public static PsiExpression getSelectedExpression(final Project project, PsiFile file, int startOffset, int endOffset) {
    final InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(project);
    PsiElement elementAtStart = file.findElementAt(startOffset);
    if (elementAtStart == null || elementAtStart instanceof PsiWhiteSpace || elementAtStart instanceof PsiComment) {
      final PsiElement element = PsiTreeUtil.skipWhitespacesAndCommentsForward(elementAtStart);
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
      elementAtEnd = PsiTreeUtil.skipWhitespacesAndCommentsBackward(elementAtEnd);
      if (elementAtEnd == null) return null;
      endOffset = elementAtEnd.getTextRange().getEndOffset();
    }

    if (endOffset <= startOffset) return null;

    PsiElement elementAt = PsiTreeUtil.findCommonParent(elementAtStart, elementAtEnd);
    if (elementAt instanceof PsiExpressionStatement) {
      return ((PsiExpressionStatement)elementAt).getExpression();
    }
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

    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
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

      if (ErrorUtil.containsDeepError(tempExpr)) return null;

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
      if (ErrorUtil.containsDeepError(toBeExpression)) return null;
      if (literalExpression != null && toBeExpression instanceof PsiExpression) {
        PsiType type = ((PsiExpression)toBeExpression).getType();
        if (type != null && !type.equals(literalExpression.getType())) {
          return null;
        }
      }
      else if (containingExpression != null) {
        PsiType containingExpressionType = containingExpression.getType();
        PsiType tempExprType = tempExpr.getType();
        if (containingExpressionType != null && 
            (tempExprType == null || !TypeConversionUtil.isAssignable(containingExpressionType, tempExprType))) {
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
    if (text.contains(",")) {
      PsiExpressionList argumentList = parent.getArgumentList();
      assert argumentList != null; // checked at call site
      final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(parent.getProject());
      final JavaResolveResult resolveResult = parent.resolveMethodGenerics();
      final PsiMethod psiMethod = (PsiMethod)resolveResult.getElement();
      if (psiMethod == null || !psiMethod.isVarArgs()) return null;
      final PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
      final PsiParameter varargParameter = parameters[parameters.length - 1];
      final PsiType type = varargParameter.getType();
      LOG.assertTrue(type instanceof PsiEllipsisType);
      final PsiArrayType psiType = (PsiArrayType)((PsiEllipsisType)type).toArrayType();
      final PsiExpression[] args = argumentList.getExpressions();
      final PsiSubstitutor psiSubstitutor = resolveResult.getSubstitutor();

      if (args.length < parameters.length || startOffset < args[parameters.length - 1].getTextRange().getStartOffset()) return null;

      final PsiFile containingFile = parent.getContainingFile();

      PsiElement startElement = containingFile.findElementAt(startOffset);
      while (startElement != null && startElement.getParent() != argumentList) {
        startElement = startElement.getParent();
      }
      if (!(startElement instanceof PsiExpression) || startOffset > startElement.getTextOffset()) return null;

      PsiElement endElement = containingFile.findElementAt(endOffset - 1);
      while (endElement != null && endElement.getParent() != argumentList) {
        endElement = endElement.getParent();
      }
      if (!(endElement instanceof PsiExpression) || endOffset < endElement.getTextRange().getEndOffset()) return null;

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

  @Override
  protected boolean invokeImpl(final Project project, final PsiExpression expr, final Editor editor) {
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
        String message = RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("selected.block.should.represent.an.expression"));
        showErrorMessage(project, editor, message);
        return false;
      }
    }

    String enumInSwitchError = RefactoringUtil.checkEnumConstantInSwitchLabel(expr);
    if (enumInSwitchError != null) {
      showErrorMessage(project, editor, enumInSwitchError);
      return false;
    }


    final PsiType originalType = RefactoringUtil.getTypeByExpressionWithExpectedType(expr);
    if (originalType == null || LambdaUtil.notInferredType(originalType)) {
      String message = RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("unknown.expression.type"));
      showErrorMessage(project, editor, message);
      return false;
    }

    if (PsiType.VOID.equals(originalType)) {
      String message = RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("selected.expression.has.void.type"));
      showErrorMessage(project, editor, message);
      return false;
    }

    for (PsiPatternVariable variable : JavaPsiPatternUtil.getExposedPatternVariables(expr)) {
      if (VariableAccessUtils.getVariableReferences(variable, variable.getDeclarationScope()).stream()
        .anyMatch(ref -> !PsiTreeUtil.isAncestor(expr, ref, true))) {
        String message = RefactoringBundle.getCannotRefactorMessage(
          JavaRefactoringBundle.message("selected.expression.introduces.pattern.variable", variable.getName()));
        showErrorMessage(project, editor, message);
        return false;
      }
    }

    final PsiElement anchorStatement = getAnchor(expr);

    PsiElement tempContainer = checkAnchorStatement(project, editor, anchorStatement);
    if (tempContainer == null) return false;

    final PsiFile file = anchorStatement.getContainingFile();
    LOG.assertTrue(file != null, "expr.getContainingFile() == null");
    final PsiElement nameSuggestionContext = editor == null ? null : file.findElementAt(editor.getCaretModel().getOffset());
    final RefactoringSupportProvider supportProvider = LanguageRefactoringSupport.INSTANCE.forContext(expr);
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

    OccurrencesInfo occurrencesInfo = new OccurrencesInfo(occurrences);

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, file)) return false;

    final LinkedHashMap<JavaReplaceChoice, List<PsiExpression>> occurrencesMap = occurrencesInfo.buildOccurrencesMap(expr);

    class IntroduceVariablePass extends Pass<JavaReplaceChoice> {
      boolean wasSucceed = true;
      
      @Override
      public void pass(final JavaReplaceChoice choice) {
        if (choice == null || !tryIntroduceInplace(project, editor, choice, occurrenceManager, originalType)) {
          CommandProcessor.getInstance().executeCommand(project, () -> introduce(choice), getRefactoringName(), null);
        }
      }

      private void introduce(@Nullable JavaReplaceChoice choice) {
        if (!anchorStatement.isValid()) {
          return;
        }
        final Editor topLevelEditor;
        if (!InjectedLanguageManager.getInstance(project).isInjectedFragment(anchorStatement.getContainingFile())) {
          topLevelEditor = InjectedLanguageUtil.getTopLevelEditor(editor);
        }
        else {
          topLevelEditor = editor;
        }

        PsiVariable variable = null;
        try {
          boolean hasWriteAccess = occurrencesInfo.myHasWriteAccess;
          final InputValidator validator = new InputValidator(IntroduceVariableBase.this, project, occurrenceManager);

          final TypeSelectorManagerImpl typeSelectorManager = new TypeSelectorManagerImpl(project, originalType, expr, occurrences);
          boolean inFinalContext = occurrenceManager.isInFinalContext();
          final IntroduceVariableSettings settings =
            getSettings(project, topLevelEditor, expr, occurrences, typeSelectorManager, inFinalContext, hasWriteAccess, validator,
                        anchorStatement, choice);
          if (!settings.isOK()) {
            wasSucceed = false;
            return;
          }
          JavaReplaceChoice finalChoice = settings.getReplaceChoice();
          PsiExpression[] selectedOccurrences = finalChoice.filter(occurrenceManager);
          final PsiElement chosenAnchor = getAnchor(selectedOccurrences);
          if (chosenAnchor == null) {
            String text = file.getText();
            String textWithOccurrences = StreamEx.of(selectedOccurrences)
              .map(e -> getPhysicalElement(e).getTextRange())
              .flatMapToEntry(range -> EntryStream.of(range.getStartOffset(), "[", range.getEndOffset(), "]").toMap())
              .sortedBy(Map.Entry::getKey)
              .prepend(0, "")
              .append(text.length(), "")
              .map(Function.identity())
              .pairMap((prev, next) -> text.substring(prev.getKey(), next.getKey()) + next.getValue())
              .joining();
            LOG.error("Unable to find anchor for a new variable; selectedOccurrences.length = "+selectedOccurrences.length,
                      new Attachment("source.java", textWithOccurrences));
            return;
          }

          final RefactoringEventData beforeData = new RefactoringEventData();
          beforeData.addElement(expr);
          project.getMessageBus()
            .syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC).refactoringStarted(REFACTORING_ID, beforeData);

          variable = VariableExtractor.introduce(project, expr, topLevelEditor, chosenAnchor, selectedOccurrences, settings);
        }
        finally {
          final RefactoringEventData afterData = new RefactoringEventData();
          afterData.addElement(variable);
          project.getMessageBus()
            .syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC).refactoringDone(REFACTORING_ID, afterData);
        }
      }
    }
    final IntroduceVariablePass callback = new IntroduceVariablePass();

    if (!isInplaceAvailableOnDataContext) {
      callback.pass(null);
    }
    else {
      String title = occurrencesInfo.myChainMethodName != null && occurrences.length == 1
                     ? JavaRefactoringBundle.message("replace.lambda.chain.detected")
                     : RefactoringBundle.message("replace.multiple.occurrences.found");
      OccurrencesChooser.<PsiExpression>simpleChooser(editor).showChooser(callback, occurrencesMap, title);
    }
    return callback.wasSucceed;
  }

  private boolean tryIntroduceInplace(@NotNull Project project,
                                      Editor editor,
                                      @NotNull JavaReplaceChoice choice,
                                      @NotNull ExpressionOccurrenceManager occurrenceManager,
                                      @NotNull PsiType originalType) {
    boolean inFinalContext = occurrenceManager.isInFinalContext();
    PsiExpression expr = occurrenceManager.getMainOccurence();
    PsiExpression[] selectedOccurrences = choice.filter(occurrenceManager);
    final InputValidator validator = new InputValidator(this, project, occurrenceManager);
    final TypeSelectorManagerImpl typeSelectorManager = new TypeSelectorManagerImpl(project, originalType, expr, selectedOccurrences);
    typeSelectorManager.setAllOccurrences(true);

    boolean hasWriteAccess = ContainerUtil.exists(selectedOccurrences, occ -> PsiUtil.isAccessedForWriting(occ));
    final PsiElement chosenAnchor = getAnchor(selectedOccurrences);
    final IntroduceVariableSettings settings =
      getSettings(project, editor, expr, selectedOccurrences, typeSelectorManager, inFinalContext, 
                  hasWriteAccess, validator, chosenAnchor, choice);

    if (choice.isChain()) {
      myInplaceIntroducer = new ChainCallInplaceIntroducer(project,
                                                           settings,
                                                           chosenAnchor,
                                                           editor, expr,
                                                           selectedOccurrences,
                                                           typeSelectorManager,
                                                           getRefactoringName());
    }
    else {
      final boolean cantChangeFinalModifier = hasWriteAccess || inFinalContext;
      myInplaceIntroducer = new JavaVariableInplaceIntroducer(project,
                                                              settings,
                                                              chosenAnchor,
                                                              editor, expr, cantChangeFinalModifier,
                                                              selectedOccurrences,
                                                              typeSelectorManager,
                                                              getRefactoringName());
    }
    return myInplaceIntroducer.startInplaceIntroduceTemplate();
  }

  public static boolean canBeExtractedWithoutExplicitType(PsiExpression expr) {
    if (PsiUtil.isLanguageLevel10OrHigher(expr)) {
      PsiType type = expr.getType();
      return type != null &&
             !PsiType.NULL.equals(type) &&
             PsiTypesUtil.isDenotableType(type, expr) &&
             (expr instanceof PsiNewExpression || type.equals(((PsiExpression)expr.copy()).getType()));
    }
    return false;
  }

  @Nullable
  private static PsiElement getAnchor(PsiElement place) {
    place = getPhysicalElement(place);
    PsiElement anchorStatement = RefactoringUtil.getParentStatement(place, false);
    if (anchorStatement == null) {
      PsiField field = PsiTreeUtil.getParentOfType(place, PsiField.class, true, PsiStatement.class);
      if (field != null && !(field instanceof PsiEnumConstant)) {
        anchorStatement = field.getInitializer();
      }
    }
    return anchorStatement;
  }

  static @Nullable PsiElement getAnchor(PsiExpression[] places) {
    if (places.length == 1) {
      return getAnchor(places[0]);
    }
    PsiElement anchor = RefactoringUtil.getAnchorElementForMultipleExpressions(places, null);
    return anchor instanceof PsiField && !(anchor instanceof PsiEnumConstant) ? ((PsiField)anchor).getInitializer() : anchor;
  }

  private static @NotNull PsiElement getPhysicalElement(PsiElement place) {
    PsiElement physicalElement = place.getUserData(ElementToWorkOn.PARENT);
    return physicalElement != null ? physicalElement : place;
  }

  @Contract("_, _, null -> null")
  protected PsiElement checkAnchorStatement(Project project, Editor editor, PsiElement anchorStatement) {
    if (anchorStatement == null) {
      String message = JavaRefactoringBundle.message("refactoring.is.not.supported.in.the.current.context", getRefactoringName());
      showErrorMessage(project, editor, message);
      return null;
    }
    if (checkAnchorBeforeThisOrSuper(project, editor, anchorStatement, getRefactoringName(), HelpID.INTRODUCE_VARIABLE)) return null;

    final PsiElement tempContainer = anchorStatement.getParent();

    if (!(tempContainer instanceof PsiCodeBlock) && !RefactoringUtil.isLoopOrIf(tempContainer) && !(tempContainer instanceof PsiLambdaExpression) && (tempContainer.getParent() instanceof PsiLambdaExpression)) {
      String message = JavaRefactoringBundle.message("refactoring.is.not.supported.in.the.current.context", getRefactoringName());
      showErrorMessage(project, editor, message);
      return null;
    }
    return tempContainer;
  }

  protected boolean isInplaceAvailableInTestMode() {
    return false;
  }

  private static ExpressionOccurrenceManager createOccurrenceManager(PsiExpression expr, PsiElement tempContainer) {
    Set<PsiVariable> vars = new HashSet<>();
    SyntaxTraverser.psiTraverser().withRoot(expr)
      .filter(element -> element instanceof PsiReferenceExpression)
      .forEach(element -> {
        final PsiElement resolve = ((PsiReferenceExpression)element).resolve();
        if (resolve instanceof PsiVariable) {
          vars.add((PsiVariable)resolve);
        }
      });

    PsiElement containerParent = tempContainer;
    PsiElement lastScope = tempContainer;
    while (true) {
      if (containerParent instanceof PsiFile) break;
      if (containerParent instanceof PsiMethod) {
        PsiClass containingClass = ((PsiMethod)containerParent).getContainingClass();
        if (containingClass == null || !PsiUtil.isLocalOrAnonymousClass(containingClass)) break;
        if (vars.stream().anyMatch(variable -> PsiTreeUtil.isAncestor(containingClass, variable, true))) {
          break;
        }
      }
      if (containerParent instanceof PsiLambdaExpression) {
        PsiParameter[] parameters = ((PsiLambdaExpression)containerParent).getParameterList().getParameters();
        if (Arrays.stream(parameters).anyMatch(vars::contains)) {
          break;
        }
      }
      if (containerParent instanceof PsiForStatement) {
        PsiForStatement forStatement = (PsiForStatement)containerParent;
        if (vars.stream().anyMatch(variable -> PsiTreeUtil.isAncestor(forStatement.getInitialization(), variable, true))) {
          break;
        }
      }
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

  static boolean isFinalVariableOnLHS(PsiExpression expr) {
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
    return simplifyVariableInitializer(initializer, expectedType, true);
  }

  public static PsiExpression simplifyVariableInitializer(final PsiExpression initializer,
                                                          final PsiType expectedType,
                                                          final boolean inDeclaration) {

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
        if (inDeclaration) {
          return newExpression.getArrayInitializer();
        }
      }
      else {
        PsiJavaCodeReferenceElement ref = newExpression.getClassOrAnonymousClassReference();
        if (ref != null) {
          final PsiExpression tryToDetectDiamondNewExpr = ((PsiVariable)JavaPsiFacade.getElementFactory(initializer.getProject())
            .createVariableDeclarationStatement("x", expectedType, initializer, initializer).getDeclaredElements()[0])
            .getInitializer();
          if (tryToDetectDiamondNewExpr instanceof PsiNewExpression &&
              PsiDiamondTypeUtil.canCollapseToDiamond((PsiNewExpression)tryToDetectDiamondNewExpr,
                                                      (PsiNewExpression)tryToDetectDiamondNewExpr,
                                                      expectedType)) {
            final PsiElement paramList = RemoveRedundantTypeArgumentsUtil.replaceExplicitWithDiamond(ref.getParameterList());
            return PsiTreeUtil.getParentOfType(paramList, PsiNewExpression.class);
          }
        }
      }
    }
    return initializer;
  }

  /**
   * Ensure that diamond inside initializer is expanded, then replace variable type with var
   */
  public static PsiElement expandDiamondsAndReplaceExplicitTypeWithVar(PsiTypeElement typeElement, PsiElement context) {
    PsiElement parent = typeElement.getParent();
    if (parent instanceof PsiVariable) {
      PsiExpression copyVariableInitializer = ((PsiVariable)parent).getInitializer();
      if (copyVariableInitializer instanceof PsiNewExpression) {
        final PsiDiamondType.DiamondInferenceResult diamondResolveResult =
          PsiDiamondTypeImpl.resolveInferredTypesNoCheck((PsiNewExpression)copyVariableInitializer, copyVariableInitializer);
        if (!diamondResolveResult.getInferredTypes().isEmpty()) {
          PsiDiamondTypeUtil.expandTopLevelDiamondsInside(copyVariableInitializer);
        }
      }
    }

    return new CommentTracker().replaceAndRestoreComments(typeElement, JavaPsiFacade.getElementFactory(context.getProject()).createTypeElementFromText("var", context));
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
      //noinspection SSBasedInspection (suggested replacement breaks behavior)
      if (StringUtil.stripQuotesAroundValue(beg).trim().isEmpty() && prefix == null) beg = "";

      LOG.assertTrue(rangeMarker.getEndOffset() <= parentRange.getEndOffset(), parent + "; prefix:" + prefix + "; suffix:" + suffix);
      String end = allText.substring(rangeMarker.getEndOffset(), parentRange.getEndOffset());
      //noinspection SSBasedInspection (suggested replacement breaks behavior)
      if (StringUtil.stripQuotesAroundValue(end).trim().isEmpty() && suffix == null) end = "";

      final String start = beg + (prefix != null ? prefix : "");
      refIdx[0] = start.length();
      text = start + refText + (suffix != null ? suffix : "") + end;
    }
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    return parent instanceof PsiStatement ? factory.createStatementFromText(text, parent) :
                                            parent instanceof PsiCodeBlock ? factory.createCodeBlockFromText(text, parent)
                                                                           : factory.createExpressionFromText(text, parent);
  }

  @Override
  protected boolean invokeImpl(Project project, PsiLocalVariable localVariable, Editor editor) {
    throw new UnsupportedOperationException();
  }

  protected static void highlightReplacedOccurrences(Project project, Editor editor, PsiElement[] replacedOccurrences){
    if (editor == null) return;
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    HighlightManager highlightManager = HighlightManager.getInstance(project);
    highlightManager.addOccurrenceHighlights(editor, replacedOccurrences, EditorColors.SEARCH_RESULT_ATTRIBUTES, true, null);
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
                                               final JavaReplaceChoice replaceChoice) {
    final boolean replaceAll = replaceChoice.isAll();
    final SuggestedNameInfo suggestedName = getSuggestedName(typeSelectorManager.getDefaultType(), expr, anchor);
    final String variableName = suggestedName.names.length > 0 ? suggestedName.names[0] : "v";
    final boolean declareFinal = replaceAll && declareFinalIfAll || !anyAssignmentLHS && createFinals(anchor.getContainingFile()) ||
                                 anchor instanceof PsiSwitchLabelStatementBase;
    final boolean declareVarType = canBeExtractedWithoutExplicitType(expr) && createVarType() && !replaceChoice.isChain();
    final boolean replaceWrite = anyAssignmentLHS && replaceAll;
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
      public boolean isDeclareVarType() {
        return declareVarType;
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
      public JavaReplaceChoice getReplaceChoice() {
        return replaceChoice;
      }

      @Override
      public boolean isOK() {
        return true;
      }
    };
  }

  public static boolean createFinals(@NotNull PsiFile file) {
    final Boolean createFinals = JavaRefactoringSettings.getInstance().INTRODUCE_LOCAL_CREATE_FINALS;
    return createFinals == null ?
           JavaCodeStyleSettings.getInstance(file).GENERATE_FINAL_LOCALS :
           createFinals.booleanValue();
  }

  public static boolean createVarType() {
    final Boolean createVarType = JavaRefactoringSettings.getInstance().INTRODUCE_LOCAL_CREATE_VAR_TYPE;
    return createVarType != null && createVarType.booleanValue();
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
          //This is either 'this' or 'super', both must be the first in the respective constructor
          String message = RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("invalid.expression.context"));
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
    if (loopForLoopCondition == null || loopForLoopCondition instanceof PsiWhileStatement) return;
    final List<PsiVariable> referencedVariables = RefactoringUtil.collectReferencedVariables(occurence);
    final List<PsiVariable> modifiedInBody = new ArrayList<>();
    for (PsiVariable psiVariable : referencedVariables) {
      if (RefactoringUtil.isModifiedInScope(psiVariable, loopForLoopCondition)) {
        modifiedInBody.add(psiVariable);
      }
    }

    if (!modifiedInBody.isEmpty()) {
      for (PsiVariable variable : modifiedInBody) {
        final String message = JavaRefactoringBundle.message("is.modified.in.loop.body", RefactoringUIUtil.getDescription(variable, false));
        conflicts.putValue(variable, CommonRefactoringUtil.capitalize(message));
      }
      conflicts.putValue(occurence, JavaRefactoringBundle.message("introducing.variable.may.break.code.logic"));
    }
  }

  @Override
  public AbstractInplaceIntroducer getInplaceIntroducer() {
    return myInplaceIntroducer;
  }

  static class OccurrencesInfo {
    List<PsiExpression> myOccurrences;
    List<PsiExpression> myNonWrite;
    boolean myCantReplaceAll;
    boolean myCantReplaceAllButWrite;
    boolean myHasWriteAccess;
    final String myChainMethodName;

    OccurrencesInfo(PsiExpression[] occurrences) {
      myOccurrences = Arrays.asList(occurrences);
      myNonWrite = new ArrayList<>();
      myCantReplaceAll = false;
      myCantReplaceAllButWrite = false;
      for (PsiExpression occurrence : myOccurrences) {
        if (!RefactoringUtil.isAssignmentLHS(occurrence)) {
          myNonWrite.add(occurrence);
        } else if (isFinalVariableOnLHS(occurrence)) {
          myCantReplaceAll = true;
        } else if (!myNonWrite.isEmpty()){
          myCantReplaceAllButWrite = true;
          myCantReplaceAll = true;
        }
      }
      myHasWriteAccess = myOccurrences.size() > myNonWrite.size() && myOccurrences.size() > 1;
      myChainMethodName = getChainCallExtractor();
    }

    private String getChainCallExtractor() {
      if (myHasWriteAccess || myOccurrences.isEmpty()) return null;
      PsiExpression expression = myOccurrences.get(0);
      // The whole lambda body selected
      if (myOccurrences.size() == 1 && expression.getParent() instanceof PsiLambdaExpression) return null;
      PsiElement parent = PsiTreeUtil.findCommonParent(myOccurrences);
      if (parent == null) return null;
      PsiType type = expression.getType();
      PsiLambdaExpression lambda = PsiTreeUtil.getParentOfType(parent, PsiLambdaExpression.class, true, PsiStatement.class);
      ChainCallExtractor extractor = ChainCallExtractor.findExtractor(lambda, expression, type);
      if (extractor == null) return null;
      PsiParameter parameter = lambda.getParameterList().getParameters()[0];
      if (!ReferencesSearch.search(parameter).forEach((Processor<PsiReference>)ref ->
        myOccurrences.stream().anyMatch(expr -> PsiTreeUtil.isAncestor(expr, ref.getElement(), false)))) {
        return null;
      }
      return extractor.getMethodName(parameter, expression, type);
    }

    @NotNull
    LinkedHashMap<JavaReplaceChoice, List<PsiExpression>> buildOccurrencesMap(PsiExpression expr) {
      final LinkedHashMap<JavaReplaceChoice, List<PsiExpression>> occurrencesMap = new LinkedHashMap<>();
      if (myChainMethodName != null) {
        if (myOccurrences.size() > 1 && !myCantReplaceAll) {
          occurrencesMap.put(JavaReplaceChoice.NO, Collections.singletonList(expr));
          occurrencesMap.put(JavaReplaceChoice.ALL, myOccurrences);
          occurrencesMap.put(new JavaReplaceChoice(ReplaceChoice.ALL, null, true) {
              @Override
              public String formatDescription(int occurrencesCount) {
                return JavaRefactoringBundle.message("replace.all.and.extract", occurrencesCount, myChainMethodName);
              }
            }, myOccurrences);
        } else {
          JavaReplaceChoice noChain =
            new JavaReplaceChoice(ReplaceChoice.NO, JavaRefactoringBundle.message("replace.inside.current.lambda"), false);
          JavaReplaceChoice chain = 
            new JavaReplaceChoice(ReplaceChoice.NO, JavaRefactoringBundle.message("replace.as.separate.operation", myChainMethodName), true);
          occurrencesMap.put(noChain, Collections.singletonList(expr));
          occurrencesMap.put(chain, Collections.singletonList(expr));
        }
      } else {
        occurrencesMap.put(JavaReplaceChoice.NO, Collections.singletonList(expr));
        if (myHasWriteAccess && !myCantReplaceAllButWrite) {
          occurrencesMap.put(JavaReplaceChoice.NO_WRITE, myNonWrite);
        }

        if (myOccurrences.size() > 1 && !myCantReplaceAll) {
          if (occurrencesMap.containsKey(JavaReplaceChoice.NO_WRITE)) {
            JavaReplaceChoice choice = new JavaReplaceChoice(
              ReplaceChoice.ALL, JavaRefactoringBundle.message("replace.all.read.and.write"), false);
            occurrencesMap.put(choice, myOccurrences);
          }
          else {
            generateScopeBasedChoices(expr, occurrencesMap);
            occurrencesMap.put(JavaReplaceChoice.ALL, myOccurrences);
          }
        }
      }
      return occurrencesMap;
    }

    private void generateScopeBasedChoices(PsiExpression expr,
                                           LinkedHashMap<JavaReplaceChoice, List<PsiExpression>> occurrencesMap) {
      // This comparator can correctly compare only elements that represent a single ancestor chain
      // i.e. for two compared elements a and b either a is ancestor of b or vice versa
      Comparator<PsiElement> treeOrder = (e1, e2) -> {
        if (PsiTreeUtil.isAncestor(e1, e2, true)) return 1;
        if (PsiTreeUtil.isAncestor(e2, e1, true)) return -1;
        return 0;
      };
      PsiElement physical = getPhysicalElement(expr);
      TreeMap<PsiElement, List<PsiExpression>> groupByBlock =
        StreamEx.of(myOccurrences).groupingBy(e -> PsiTreeUtil.findCommonParent(e, physical),
                                              () -> new TreeMap<>(treeOrder), Collectors.toList());
      LOG.assertTrue(!groupByBlock.isEmpty());
      List<PsiExpression> currentOccurrences = new ArrayList<>();
      Map<String, Integer> counts = new HashMap<>();
      groupByBlock.forEach((parent, occurrences) -> {
        PsiElement nextParent = groupByBlock.higherKey(parent);
        if (nextParent == null) return;
        currentOccurrences.addAll(occurrences);
        if (currentOccurrences.size() == 1) return;
        PsiElement current = parent.getParent();
        String keyword = null;
        while (current != nextParent) {
          if (current instanceof PsiIfStatement || current instanceof PsiWhileStatement || current instanceof PsiForStatement ||
              current instanceof PsiTryStatement) {
            keyword = current.getFirstChild().getText();
          }
          else if (current instanceof PsiDoWhileStatement) {
            keyword = "do-while";
          }
          else if (current instanceof PsiForeachStatement) {
            keyword = "for-each";
          }
          else if (current instanceof PsiLambdaExpression) {
            keyword = "lambda";
          }
          if (keyword != null) {
            break;
          }
          current = current.getParent();
        }
        if (keyword == null && nextParent instanceof PsiIfStatement) {
          PsiStatement thenBranch = ((PsiIfStatement)nextParent).getThenBranch();
          PsiStatement elseBranch = ((PsiIfStatement)nextParent).getElseBranch();
          if (PsiTreeUtil.isAncestor(thenBranch, parent, false)) {
            keyword = "if-then";
          } else if (PsiTreeUtil.isAncestor(elseBranch, parent, false)) {
            keyword = "else";
          }
        }
        if (keyword != null) {
          int sameKeywordCount = counts.merge(keyword, 1, Integer::sum);
          if (sameKeywordCount <= 2) {
            JavaReplaceChoice choice = JavaReplaceChoice.allOccurrencesInside(parent, sameKeywordCount, keyword);
            occurrencesMap.put(choice, new ArrayList<>(currentOccurrences));
          }
        }
      });
    }
  }

  protected static String getRefactoringName() {
    return RefactoringBundle.message("introduce.variable.title");
  }
}
