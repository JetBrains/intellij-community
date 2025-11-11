// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.modcompletion;

import com.intellij.codeInsight.JavaTailTypes;
import com.intellij.codeInsight.ModNavigatorTailType;
import com.intellij.codeInsight.TailTypes;
import com.intellij.codeInsight.completion.AllClassesGetter;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.completion.JavaPsiClassReferenceElement;
import com.intellij.codeInsight.completion.ReferenceExpressionCompletionContributor;
import com.intellij.java.codeserver.core.JavaPsiSwitchUtil;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcompletion.CompletionItem;
import com.intellij.modcompletion.CompletionItemProvider;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.MarkupText;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.filters.FilterPositionUtil;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.util.JavaPsiPatternUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.SealedUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static com.intellij.patterns.PsiJavaPatterns.psiAnnotation;
import static com.intellij.patterns.PsiJavaPatterns.psiElement;
import static com.intellij.patterns.StandardPatterns.or;
import static com.intellij.patterns.StandardPatterns.string;

/**
 * A provider for Java keywords completion.
 */
@NotNullByDefault
final class KeywordCompletionItemProvider implements CompletionItemProvider {
  @Override
  public void provideItems(CompletionContext context, Consumer<CompletionItem> sink) {
    PsiElement element = context.element();
    if (!context.isSmart()) {
      if (canAddKeywords(element)) {
        if (isStatementPosition(element)) {
          addStatementKeywords(context, sink);
        }
      }
    }
    addEnumCases(element, sink);
    addEnhancedCases(element, sink);
  }

  private static void addStatementKeywords(CompletionContext context, Consumer<CompletionItem> sink) {
    PsiElement element = context.element();
    PsiElement prevLeaf = FilterPositionUtil.searchNonSpaceNonCommentBack(element);
    if (psiElement()
      .withText("}")
      .withParent(psiElement(PsiCodeBlock.class).withParent(or(psiElement(PsiTryStatement.class), psiElement(PsiCatchSection.class))))
      .accepts(prevLeaf)) {
      sink.accept(createItem(JavaKeywords.CATCH, JavaTailTypes.CATCH_LPARENTH));
      sink.accept(createItem(JavaKeywords.FINALLY, JavaTailTypes.FINALLY_LBRACE));
      if (prevLeaf != null && prevLeaf.getParent().getNextSibling() instanceof PsiErrorElement) {
        return;
      }
    }
    sink.accept(createItem(JavaKeywords.SWITCH, JavaTailTypes.SWITCH_LPARENTH));
    sink.accept(createItem(JavaKeywords.WHILE, JavaTailTypes.WHILE_LPARENTH));
    sink.accept(createItem(JavaKeywords.DO, JavaTailTypes.DO_LBRACE));
    sink.accept(createItem(JavaKeywords.FOR, JavaTailTypes.FOR_LPARENTH));
    sink.accept(createItem(JavaKeywords.IF, JavaTailTypes.IF_LPARENTH));
    sink.accept(createItem(JavaKeywords.TRY, JavaTailTypes.TRY_LBRACE));
    sink.accept(createItem(JavaKeywords.SYNCHRONIZED, JavaTailTypes.SYNCHRONIZED_LPARENTH));
    sink.accept(createItem(JavaKeywords.THROW, (ModNavigatorTailType)TailTypes.insertSpaceType()));
    sink.accept(createItem(JavaKeywords.NEW, (ModNavigatorTailType)TailTypes.insertSpaceType()));
    if (PsiUtil.isAvailable(JavaFeature.ASSERTIONS, element)) {
      sink.accept(createItem(JavaKeywords.ASSERT, (ModNavigatorTailType)TailTypes.insertSpaceType()));
    }
    if (!(PsiTreeUtil.getParentOfType(element, PsiSwitchExpression.class, PsiLambdaExpression.class) 
            instanceof PsiSwitchExpression)) {
      sink.accept(createItem(JavaKeywords.RETURN, getReturnTail(element)));
    }
    if (psiElement().withText(";").withSuperParent(2, PsiIfStatement.class).accepts(prevLeaf) ||
        psiElement().withText("}").withSuperParent(3, PsiIfStatement.class).accepts(prevLeaf)) {
      CommonCompletionItem elseKeyword = createItem(JavaKeywords.ELSE, (ModNavigatorTailType)TailTypes.humbleSpaceBeforeWordType());
      CharSequence text = element.getContainingFile().getFileDocument().getCharsSequence();
      int offset = context.offset();
      while (text.length() > offset && Character.isWhitespace(text.charAt(offset))) {
        offset++;
      }
      if (text.length() > offset + JavaKeywords.ELSE.length() &&
          text.subSequence(offset, offset + JavaKeywords.ELSE.length()).toString().equals(JavaKeywords.ELSE) &&
          Character.isWhitespace(text.charAt(offset + JavaKeywords.ELSE.length()))) {
        elseKeyword = elseKeyword.withPriority(-1);
      }
      sink.accept(elseKeyword);
    }
  }

