// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker;

import com.intellij.codeInspection.SuppressManager;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.callMatcher.CallHandler;
import com.siyeh.ig.callMatcher.CallMapper;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * @author shkate@jetbrains.com
 */
public class JavaSpellcheckingStrategy extends SpellcheckingStrategy {
  private final MethodNameTokenizerJava myMethodNameTokenizer = new MethodNameTokenizerJava();
  private final DocCommentTokenizer myDocCommentTokenizer = new DocCommentTokenizer();
  private final LiteralExpressionTokenizer myLiteralExpressionTokenizer = new LiteralExpressionTokenizer();
  private final NamedElementTokenizer myNamedElementTokenizer = new NamedElementTokenizer();


  private static final CallMapper<ArgumentMatcher> SKIP_ARGUMENT_METHOD_HANDLER = new CallMapper<>(
    CallHandler.of(CallMatcher.staticCall("java.time.format.DateTimeFormatter", "ofPattern"),
                   methodCall -> argumentNumber(0, methodCall)),
    CallHandler.of(CallMatcher.instanceCall("java.time.format.DateTimeFormatterBuilder", "appendPattern"),
                   methodCall -> argumentNumber(0, methodCall)),
    CallHandler.of(CallMatcher.instanceCall("java.text.SimpleDateFormat", "applyPattern"),
                   methodCall -> argumentNumber(0, methodCall)),
    CallHandler.of(CallMatcher.instanceCall("java.text.SimpleDateFormat", "applyLocalizedPattern"),
                   methodCall -> argumentNumber(0, methodCall))
  );

  private static final Map<String, BiPredicate<PsiNewExpression, PsiElement>> SKIP_ARGUMENT_CONSTRUCTOR_HANDLER =
    Map.ofEntries(
      Map.entry("java.text.SimpleDateFormat", (expression, psiElement) -> argumentNumber(0, expression).test(psiElement))
    );

  interface ArgumentMatcher extends Predicate<PsiElement> {
  }

  private static ArgumentMatcher argumentNumber(@SuppressWarnings("SameParameterValue") int number, @NotNull PsiCall callExpression) {
    return psiElement -> {
      PsiExpressionList argumentList = callExpression.getArgumentList();
      if (argumentList == null) {
        return false;
      }
      PsiExpression[] expressions = argumentList.getExpressions();
      if (number < 0 || number >= expressions.length) {
        return false;
      }
      return expressions[number] == psiElement;
    };
  }

  @NotNull
  @Override
  public Tokenizer getTokenizer(PsiElement element) {
    if (element instanceof PsiMethod) {
      return myMethodNameTokenizer;
    }
    if (element instanceof PsiDocComment) {
      return myDocCommentTokenizer;
    }
    if (element instanceof PsiLiteralExpression literalExpression) {
      if (SuppressManager.isSuppressedInspectionName(literalExpression) ||
          skipKnownMethodArgument(literalExpression)) {
        return EMPTY_TOKENIZER;
      }
      return myLiteralExpressionTokenizer;
    }
    if (element instanceof PsiNamedElement) {
      return myNamedElementTokenizer;
    }

    return super.getTokenizer(element);
  }

  private static boolean skipKnownMethodArgument(@NotNull PsiLiteralExpression expression) {
    PsiType type = expression.getType();
    if (type == null || !type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
      return false;
    }
    PsiElement element = PsiUtil.skipParenthesizedExprUp(expression);
    if (element == null) {
      return false;
    }
    if (element.getParent() instanceof PsiVariable psiVariable) {
      List<PsiReferenceExpression> references = VariableAccessUtils.getVariableReferences(psiVariable, PsiTreeUtil.getParentOfType(psiVariable, PsiCodeBlock.class, PsiMethod.class, PsiClass.class));
      return ContainerUtil.or(references, reference -> checkCall(reference));
    }
    return checkCall(element);
  }

  private static boolean checkCall(PsiElement source) {
    PsiElement element = PsiUtil.skipParenthesizedExprUp(source);
    PsiElement parent = element.getParent();
    if (!(parent instanceof PsiExpressionList expressionList)) {
      return false;
    }

    if (!(expressionList.getParent() instanceof PsiCall psiCall)) {
      return false;
    }

    if (psiCall instanceof PsiMethodCallExpression callExpression) {
      ArgumentMatcher matcher = SKIP_ARGUMENT_METHOD_HANDLER.mapFirst(callExpression);
      if (matcher == null) {
        return false;
      }
      return matcher.test(element);
    }
    if (psiCall instanceof PsiNewExpression newExpression) {
      PsiJavaCodeReferenceElement reference = newExpression.getClassReference();
      if (reference == null) {
        return false;
      }
      BiPredicate<PsiNewExpression, PsiElement> predicate =
        SKIP_ARGUMENT_CONSTRUCTOR_HANDLER.get(reference.getQualifiedName());
      if (predicate == null) {
        return false;
      }
      return predicate.test(newExpression, element);
    }
    return false;
  }
}
