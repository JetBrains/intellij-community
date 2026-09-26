// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.intention.CommonIntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.java.syntax.JavaSyntaxBundle;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiCatchSection;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiLabeledStatement;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiLoopStatement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiSwitchBlock;
import com.intellij.psi.PsiTryStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiTypeParameterList;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public final class HighlightFixTypoUtil {
  private HighlightFixTypoUtil() {
  }

  static @Nullable CommonIntentionAction createKeywordTypoFix(@Nullable PsiErrorElement psiErrorElement) {
    if (psiErrorElement == null) return null;
    ModCommandAction fix = createThrowsTypoFix(psiErrorElement);
    if (fix != null) return fix;
    fix = createImplementExtendTypoFix(psiErrorElement);
    return fix;
  }

  static @Nullable CommonIntentionAction createKeywordTypoFix(@Nullable PsiStatement psiStatement) {
    if (psiStatement == null) return null;
    ModCommandAction fix = createSwitchDefaultTypoFix(psiStatement);
    return fix;
  }

  private static ModCommandAction createSwitchDefaultTypoFix(@NotNull PsiStatement statement) {
    if (!(statement instanceof PsiExpressionStatement expressionStatement)) return null;
    if (!(expressionStatement.getExpression() instanceof PsiLambdaExpression lambdaExpression)) return null;
    PsiParameterList parameterList = lambdaExpression.getParameterList();
    if (parameterList.getParametersCount() != 1) return null;
    PsiParameter parameter = parameterList.getParameter(0);
    if (!(statement.getParent() instanceof PsiCodeBlock codeBlock &&
          codeBlock.getParent() instanceof PsiSwitchBlock)) {
      return null;
    }
    return QuickFixFactory.getInstance().createChangeToSimilarKeyword(parameter, Collections.singleton(JavaKeywords.DEFAULT));
  }

  static @Nullable CommonIntentionAction createKeywordTypoFix(@Nullable PsiLabeledStatement labeledStatement) {
    if (labeledStatement == null) return null;
    ModCommandAction fix = createSwitchDefaultTypoFix(labeledStatement);
    return fix;
  }

  private static @Nullable ModCommandAction createImplementExtendTypoFix(@NotNull PsiErrorElement psiErrorElement) {
    boolean extendFound = false;
    boolean implementFound = false;
    PsiElement firstReferenceList = PsiTreeUtil.skipWhitespacesBackward(psiErrorElement);
    if (!(firstReferenceList instanceof PsiReferenceList referenceList1)) return null;
    PsiElement secondReferenceList = PsiTreeUtil.skipWhitespacesBackward(firstReferenceList);
    if (!(secondReferenceList instanceof PsiReferenceList referenceList2)) return null;
    for (PsiReferenceList list : Set.of(referenceList1, referenceList2)) {
      if (list.getChildren().length != 0) {
        PsiElement firstChild = list.getFirstChild();
        if (firstChild instanceof PsiKeyword && JavaKeywords.EXTENDS.equals(firstChild.getText())) {
          if (extendFound) return null;
          extendFound = true;
        }
        else if (firstChild instanceof PsiJavaCodeReferenceElement ref && JavaKeywords.IMPLEMENTS.equals(ref.getText())) {
          if (implementFound) return null;
          implementFound = true;
        }
      }
    }
    PsiElement typeParameters = PsiTreeUtil.skipWhitespacesBackward(secondReferenceList);
    if (!(typeParameters instanceof PsiTypeParameterList)) return null;
    PsiElement identifier = PsiTreeUtil.skipWhitespacesBackward(typeParameters);
    if (!(identifier instanceof PsiIdentifier)) return null;
    PsiElement classKeyword = PsiTreeUtil.skipWhitespacesBackward(identifier);
    if (!(classKeyword instanceof PsiKeyword)) return null;
    PsiElement targetElement = psiErrorElement.getChildren().length < 2 ? psiErrorElement : psiErrorElement.getChildren()[0];
    return switch (classKeyword.getText()) {
      case JavaKeywords.CLASS -> {
        Set<String> keywords = new HashSet<>();
        if (!extendFound) keywords.add(JavaKeywords.EXTENDS);
        if (!implementFound) keywords.add(JavaKeywords.IMPLEMENTS);
        yield QuickFixFactory.getInstance().createChangeToSimilarKeyword(targetElement, keywords);
      }
      case JavaKeywords.INTERFACE -> {
        Set<String> keywords = new HashSet<>();
        if (!extendFound) keywords.add(JavaKeywords.EXTENDS);
        yield QuickFixFactory.getInstance().createChangeToSimilarKeyword(targetElement, keywords);
      }
      case JavaKeywords.ENUM, JavaKeywords.RECORD -> {
        Set<String> keywords = new HashSet<>();
        if (!implementFound) keywords.add(JavaKeywords.IMPLEMENTS);
        yield QuickFixFactory.getInstance().createChangeToSimilarKeyword(targetElement, keywords);
      }
      default -> null;
    };
  }

  private static @Nullable ModCommandAction createThrowsTypoFix(@NotNull PsiErrorElement psiErrorElement) {
    // class A throw<caret> SomeException
    PsiElement prevVisibleLeaf = PsiTreeUtil.prevVisibleLeaf(psiErrorElement);
    if (!(prevVisibleLeaf instanceof PsiJavaToken token && token.getTokenType() == JavaTokenType.RPARENTH)) return null;
    if (!(token.getParent() instanceof PsiParameterList parameterList && parameterList.getParent() instanceof PsiMethod)) {
      return null;
    }
    PsiElement element = psiErrorElement.getChildren().length < 2 ? psiErrorElement : psiErrorElement.getChildren()[0];
    return QuickFixFactory.getInstance().createChangeToSimilarKeyword(element, Collections.singleton(JavaKeywords.THROWS));
  }

  static @Nullable CommonIntentionAction createKeywordTypoFix(@Nullable PsiMethodCallExpression methodCallExpression) {
    if (methodCallExpression == null) return null;
    if (methodCallExpression.getMethodExpression().getQualifierExpression() != null) return null;
    CommonIntentionAction action = createSynchronizedSwitchTypoFix(methodCallExpression);
    if (action != null) return action;
    action = createCatchFinallyTypoFix(methodCallExpression.getMethodExpression());
    return action;
  }

  @Nullable
  private static CommonIntentionAction createSynchronizedSwitchTypoFix(@NotNull PsiMethodCallExpression methodCallExpression) {
    // switc<caret>()
    PsiExpressionList argumentList = methodCallExpression.getArgumentList();
    PsiExpression[] expressions = argumentList.getExpressions();
    if (expressions.length != 0 && expressions.length != 1) return null;
    Set<String> keywords = new HashSet<>();
    PsiElement parent = methodCallExpression.getParent();
    if (parent instanceof PsiExpressionStatement) {
      keywords.add(JavaKeywords.SYNCHRONIZED);
      keywords.add(JavaKeywords.SWITCH);
    }
    else if (parent instanceof PsiExpression ||
             parent instanceof PsiExpressionList ||
             parent instanceof PsiReturnStatement) {
      keywords.add(JavaKeywords.SWITCH);
    }
    return QuickFixFactory.getInstance()
      .createChangeToSimilarKeyword(methodCallExpression.getMethodExpression(), keywords);
  }


  static @Nullable CommonIntentionAction createKeywordTypoFix(@Nullable PsiJavaCodeReferenceElement ref) {
    if (ref == null) return null;
    if (ref.isQualified()) return null;
    CommonIntentionAction action = createVariableTypeTypoFix(ref);
    if (action != null) return action;
    action = createBooleanNullTypoFix(ref);
    if (action != null) return action;
    action = createBreakContinueTypoFix(ref);
    if (action != null) return action;
    action = createSynchronizedSwitchTypoFix(ref);
    if (action != null) return action;
    action = createClassTypoFix(ref);
    if (action != null) return action;
    action = createModifiersTypoFix(ref);
    if (action != null) return action;
    action = createCatchFinallyTypoFix(ref);
    if (action != null) return action;
    action = createSwitchDefaultTypoFix(ref);
    if (action != null) return action;
    action = createReturnTypoFix(ref);
    if (action != null) return action;
    action = createNewTypoFix(ref);
    return action;
  }

  @Nullable
  private static CommonIntentionAction createNewTypoFix(@NotNull PsiJavaCodeReferenceElement ref) {
    //return ne<caret> Something();
    if (!(ref instanceof PsiReferenceExpression)) return null;
    if (!(ref.getNextSibling() instanceof PsiErrorElement)) return null;
    PsiElement nextVisibleLeaf = PsiTreeUtil.nextVisibleLeaf(ref);
    if (!(nextVisibleLeaf instanceof PsiIdentifier identifier &&
          identifier.getParent() instanceof PsiReferenceExpression referenceExpression &&
          referenceExpression.getParent() instanceof PsiMethodCallExpression)) {
      return null;
    }
    return QuickFixFactory.getInstance().createChangeToSimilarKeyword(ref, Collections.singleton(JavaKeywords.NEW));
  }

  @Nullable
  private static CommonIntentionAction createReturnTypoFix(@NotNull PsiJavaCodeReferenceElement ref) {
    boolean possiblePlace = false;
    if (PsiTreeUtil.getParentOfType(ref, PsiStatement.class) instanceof PsiStatement statement &&
        statement.getParent() instanceof PsiIfStatement) {
      possiblePlace = true;
    }

    if (!possiblePlace) {
      PsiCodeBlock codeBlock = PsiTreeUtil.getParentOfType(ref, PsiCodeBlock.class);
      if (codeBlock == null) return null;
      PsiStatement[] statements = codeBlock.getStatements();
      if (statements.length == 0) return null;
      //  static int test() {
      //    retur<caret>
      //  }
      if (PsiTreeUtil.isAncestor(statements[statements.length - 1], ref, false)) {
        possiblePlace = true;
      }
      //retur<caret> a();
      if (statements.length > 1 &&
          PsiTreeUtil.isAncestor(statements[statements.length - 2], ref, false) &&
          PsiTreeUtil.hasErrorElements(statements[statements.length - 2])) {
        possiblePlace = true;
      }
    }
    if (!possiblePlace) return null;
    if (ref instanceof PsiReferenceExpression referenceExpression &&
        referenceExpression.getParent() instanceof PsiExpressionStatement &&
        ref.getNextSibling() instanceof PsiErrorElement errorElement &&
        errorElement.getErrorDescription().equals(JavaSyntaxBundle.message("expected.semicolon"))) {
      return QuickFixFactory.getInstance().createChangeToSimilarKeyword(ref, Collections.singleton(JavaKeywords.RETURN));
    }
    if (ref.getParent() instanceof PsiTypeElement) {
      return QuickFixFactory.getInstance().createChangeToSimilarKeyword(ref, Collections.singleton(JavaKeywords.RETURN));
    }
    return null;
  }

  @Nullable
  private static CommonIntentionAction createSwitchDefaultTypoFix(@NotNull PsiJavaCodeReferenceElement ref) {
    if (!(ref instanceof PsiReferenceExpression &&
          ref.getParent() instanceof PsiExpressionStatement expressionStatement &&
          expressionStatement.getParent() instanceof PsiCodeBlock codeBlock &&
          codeBlock.getParent() instanceof PsiSwitchBlock)) {
      return null;
    }
    return QuickFixFactory.getInstance().createChangeToSimilarKeyword(ref, Collections.singleton(JavaKeywords.DEFAULT));
  }

  @Nullable
  private static ModCommandAction createSwitchDefaultTypoFix(@NotNull PsiLabeledStatement labeledStatement) {
    if (!(labeledStatement.getParent() instanceof PsiCodeBlock codeBlock &&
          codeBlock.getParent() instanceof PsiSwitchBlock)) {
      return null;
    }
    PsiElement[] children = labeledStatement.getChildren();
    if (children.length < 2) return null;
    PsiElement child = children[0];
    if (!(child instanceof PsiIdentifier identifier)) return null;
    return QuickFixFactory.getInstance().createChangeToSimilarKeyword(identifier, Collections.singleton(JavaKeywords.DEFAULT));
  }

  @Nullable
  private static CommonIntentionAction createCatchFinallyTypoFix(@NotNull PsiJavaCodeReferenceElement ref) {
    if (!(ref instanceof PsiReferenceExpression)) return null;
    PsiElement element = PsiTreeUtil.prevVisibleLeaf(ref);
    if (!(element instanceof PsiJavaToken token && token.getTokenType() == JavaTokenType.RBRACE)) return null;
    if (!(token.getParent() instanceof PsiCodeBlock block)) return null;
    PsiElement parent = block.getParent();
    if (!(parent instanceof PsiTryStatement || parent instanceof PsiCatchSection)) return null;
    List<@NotNull String> keywords = ref.getParent() instanceof PsiMethodCallExpression ?
                                     List.of(JavaKeywords.CATCH) :
                                     List.of(JavaKeywords.CATCH, JavaKeywords.FINALLY);
    return QuickFixFactory.getInstance().createChangeToSimilarKeyword(ref, keywords);
  }

  //it contains only basic checks, there can be more, but it is enough for now
  private static final List<ModifierHandler> MODIFIERS =
    List.of(new ModifierHandler(new String[]{JavaKeywords.PUBLIC, JavaKeywords.PROTECTED, JavaKeywords.PRIVATE}, e -> true, (m, e) -> true),
            new ModifierHandler(new String[]{JavaKeywords.STATIC, JavaKeywords.DEFAULT}, e -> e.getParent() instanceof PsiClass,
                                (m, e) -> !JavaKeywords.DEFAULT.equals(m) ||
                                          e.getParent() instanceof PsiClass psiClass &&
                                          psiClass.isInterface() &&
                                          PsiUtil.isAvailable(JavaFeature.STATIC_INTERFACE_CALLS, e)),
            new ModifierHandler(new String[]{JavaKeywords.FINAL, JavaKeywords.ABSTRACT}, e -> true, (m, e) -> true),
            new ModifierHandler(new String[]{JavaKeywords.SEALED, JavaKeywords.NON_SEALED}, e -> true,
                                (m, e) -> PsiUtil.isAvailable(JavaFeature.SEALED_CLASSES, e)),
            new ModifierHandler(new String[]{JavaKeywords.VOLATILE}, e -> e.getParent() instanceof PsiField, (m, e) -> true));

  private record ModifierHandler(String @NotNull [] modifiers, @NotNull Predicate<@NotNull PsiElement> condition,
                                 @NotNull BiPredicate<@NotNull String, @NotNull PsiElement> modifierCondition) {
    public void addModifier(@NotNull Set<@NotNull String> collected, @NotNull Set<@NotNull String> existing, @NotNull PsiElement element) {
      if (!condition.test(element)) return;
      for (String modifier : modifiers) {
        if (existing.contains(modifier)) return;
      }
      for (String modifier : modifiers) {
        if (modifierCondition.test(modifier, element)) {
          collected.add(modifier);
        }
      }
    }
  }

  @Nullable
  private static CommonIntentionAction createModifiersTypoFix(@NotNull PsiJavaCodeReferenceElement ref) {
    if (!(ref.getParent() instanceof PsiTypeElement psiTypeElement)) return null;
    PsiElement parent = psiTypeElement.getParent();
    if (!(parent instanceof PsiField || parent instanceof PsiClass || parent instanceof PsiJavaFile)) return null;
    boolean canBeModifier = false;
    PsiElement forward = PsiTreeUtil.skipWhitespacesForward(psiTypeElement);
    Set<String> existedModifiers = new HashSet<>();
    if (forward instanceof PsiIdentifier psiIdentifier &&
        PsiTreeUtil.skipWhitespacesForward(psiIdentifier) instanceof PsiErrorElement errorElement &&
        errorElement.getErrorDescription().equals(JavaSyntaxBundle.message("expected.semicolon"))) {
      canBeModifier = true;
      collectModifiers(psiIdentifier, existedModifiers);
    }
    if (!canBeModifier &&
        forward instanceof PsiErrorElement errorElement &&
        errorElement.getErrorDescription().equals(JavaSyntaxBundle.message("expected.identifier"))) {
      canBeModifier = true;
      PsiElement modifierListExpected = PsiTreeUtil.skipWhitespacesForward(errorElement);
      if (modifierListExpected != null && modifierListExpected.textMatches("-")) {
        modifierListExpected = PsiTreeUtil.nextVisibleLeaf(modifierListExpected);
      }
      if (modifierListExpected instanceof PsiIdentifier identifier) {
        modifierListExpected = PsiTreeUtil.nextVisibleLeaf(identifier);
      }
      collectModifiers(modifierListExpected, existedModifiers);
    }
    if (!canBeModifier) return null;
    PsiElement modifierListExpected = PsiTreeUtil.skipWhitespacesBackward(psiTypeElement);
    if (modifierListExpected != null && modifierListExpected.getTextLength() == 0) {
      modifierListExpected = PsiTreeUtil.prevVisibleLeaf(modifierListExpected);
    }
    if (modifierListExpected instanceof PsiJavaToken javaToken && javaToken.getTokenType() == JavaTokenType.MINUS) {
      modifierListExpected = PsiTreeUtil.prevVisibleLeaf(modifierListExpected);
      if (modifierListExpected instanceof PsiIdentifier identifier) {
        modifierListExpected = PsiTreeUtil.prevVisibleLeaf(identifier);
      }
    }
    collectModifiers(modifierListExpected, existedModifiers);
    Set<String> modifiers = new HashSet<>();
    for (ModifierHandler modifier : MODIFIERS) {
      modifier.addModifier(modifiers, existedModifiers, psiTypeElement);
    }
    return QuickFixFactory.getInstance().createChangeToSimilarKeyword(psiTypeElement, modifiers);
  }

  private static void collectModifiers(@Nullable PsiElement modifierListExpected, @NotNull Set<String> existedModifiers) {
    if (modifierListExpected == null) return;
    PsiElement canBeModifierList = modifierListExpected;
    if (canBeModifierList instanceof PsiModifierListOwner modifierListOwner) {
      canBeModifierList = modifierListOwner.getModifierList();
    }
    if (canBeModifierList instanceof PsiModifierList modifierList) {
      for (PsiElement element : modifierList.getChildren()) {
        if (element instanceof PsiKeyword keyword) {
          existedModifiers.add(keyword.getText());
        }
      }
    }
    if (modifierListExpected instanceof PsiField field) {
      existedModifiers.add(tryExtendKeyword(field.getNameIdentifier()).first);
    }
    if (modifierListExpected instanceof PsiIdentifier || modifierListExpected instanceof PsiKeyword) {
      existedModifiers.add(tryExtendKeyword(modifierListExpected).first);
    }
  }

  @Nullable
  private static CommonIntentionAction createClassTypoFix(@NotNull PsiJavaCodeReferenceElement ref) {
    if (!(ref.getParent() instanceof PsiTypeElement typeElement)) return null;
    PsiElement parent = typeElement.getParent();
    //skip a case inside methods, it is not so popular
    List<@NotNull String> keywords = List.of(JavaKeywords.CLASS, JavaKeywords.INTERFACE, JavaKeywords.ENUM, JavaKeywords.RECORD);
    if (PsiTreeUtil.skipWhitespacesForward(typeElement) instanceof PsiErrorElement errorElement &&
        errorElement.getErrorDescription().equals(JavaSyntaxBundle.message("expected.identifier")) &&
        (parent instanceof PsiClass || parent instanceof PsiJavaFile)) {
      return QuickFixFactory.getInstance().createChangeToSimilarKeyword(typeElement, keywords);
    }
    if (PsiTreeUtil.skipWhitespacesForward(typeElement) instanceof PsiIdentifier identifier &&
        PsiTreeUtil.skipWhitespacesForward(identifier) instanceof PsiErrorElement errorElement &&
        errorElement.getErrorDescription().equals(JavaSyntaxBundle.message("expected.semicolon")) &&
        parent instanceof PsiField field &&
        field.getInitializer() == null) {
      return QuickFixFactory.getInstance().createChangeToSimilarKeyword(typeElement, keywords);
    }
    return null;
  }

  @Nullable
  private static CommonIntentionAction createSynchronizedSwitchTypoFix(@NotNull PsiJavaCodeReferenceElement ref) {
    if (ref.getParent() instanceof PsiExpressionStatement expressionStatement) {
      PsiElement[] children = expressionStatement.getChildren();
      if (children.length == 2 && children[0] == ref && children[1] instanceof PsiErrorElement) {
        return QuickFixFactory.getInstance().createChangeToSimilarKeyword(ref,
                                                                          List.of(JavaKeywords.SYNCHRONIZED, JavaKeywords.SWITCH));
      }
    }
    return null;
  }

  @Nullable
  private static CommonIntentionAction createBreakContinueTypoFix(@NotNull PsiJavaCodeReferenceElement ref) {
    // contine<caret>;
    if (ref.getParent() instanceof PsiExpressionStatement expressionStatement) {
      PsiElement[] children = expressionStatement.getChildren();
      if (children.length == 2 &&
          children[0] == ref &&
          (children[1] instanceof PsiErrorElement ||
           children[1] instanceof PsiJavaToken javaToken && javaToken.getTokenType() == JavaTokenType.SEMICOLON)) {
        Set<String> targetKeywords = new HashSet<>();
        if (PsiTreeUtil.getParentOfType(expressionStatement, PsiLoopStatement.class) != null) {
          targetKeywords.add(JavaKeywords.BREAK);
          targetKeywords.add(JavaKeywords.CONTINUE);
        }
        if (PsiTreeUtil.getParentOfType(expressionStatement, PsiSwitchBlock.class) != null) {
          targetKeywords.add(JavaKeywords.BREAK);
        }
        return QuickFixFactory.getInstance().createChangeToSimilarKeyword(ref, targetKeywords);
      }
    }
    return null;
  }


  private static CommonIntentionAction createBooleanNullTypoFix(@NotNull PsiJavaCodeReferenceElement ref) {
    if (ref instanceof PsiReferenceExpression expression) {
      //call(tru<caret>)
      Set<String> targetKeywords = new HashSet<>();
      PsiType expectedType = ExpectedTypeUtils.findExpectedType(expression, false);
      if (expectedType != null) {
        if (TypeConversionUtil.isAssignable(expectedType, PsiTypes.booleanType())) {
          targetKeywords.add(JavaKeywords.TRUE);
          targetKeywords.add(JavaKeywords.FALSE);
        }
        if (expectedType instanceof PsiClassType) {
          targetKeywords.add(JavaKeywords.NULL);
        }
        return QuickFixFactory.getInstance().createChangeToSimilarKeyword(ref, targetKeywords);
      }
    }
    return null;
  }

  @Nullable
  private static CommonIntentionAction createVariableTypeTypoFix(@NotNull PsiJavaCodeReferenceElement ref) {
    if (ref.getParent() instanceof PsiTypeElement typeElement) {
      PsiElement parent = typeElement.getParent();
      if (parent instanceof PsiClass && typeElement.getNextSibling() instanceof PsiErrorElement) {
        return QuickFixFactory.getInstance().createChangeToSimilarKeyword(typeElement, PsiTypes.primitiveTypeNames());
      }
      if (!(parent instanceof PsiVariable variable &&
            !(parent instanceof PsiParameter parameter && !(parameter.getParent() instanceof PsiParameterList)))) {
        return null;
      }
      Set<String> targetKeywords = new HashSet<>(PsiTypes.primitiveTypeNames());
      PsiExpression initializer = variable.getInitializer();
      if (initializer != null && initializer.getType() != null) {
        PsiType initializerType = initializer.getType();
        targetKeywords.removeIf(t -> {
          PsiPrimitiveType targetType = PsiTypes.primitiveTypeByName(t);
          if (targetType == null) return true;
          return !TypeConversionUtil.isAssignable(targetType, initializerType);
        });
      }
      if (parent instanceof PsiLocalVariable && PsiUtil.isAvailable(JavaFeature.LVTI, parent)) {
        targetKeywords.add(JavaKeywords.VAR);
      }
      return QuickFixFactory.getInstance().createChangeToSimilarKeyword(typeElement, targetKeywords);
    }
    if (ref.getParent() instanceof PsiExpressionStatement expressionStatement) {
      // val<caret> something = something();
      PsiElement[] children = expressionStatement.getChildren();
      if (children.length == 2 && children[0] == ref && children[1] instanceof PsiErrorElement) {
        Set<String> targetKeywords = new HashSet<>(PsiTypes.primitiveTypeNames());
        if (PsiUtil.isAvailable(JavaFeature.LVTI, expressionStatement)) {
          targetKeywords.add(JavaKeywords.VAR);
        }
        return QuickFixFactory.getInstance().createChangeToSimilarKeyword(ref, targetKeywords);
      }
    }
    return null;
  }

  /**
   * Tries to extend the old element to a keyword with "-".
   */
  @NotNull
  public static Pair<String, TextRange> tryExtendKeyword(@NotNull PsiElement old) {
    if (PsiTreeUtil.nextVisibleLeaf(old) instanceof PsiJavaToken javaToken && javaToken.getTokenType() == JavaTokenType.MINUS) {
      PsiElement secondPart = PsiTreeUtil.nextVisibleLeaf(javaToken);
      if (secondPart instanceof PsiIdentifier || secondPart instanceof PsiKeyword) {
        String newText = old.getText() + "-" + secondPart.getText();
        TextRange newTextRange = new TextRange(old.getTextRange().getStartOffset(), secondPart.getTextRange().getEndOffset());
        return new Pair<>(newText, newTextRange);
      }
    }
    if (PsiTreeUtil.prevVisibleLeaf(old) instanceof PsiJavaToken javaToken &&
        javaToken.getTokenType() == JavaTokenType.MINUS &&
        PsiTreeUtil.prevVisibleLeaf(javaToken) instanceof PsiIdentifier identifier) {
      String newText = identifier.getText() + "-" + old.getText();
      TextRange newTextRange = new TextRange(identifier.getTextRange().getStartOffset(), old.getTextRange().getEndOffset());
      return new Pair<>(newText, newTextRange);
    }
    return new Pair<>(old.getText(), old.getTextRange());
  }
}