  private static CommonCompletionItem createItem(@NlsSafe String keyword, ModNavigatorTailType tailType) {
    return new CommonCompletionItem(keyword).withObject(new KeywordInfo(keyword)).withTail(tailType);
  }

  void addEnumCases(PsiElement position, Consumer<CompletionItem> sink) {
    PsiSwitchBlock switchBlock = getSwitchFromLabelPosition(position);
    PsiExpression expression = switchBlock == null ? null : switchBlock.getExpression();
    PsiClass switchType = expression == null ? null : PsiUtil.resolveClassInClassTypeOnly(expression.getType());
    if (switchType == null || !switchType.isEnum()) return;

    Set<PsiField> used = ReferenceExpressionCompletionContributor.findConstantsUsedInSwitch(switchBlock);
    ModNavigatorTailType tailType = JavaTailTypes.forSwitchLabel(switchBlock);
    for (PsiField field : switchType.getAllFields()) {
      String name = field.getName();
      if (!(field instanceof PsiEnumConstant) || used.contains(CompletionUtil.getOriginalOrSelf(field))) {
        continue;
      }
      @NlsSafe String prefix = "case ";
      CommonCompletionItem caseConst =
        new CommonCompletionItem(prefix + name)
          .addLookupString(name)
          .adjustIndent()
          .withTail(tailType)
          .withObject(field)
          .withPresentation(MarkupText.builder().append(prefix, MarkupText.Kind.STRONG).append(name).build())
          .withPriority(prioritizeForRule(switchBlock));
      sink.accept(caseConst);
    }
  }
  
  void addEnhancedCases(PsiElement position, Consumer<CompletionItem> sink) {
    if (!canAddKeywords(position)) return;

    boolean statementPosition = isStatementPosition(position);
    if (statementPosition) {
      addCaseDefault(position, sink);

      addPatternMatchingInSwitchCases(position, sink);
    }
    PsiElement parent = position.getParent();
    if (parent != null && parent.getParent() instanceof PsiCaseLabelElementList) {
      addCaseAfterNullDefault(position, sink);
    }
  }

  private static void addCaseAfterNullDefault(PsiElement position, Consumer<CompletionItem> sink) {
    if (!PsiUtil.isAvailable(JavaFeature.PATTERNS_IN_SWITCH, position)) return;
    PsiCaseLabelElementList labels = PsiTreeUtil.getParentOfType(position, PsiCaseLabelElementList.class);
    if (labels == null || labels.getElementCount() != 2 ||
        !(labels.getElements()[0] instanceof PsiLiteralExpression literalExpression &&
          ExpressionUtils.isNullLiteral(literalExpression))) {
      return;
    }

    PsiSwitchBlock switchBlock = PsiTreeUtil.getParentOfType(labels, PsiSwitchBlock.class);
    if (switchBlock == null) return;
    List<PsiSwitchLabelStatementBase> allBranches =
      PsiTreeUtil.getChildrenOfTypeAsList(switchBlock.getBody(), PsiSwitchLabelStatementBase.class);
    if (allBranches.isEmpty() || allBranches.getLast().getCaseLabelElementList() != labels) {
      return;
    }
    if (JavaPsiSwitchUtil.findDefaultElement(switchBlock) != null) {
      return;
    }

    CompletionItem defaultCaseRule = createItem(JavaKeywords.DEFAULT, JavaTailTypes.forSwitchLabel(switchBlock))
      .adjustIndent()
      .withPriority(prioritizeForRule(switchBlock));
    sink.accept(defaultCaseRule);
  }

