// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.AddNewArrayExpressionFix;
import com.intellij.codeInsight.intention.impl.TypeExpression;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.Result;
import com.intellij.codeInsight.template.TextResult;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.java.JavaBundle;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
import com.intellij.psi.impl.source.tree.java.ReplaceExpressionUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.introduceField.ElementToWorkOn;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.CodeBlockSurrounder;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import com.siyeh.ipp.psiutils.ErrorUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public final class IntroduceVariableUtil {

  public static final Logger LOG = Logger.getInstance(IntroduceVariableUtil.class);
  public static final Key<Boolean> NEED_PARENTHESIS = Key.create("NEED_PARENTHESIS");
  private static final @NonNls String PREFER_STATEMENTS_OPTION = "introduce.variable.prefer.statements";

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
    return PropertiesComponent.getInstance().getBoolean(PREFER_STATEMENTS_OPTION) || Registry.is(PREFER_STATEMENTS_OPTION, false);
  }

  public static PsiElement[] findStatementsAtOffset(final Editor editor, final PsiFile file, final int offset) {
    final Document document = editor.getDocument();
    final int lineNumber = document.getLineNumber(offset);
    final int lineStart = document.getLineStartOffset(lineNumber);
    final int lineEnd = document.getLineEndOffset(lineNumber);

    return CodeInsightUtil.findStatementsInRange(file, lineStart, lineEnd);
  }

  /**
   * @return can return NotNull value although extraction will fail: reason could be retrieved from {@link #getErrorMessage(PsiExpression)}
   */
  public static PsiExpression getSelectedExpression(final Project project, PsiFile file, int startOffset, int endOffset) {
    final InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(project);
    PsiElement elementAtStart = file.findElementAt(startOffset);
    if (elementAtStart != null && isStringLiteral(elementAtStart) && elementAtStart.getTextRange().getEndOffset() - 1 == startOffset) {
      PsiLiteralExpression expressionAtStart = PsiTreeUtil.getParentOfType(elementAtStart, PsiLiteralExpression.class);
      PsiExpression nextExpression = PsiTreeUtil.getNextSiblingOfType(expressionAtStart, PsiExpression.class);
      if (nextExpression != null) {
        elementAtStart = nextExpression;
        startOffset = nextExpression.getTextRange().getStartOffset();
      }
    }
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
    if (elementAtEnd != null && isStringLiteral(elementAtEnd) && elementAtEnd.getTextRange().getStartOffset() + 1 == endOffset) {
      PsiLiteralExpression expressionAtEnd = PsiTreeUtil.getParentOfType(elementAtEnd, PsiLiteralExpression.class);
      PsiExpression prevExpression = PsiTreeUtil.getPrevSiblingOfType(expressionAtEnd, PsiExpression.class);
      if (prevExpression != null) {
        elementAtEnd = prevExpression;
        endOffset = prevExpression.getTextRange().getEndOffset();
      }
    }
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

      if (startLiteralExpression == null && endLiteralExpression == null && StringUtil.endsWithChar(text, ';')) {
        text = text.substring(0, text.length() - 1);
      }

      if (literalExpression != null && text.equals(literalExpression.getText())) return literalExpression;

      final PsiElement parent = literalExpression != null ? literalExpression : elementAt;
      PsiElement commonParent = PsiTreeUtil.findCommonParent(elementAtStart, elementAtEnd);
      PsiElement context = Objects.requireNonNullElse(parent, commonParent);
      if (TextRange.create(startOffset, endOffset).contains(context.getTextRange())){
        context = context.getParent();
      }
      tempExpr = elementFactory.createExpressionFromText(text, context);

      if (ErrorUtil.containsDeepError(tempExpr)) return null;

      tempExpr.putUserData(ElementToWorkOn.PREFIX, prefix);
      tempExpr.putUserData(ElementToWorkOn.SUFFIX, suffix);

      Document document = PsiDocumentManager.getInstance(project).getDocument(file);
      RangeMarker rangeMarker = document != null ? document.createRangeMarker(startOffset, endOffset) : null;
      tempExpr.putUserData(ElementToWorkOn.TEXT_RANGE, rangeMarker);

      if (parent != null) {
        tempExpr.putUserData(ElementToWorkOn.PARENT, parent);
      }
      else {
        PsiElement errorElement = elementAtStart instanceof PsiErrorElement
                                       ? (PsiErrorElement)elementAtStart
                                       : PsiTreeUtil.getNextSiblingOfType(elementAtStart, PsiErrorElement.class);
        if (errorElement == null) {
          errorElement = PsiTreeUtil.getParentOfType(elementAtStart, PsiErrorElement.class);
        }
        if (commonParent instanceof PsiMethod method && isIncompleteMethod(method)) {
          errorElement = method;
        }
        if (errorElement == null) return null;
        if (errorElement.getParent() instanceof PsiMethod method && isIncompleteMethod(method)) {
          errorElement = method;
        }
        if (!(errorElement.getParent() instanceof PsiClass)) return null;
        tempExpr.putUserData(ElementToWorkOn.PARENT, errorElement);
        tempExpr.putUserData(ElementToWorkOn.OUT_OF_CODE_BLOCK, Boolean.TRUE);
      }

      final String fakeInitializer = "intellijidearulezzz";
      final int[] refIdx = new int[1];
      final PsiElement toBeExpression = createReplacement(fakeInitializer, project, prefix, suffix, parent, TextRange.create(startOffset, endOffset), refIdx);
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

  private static boolean isStringLiteral(PsiElement elementAtEnd) {
    return elementAtEnd.getNode().getElementType().equals(JavaTokenType.STRING_LITERAL);
  }

  private static boolean isIncompleteMethod(@Nullable PsiMethod incompleteMethod) {
    return incompleteMethod != null && incompleteMethod.getReturnTypeElement() == null && incompleteMethod.getBody() == null;
  }

  private static PsiExpression getSelectionFromInjectedHost(Project project,
                                                            PsiFile file,
                                                            InjectedLanguageManager injectedLanguageManager, int startOffset, int endOffset) {
    final PsiLanguageInjectionHost injectionHost = injectedLanguageManager.getInjectionHost(file);
    return getSelectedExpression(project, injectionHost.getContainingFile(), injectedLanguageManager.injectedToHost(file, startOffset), injectedLanguageManager.injectedToHost(file, endOffset));
  }

  public static @NlsContexts.DialogMessage @Nullable String getErrorMessage(PsiExpression expr) {
    final Boolean needParenthesis = expr.getCopyableUserData(NEED_PARENTHESIS);
    if (needParenthesis != null && needParenthesis.booleanValue()) {
      return JavaBundle.message("introduce.variable.change.semantics.warning");
    }
    if (expr instanceof PsiClassObjectAccessExpression && PsiUtilCore.hasErrorElementChild(expr)) {
      return JavaRefactoringBundle.message("selected.block.should.represent.an.expression");
    }
    if (expr instanceof PsiSuperExpression) {
      return JavaRefactoringBundle.message("selected.expression.cannot.be.extracted");
    }
    if (!CodeBlockSurrounder.canSurround(expr)) {
      PsiExpression topLevelExpression = ExpressionUtils.getTopLevelExpression(expr);
      if (topLevelExpression != expr) {
        for (PsiVariable variable : VariableAccessUtils.collectUsedVariables(expr)) {
          if (variable instanceof PsiPatternVariable && PsiTreeUtil.isAncestor(topLevelExpression, variable, true) &&
              !PsiTreeUtil.isAncestor(expr, variable, true)) {
            return JavaRefactoringBundle.message("introduce.variable.message.expression.refers.to.pattern.variable.declared.outside", variable.getName());
          }
        }
      }
      if (topLevelExpression.getParent() instanceof PsiField f && f.getParent() instanceof PsiImplicitClass) {
        return JavaRefactoringBundle.message("introduce.variable.message.cannot.extract.in.implicit.class");
      }
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

  private static PsiElement createReplacement(final @NonNls String refText, final Project project,
                                             final String prefix,
                                             final String suffix,
                                             final PsiElement parent, final TextRange textRange, int[] refIdx) {
    String text = refText;
    if (parent != null) {
      final String allText = parent.getContainingFile().getText();
      final TextRange parentRange = parent.getTextRange();

      LOG.assertTrue(parentRange.getStartOffset() <= textRange.getStartOffset(), parent + "; prefix:" + prefix + "; suffix:" + suffix);
      String beg = allText.substring(parentRange.getStartOffset(), textRange.getStartOffset());
      //noinspection SSBasedInspection (suggested replacement breaks behavior)
      if (StringUtil.stripQuotesAroundValue(beg).trim().isEmpty() && prefix == null) beg = "";

      LOG.assertTrue(textRange.getEndOffset() <= parentRange.getEndOffset(), parent + "; prefix:" + prefix + "; suffix:" + suffix);
      String end = allText.substring(textRange.getEndOffset(), parentRange.getEndOffset());
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

  public static boolean isChooserNeeded(List<? extends PsiExpression> expressions) {
    if (expressions.size() == 1) {
      final PsiExpression expression = expressions.get(0);
      return expression instanceof PsiNewExpression && ((PsiNewExpression)expression).getAnonymousClass() != null;
    }
    return true;
  }

  public static Expression createExpression(final TypeExpression expression, final String defaultType) {
    return new Expression() {
      @Override
      public Result calculateResult(ExpressionContext context) {
        return new TextResult(defaultType);
      }

      @Override
      public LookupElement[] calculateLookupItems(ExpressionContext context) {
        final LookupElement[] elements = expression.calculateLookupItems(context);
        if (elements != null) {
          LookupElement toBeSelected = null;
          for (LookupElement element : elements) {
            if (element instanceof PsiTypeLookupItem && ((PsiTypeLookupItem)element).getType().getPresentableText().equals(defaultType)) {
              toBeSelected = element;
              break;
            }
          }
          if (toBeSelected != null) {
            final int idx = ArrayUtil.find(elements, toBeSelected);
            if (idx > 0) {
              return ArrayUtil.prepend(toBeSelected, ArrayUtil.remove(elements, idx));
            }
          }
        }
        return elements;
      }

      @Override
      public String getAdvertisingText() {
        return null;
      }
    };
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
      else if (copyVariableInitializer instanceof PsiArrayInitializerExpression initializer) {
        AddNewArrayExpressionFix.doFix(initializer);
      }
      else if (copyVariableInitializer instanceof PsiFunctionalExpression) {
        PsiTypeCastExpression castExpression =
          (PsiTypeCastExpression)JavaPsiFacade.getElementFactory(copyVariableInitializer.getProject())
            .createExpressionFromText("(" + typeElement.getText() + ")a", copyVariableInitializer);
        Objects.requireNonNull(castExpression.getOperand()).replace(copyVariableInitializer);
        copyVariableInitializer.replace(castExpression);
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
      expr2 = CommonJavaRefactoringUtil.outermostParenthesizedExpression(expr1);
    }
    if (expr2.isPhysical() || expr1.getUserData(ElementToWorkOn.REPLACE_NON_PHYSICAL) != null || IntentionPreviewUtils.isPreviewElement(expr2)) {
      return expr2.replace(ref);
    }
    else {
      final String prefix  = expr1.getUserData(ElementToWorkOn.PREFIX);
      final String suffix  = expr1.getUserData(ElementToWorkOn.SUFFIX);
      final PsiElement parent = expr1.getUserData(ElementToWorkOn.PARENT);
      final RangeMarker rangeMarker = expr1.getUserData(ElementToWorkOn.TEXT_RANGE);

      LOG.assertTrue(parent != null, expr1);
      LOG.assertTrue(rangeMarker != null, expr1);
      final TextRange textRange = rangeMarker.getTextRange();
      return parent.replace(createReplacement(ref.getText(), project, prefix, suffix, parent, textRange, new int[1]));
    }
  }
}