  private static void addCaseDefault(PsiElement position, Consumer<CompletionItem> sink) {
    PsiSwitchBlock switchBlock = getSwitchFromLabelPosition(position);
    if (switchBlock == null) return;
    PsiElement defaultElement = JavaPsiSwitchUtil.findDefaultElement(switchBlock);
    if (defaultElement != null && defaultElement.getTextRange().getStartOffset() < position.getTextRange().getStartOffset()) return;
    sink.accept(createItem(JavaKeywords.CASE, (ModNavigatorTailType)TailTypes.insertSpaceType()));
    if (defaultElement != null) {
      return;
    }
    CompletionItem defaultCaseRule = createItem(JavaKeywords.DEFAULT, JavaTailTypes.forSwitchLabel(switchBlock))
      .adjustIndent()
      .withPriority(prioritizeForRule(switchBlock));
    sink.accept(defaultCaseRule);
  }

  private static void addPatternMatchingInSwitchCases(PsiElement position, Consumer<CompletionItem> sink) {
    if (!PsiUtil.isAvailable(JavaFeature.PATTERNS_IN_SWITCH, position)) return;

    PsiSwitchBlock switchBlock = getSwitchFromLabelPosition(position);
    if (switchBlock == null) return;

    final PsiType selectorType = getSelectorType(switchBlock);
    if (selectorType == null || selectorType instanceof PsiPrimitiveType) return;

    PsiElement defaultElement = JavaPsiSwitchUtil.findDefaultElement(switchBlock);
    if (defaultElement != null && defaultElement.getTextRange().getStartOffset() < position.getTextRange().getStartOffset()) return;

    final ModNavigatorTailType caseRuleTail = JavaTailTypes.forSwitchLabel(switchBlock);
    Set<String> containedLabels = getSwitchCoveredLabels(switchBlock, position);
    if (!containedLabels.contains(JavaKeywords.NULL)) {
      sink.accept(createCaseRule(JavaKeywords.NULL, caseRuleTail, switchBlock));
      if (!containedLabels.contains(JavaKeywords.DEFAULT)) {
        sink.accept(createCaseRule(JavaKeywords.NULL + ", " + JavaKeywords.DEFAULT, caseRuleTail, switchBlock));
      }
    }
    addSealedHierarchyCases(position, selectorType, containedLabels, sink);
  }

  private static CompletionItem createCaseRule(@NlsSafe String caseRuleName,
                                               ModNavigatorTailType tailType,
                                               @Nullable PsiSwitchBlock switchBlock) {
    @NlsSafe String prefix = "case ";

    return new CommonCompletionItem(prefix + caseRuleName)
      .withPresentation(MarkupText.builder()
                          .append(prefix, MarkupText.Kind.STRONG)
                          .append(caseRuleName).build())
      .withTail(tailType)
      .addLookupString(caseRuleName)
      .adjustIndent()
      .withPriority(prioritizeForRule(switchBlock));
  }

  private static double prioritizeForRule(@Nullable PsiSwitchBlock switchBlock) {
    if (switchBlock == null) {
      return 0;
    }
    PsiCodeBlock body = switchBlock.getBody();
    if (body == null) {
      return 0;
    }
    PsiStatement[] statements = body.getStatements();
    if (statements.length == 0) {
      return 0;
    }
    if (statements[0] instanceof PsiSwitchLabeledRuleStatement) {
      return -1;
    }
    return 0;
  }

  private static Set<String> getSwitchCoveredLabels(@Nullable PsiSwitchBlock block, PsiElement position) {
    HashSet<String> labels = new HashSet<>();
    if (block == null) {
      return labels;
    }
    PsiCodeBlock body = block.getBody();
    if (body == null) {
      return labels;
    }
    int offset = position.getTextRange().getStartOffset();
    for (PsiStatement statement : body.getStatements()) {
      if (!(statement instanceof PsiSwitchLabelStatementBase labelStatement)) continue;
      if (labelStatement.isDefaultCase()) {
        labels.add(JavaKeywords.DEFAULT);
        continue;
      }
      if (labelStatement.getGuardExpression() != null) {
        continue;
      }
      PsiCaseLabelElementList list = labelStatement.getCaseLabelElementList();
      if (list == null) {
        continue;
      }
      for (PsiCaseLabelElement element : list.getElements()) {
        if (element instanceof PsiExpression expr &&
            ExpressionUtils.isNullLiteral(expr)) {
          labels.add(JavaKeywords.NULL);
          continue;
        }
        if (element instanceof PsiDefaultCaseLabelElement) {
          labels.add(JavaKeywords.DEFAULT);
        }
        if (element.getTextRange().getStartOffset() >= offset) {
          break;
        }
        PsiType patternType = JavaPsiPatternUtil.getPatternType(element);
        if (patternType != null && JavaPsiPatternUtil.isUnconditionalForType(element, patternType)) {
          PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(patternType);
          if (psiClass == null) continue;
          String qualifiedName = psiClass.getQualifiedName();
          if (qualifiedName == null) continue;
          labels.add(qualifiedName);
        }
      }
    }
    return labels;
  }

  @Contract(pure = true)
  private static @Nullable PsiType getSelectorType(PsiSwitchBlock switchBlock) {

    final PsiExpression selector = switchBlock.getExpression();
    if (selector == null) return null;

    return selector.getType();
  }

  private static @Nullable PsiSwitchBlock getSwitchFromLabelPosition(PsiElement position) {
    PsiStatement statement = PsiTreeUtil.getParentOfType(position, PsiStatement.class, false, PsiMember.class);
    if (statement == null || statement.getTextRange().getStartOffset() != position.getTextRange().getStartOffset()) {
      return null;
    }

    if (!(statement instanceof PsiSwitchLabelStatementBase) && statement.getParent() instanceof PsiCodeBlock) {
      return ObjectUtils.tryCast(statement.getParent().getParent(), PsiSwitchBlock.class);
    }
    return null;
  }

  private static void addSealedHierarchyCases(PsiElement position, PsiType type, Set<String> containedLabels, Consumer<CompletionItem> sink) {
    final PsiResolveHelper resolver = JavaPsiFacade.getInstance(position.getProject()).getResolveHelper();
    PsiClass aClass = resolver.resolveReferencedClass(type.getCanonicalText(), null);
    if (aClass == null) {
      aClass = PsiUtil.resolveClassInClassTypeOnly(type);
    }
    if (aClass == null || aClass.isEnum() || !aClass.hasModifierProperty(PsiModifier.SEALED)) return;

    for (PsiClass inheritor : SealedUtils.findSameFileInheritorsClasses(aClass)) {
      //we don't check hierarchy here, because it is time-consuming
      if (containedLabels.contains(inheritor.getQualifiedName())) {
        continue;
      }

      final JavaPsiClassReferenceElement item = AllClassesGetter.createLookupItem(inheritor, AllClassesGetter.TRY_SHORTENING);
      item.setForcedPresentableName("case " + inheritor.getName());
      sink.accept(new ClassReferenceCompletionItem(inheritor).withPresentableName("case " + inheritor.getName()));
    }
  }

  private static ModNavigatorTailType getReturnTail(PsiElement position) {
    PsiElement scope = position;
    while (true) {
      if (scope instanceof PsiFile || scope instanceof PsiClassInitializer) {
        return (ModNavigatorTailType)TailTypes.noneType();
      }

      if (scope instanceof PsiMethod method) {
        if (method.isConstructor() || PsiTypes.voidType().equals(method.getReturnType())) {
          return (ModNavigatorTailType)TailTypes.semicolonType();
        }

        return (ModNavigatorTailType)TailTypes.humbleSpaceBeforeWordType();
      }
      if (scope instanceof PsiLambdaExpression lambda) {
        final PsiType returnType = LambdaUtil.getFunctionalInterfaceReturnType(lambda);
        if (PsiTypes.voidType().equals(returnType)) {
          return (ModNavigatorTailType)TailTypes.semicolonType();
        }
        return (ModNavigatorTailType)TailTypes.humbleSpaceBeforeWordType();
      }
      scope = scope.getParent();
    }
  }

  private static boolean canAddKeywords(PsiElement position) {
    if (PsiTreeUtil.getNonStrictParentOfType(position, PsiLiteralExpression.class, PsiComment.class) != null) {
      return false;
    }

    if (psiElement().afterLeaf("::").accepts(position)) {
      return false;
    }
    return true;
  }

  private static boolean isStatementPosition(PsiElement position) {
    if (psiElement()
      .withSuperParent(2, PsiConditionalExpression.class)
      .andNot(psiElement().insideStarting(psiElement(PsiConditionalExpression.class)))
      .accepts(position)) {
      return false;
    }

    if (isEndOfBlock(position) &&
        PsiTreeUtil.getParentOfType(position, PsiCodeBlock.class, true, PsiMember.class) != null) {
      return !isForLoopMachinery(position);
    }

    if (psiElement().withParents(PsiReferenceExpression.class, PsiExpressionStatement.class, PsiIfStatement.class).andNot(
      psiElement().afterLeaf(".")).accepts(position)) {
      PsiElement stmt = position.getParent().getParent();
      PsiIfStatement ifStatement = (PsiIfStatement)stmt.getParent();
      return ifStatement.getElseBranch() == stmt || ifStatement.getThenBranch() == stmt;
    }

    return false;
  }

  static boolean isEndOfBlock(PsiElement element) {
    PsiElement prev = prevSignificantLeaf(element);
    if (prev == null) {
      PsiFile file = element.getContainingFile();
      return !(file instanceof PsiCodeFragment) || isStatementCodeFragment(file);
    }

    if (psiElement().inside(psiAnnotation()).accepts(prev)) return false;

    if (prev instanceof OuterLanguageElement) return true;
    if (psiElement().withText(string().oneOf("{", "}", ";", ":", "else")).accepts(prev)) return true;
    if (prev.textMatches(")")) {
      PsiElement parent = prev.getParent();
      if (parent instanceof PsiParameterList) {
        return PsiTreeUtil.getParentOfType(PsiTreeUtil.prevVisibleLeaf(element), PsiDocComment.class) != null;
      }

      return !(parent instanceof PsiExpressionList || parent instanceof PsiTypeCastExpression
               || parent instanceof PsiRecordHeader);
    }

    return false;
  }

  private static @Nullable PsiElement prevSignificantLeaf(PsiElement position) {
    return FilterPositionUtil.searchNonSpaceNonCommentBack(position);
  }

  private static boolean isStatementCodeFragment(PsiFile file) {
    return file instanceof JavaCodeFragment &&
           !(file instanceof PsiExpressionCodeFragment ||
             file instanceof PsiJavaCodeReferenceCodeFragment ||
             file instanceof PsiTypeCodeFragment);
  }

  private static boolean isForLoopMachinery(PsiElement position) {
    PsiStatement statement = PsiTreeUtil.getParentOfType(position, PsiStatement.class);
    if (statement == null) return false;

    return statement instanceof PsiForStatement ||
           statement.getParent() instanceof PsiForStatement && statement != ((PsiForStatement)statement.getParent()).getBody();
  }

  public record KeywordInfo(String keyword) {
  }
}
