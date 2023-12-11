// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInspection.RemoveRedundantTypeArgumentsUtil;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.EqualityCheck;
import com.siyeh.ig.psiutils.ExpressionUtils;
import one.util.streamex.IntStreamEx;
import one.util.streamex.MoreCollectors;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.util.*;

import static com.intellij.util.ObjectUtils.tryCast;

public final class ExtractSetFromComparisonChainAction implements ModCommandAction {
  private static final String GUAVA_IMMUTABLE_SET = "com.google.common.collect.ImmutableSet";
  private static final String INITIALIZER_FORMAT_GUAVA = GUAVA_IMMUTABLE_SET + ".of({0})";
  private static final String INITIALIZER_FORMAT_JAVA2 =
    CommonClassNames.JAVA_UTIL_COLLECTIONS + ".unmodifiableSet(" +
    "new " + CommonClassNames.JAVA_UTIL_HASH_SET +
    "(" + CommonClassNames.JAVA_UTIL_ARRAYS + ".asList(new {1}[] '{'{0}'}')))";
  private static final String INITIALIZER_FORMAT_JAVA5 =
    CommonClassNames.JAVA_UTIL_COLLECTIONS + ".unmodifiableSet(" +
    "new " + CommonClassNames.JAVA_UTIL_HASH_SET + "<{1}>" +
    "(" + CommonClassNames.JAVA_UTIL_ARRAYS + ".asList({0})))";
  private static final String INITIALIZER_FORMAT_JAVA9 = CommonClassNames.JAVA_UTIL_SET + ".of({0})";
  private static final String INITIALIZER_ENUM_SET =
    CommonClassNames.JAVA_UTIL_COLLECTIONS + ".unmodifiableSet(" +
    "java.util.EnumSet.of({0}))";

  private final @NotNull ThreeState myProcessDuplicates;

  public ExtractSetFromComparisonChainAction() {
    myProcessDuplicates = ThreeState.UNSURE;
  }

  private ExtractSetFromComparisonChainAction(boolean processDuplicates) {
    myProcessDuplicates = ThreeState.fromBoolean(processDuplicates);
  }

  @Override
  public @NotNull ModCommand perform(@NotNull ActionContext actionContext) {
    PsiElement element = actionContext.findLeaf();
    List<ExpressionToConstantComparison> comparisons = comparisons(element).toList();
    if (comparisons.size() < 2) return ModCommand.nop();
    PsiClass containingClass = ClassUtils.getContainingStaticClass(element);
    if (containingClass == null) return ModCommand.nop();
    List<ExpressionToConstantReplacementContext> copies =
      myProcessDuplicates == ThreeState.NO ? List.of() : findCopies(comparisons, containingClass);
    if (myProcessDuplicates == ThreeState.UNSURE && !copies.isEmpty()) {
      return ModCommand.chooseAction(JavaBundle.message("intention.extract.set.from.comparison.chain.popup.title"),
                                     new ExtractSetFromComparisonChainAction(false), new ExtractSetFromComparisonChainAction(true));
    }
    LinkedHashSet<String> suggestions = getSuggestions(comparisons);

    return ModCommand.psiUpdate(containingClass, (cls, updater) -> {
      Project project = cls.getProject();
      ExpressionToConstantReplacementContext context = new ExpressionToConstantReplacementContext(comparisons).getWritable(updater);
      List<ExpressionToConstantReplacementContext> writableCopies = ContainerUtil.map(copies, copy -> copy.getWritable(updater));

      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      JavaCodeStyleManager manager = JavaCodeStyleManager.getInstance(project);
      String name = manager.suggestUniqueVariableName(suggestions.iterator().next(), cls, false);
      String fieldInitializer = context.myInitializer;
      PsiType elementType = context.myType;
      if (elementType == null) return;
      String pattern = getInitializer(elementType, cls);
      String initializer = MessageFormat.format(pattern, fieldInitializer, elementType.getCanonicalText());
      String modifiers = cls.isInterface() ? "" : "private static final ";
      String type = CommonClassNames.JAVA_UTIL_SET +
                    (PsiUtil.isLanguageLevel5OrHigher(cls) ? "<" + elementType.getCanonicalText() + ">" : "");
      PsiField field = factory.createFieldFromText(modifiers + type + " " + name + "=" + initializer + ";", cls);
      field = (PsiField)cls.add(field);
      RemoveRedundantTypeArgumentsUtil.removeRedundantTypeArguments(field);
      CodeStyleManager.getInstance(project).reformat(manager.shortenClassReferences(field));

      context.replace(field);
      for (ExpressionToConstantReplacementContext copy : writableCopies) {
        copy.replace(field);
      }
      updater.rename(field, List.copyOf(suggestions));
    });
  }

  private static List<ExpressionToConstantReplacementContext> findCopies(@NotNull List<ExpressionToConstantComparison> comparisons,
                                                                         @NotNull PsiClass aClass) {
    Set<String> orig = StreamEx.of(comparisons).map(c -> c.myConstantRepresentation).toSet();
    List<ExpressionToConstantReplacementContext> copies = new ArrayList<>();
    Set<PsiExpression> processedOperands = new HashSet<>();
    aClass.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitPolyadicExpression(@NotNull PsiPolyadicExpression expression) {
        super.visitPolyadicExpression(expression);
        if (!expression.getOperationTokenType().equals(JavaTokenType.OROR)) return;
        for (PsiExpression operand : expression.getOperands()) {
          if (processedOperands.contains(operand)) continue;
          List<ExpressionToConstantComparison> otherComparisons = comparisons(operand).toList();
          otherComparisons.stream().map(c -> c.myComparison).forEach(processedOperands::add);
          if (otherComparisons.size() == comparisons.size() &&
              otherComparisons.get(0).myExpression != comparisons.get(0).myExpression &&
              otherComparisons.get(0).myType.equals(comparisons.get(0).myType)
              && StreamEx.of(otherComparisons).map(c -> c.myConstantRepresentation).toSet().equals(orig)) {
            copies.add(new ExpressionToConstantReplacementContext(otherComparisons));
          }
        }
      }
    });
    return copies;
  }

  @NotNull
  String getInitializer(PsiType type, PsiClass containingClass) {
    if (!type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
      return INITIALIZER_ENUM_SET;
    }
    if (PsiUtil.isLanguageLevel9OrHigher(containingClass)) {
      return INITIALIZER_FORMAT_JAVA9;
    }
    if (JavaPsiFacade.getInstance(containingClass.getProject()).findClass(GUAVA_IMMUTABLE_SET, containingClass.getResolveScope()) != null) {
      return INITIALIZER_FORMAT_GUAVA;
    }
    if (PsiUtil.isLanguageLevel5OrHigher(containingClass)) {
      return INITIALIZER_FORMAT_JAVA5;
    }
    return INITIALIZER_FORMAT_JAVA2;
  }

  @Override
  public @Nullable Presentation getPresentation(@NotNull ActionContext actionContext) {
    if (!BaseIntentionAction.canModify(actionContext.file())) return null;
    PsiElement element = actionContext.findLeaf();
    List<ExpressionToConstantComparison> comparisons = comparisons(element).toList();
    if (comparisons.size() <= 1) return null;
    return switch (myProcessDuplicates) {
      case UNSURE -> Presentation.of(getFamilyName());
      case YES -> {
        PsiClass containingClass = ClassUtils.getContainingStaticClass(element);
        if (containingClass == null) yield null;
        ExpressionToConstantReplacementContext context = new ExpressionToConstantReplacementContext(comparisons);
        List<ExpressionToConstantReplacementContext> copies = findCopies(comparisons, containingClass);
        TextRange[] ranges =
          StreamEx.of(copies).append(context).map(ExpressionToConstantReplacementContext::range).toArray(TextRange.EMPTY_ARRAY);
        yield Presentation.of(JavaBundle.message("intention.extract.set.from.comparison.chain.replace.all")).withHighlighting(ranges);
      }
      case NO -> {
        ExpressionToConstantReplacementContext context = new ExpressionToConstantReplacementContext(comparisons);
        yield Presentation.of(JavaBundle.message("intention.extract.set.from.comparison.chain.replace.only.this"))
          .withHighlighting(context.range());
      }
    };
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return JavaBundle.message("intention.extract.set.from.comparison.chain.family");
  }

  @NotNull
  private static LinkedHashSet<String> getSuggestions(List<ExpressionToConstantComparison> comparisons) {
    PsiExpression stringExpression = comparisons.get(0).myExpression;
    Project project = stringExpression.getProject();
    JavaCodeStyleManager manager = JavaCodeStyleManager.getInstance(project);
    SuggestedNameInfo info = manager.suggestVariableName(VariableKind.STATIC_FINAL_FIELD, null, stringExpression,
                                                         comparisons.get(0).myType, false);
    // Suggestions like OBJECT and AN_OBJECT appear because Object.equals argument type is an Object,
    // such names are rarely appropriate
    LinkedHashSet<String> suggestions =
      StreamEx.of(info.names).without("OBJECT", "AN_OBJECT").map(StringUtil::pluralize).nonNull().toCollection(LinkedHashSet::new);
    Pair<String, String> prefixSuffix = comparisons.stream().map(cmp -> cmp.myConstantRepresentation).collect(
      MoreCollectors.pairing(MoreCollectors.commonPrefix(), MoreCollectors.commonSuffix(), Pair::create));
    StreamEx.of(prefixSuffix.first, prefixSuffix.second).flatMap(str -> StreamEx.split(str, "\\W+").limit(3))
      .map(str -> str.replaceFirst("^_+", "").replaceFirst("_+$", ""))
      .filter(str -> str.length() >= 3 && StringUtil.isJavaIdentifier(str))
      .flatMap(str -> StreamEx.of(manager.suggestVariableName(VariableKind.STATIC_FINAL_FIELD, str, null, null).names))
      .limit(5)
      .map(StringUtil::pluralize)
      .forEach(suggestions::add);
    if(comparisons.get(0).myType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
      suggestions.add("STRINGS");
    }
    return suggestions;
  }

  private static StreamEx<ExpressionToConstantComparison> comparisons(PsiElement element) {
    PsiPolyadicExpression disjunction = PsiTreeUtil.getParentOfType(element, PsiPolyadicExpression.class);
    if (disjunction != null && disjunction.getOperationTokenType() == JavaTokenType.EQEQ) {
      disjunction = PsiTreeUtil.getParentOfType(disjunction, PsiPolyadicExpression.class);
    }
    if (disjunction == null || disjunction.getOperationTokenType() != JavaTokenType.OROR) return StreamEx.empty();
    PsiExpression[] operands = disjunction.getOperands();
    int offset = element.getTextOffset() - disjunction.getTextOffset();
    int index = IntStreamEx.ofIndices(operands, op -> op.getStartOffsetInParent() + op.getTextLength() > offset)
      .findFirst().orElse(operands.length - 1);
    ExpressionToConstantComparison anchorComparison = ExpressionToConstantComparison.create(operands[index]);
    if (anchorComparison == null) return StreamEx.empty();
    List<ExpressionToConstantComparison> prefix = IntStreamEx.rangeClosed(index - 1, 0, -1)
      .elements(operands)
      .map(ExpressionToConstantComparison::create)
      .takeWhile(anchorComparison::belongsToChain)
      .toList();
    List<ExpressionToConstantComparison> suffix = StreamEx.of(operands, index + 1, operands.length)
      .map(ExpressionToConstantComparison::create)
      .takeWhile(anchorComparison::belongsToChain)
      .toList();
    return StreamEx.ofReversed(prefix).append(anchorComparison).append(suffix);
  }

  static final class ExpressionToConstantReplacementContext {
    final @NotNull PsiExpression myExpression;
    final @NotNull PsiExpression myFirstComparison;
    final @NotNull PsiExpression myLastComparison;
    final PsiType myType;
    final String myInitializer;

    ExpressionToConstantReplacementContext(List<ExpressionToConstantComparison> comparisons) {
      assert !comparisons.isEmpty();
      myExpression = comparisons.get(0).myExpression;
      myFirstComparison = comparisons.get(0).myComparison;
      myLastComparison = comparisons.get(comparisons.size() - 1).myComparison;
      myType = comparisons.get(0).myType;
      myInitializer = StreamEx.of(comparisons).map(cmp -> cmp.myConstant.getText()).joining(",");
    }

    private ExpressionToConstantReplacementContext(ExpressionToConstantReplacementContext context, ModPsiUpdater updater) {
      myExpression = updater.getWritable(context.myExpression);
      myFirstComparison = updater.getWritable(context.myFirstComparison);
      myLastComparison = updater.getWritable(context.myLastComparison);
      myType = context.myType;
      myInitializer = context.myInitializer;
    }

    @NotNull TextRange range() {
      int start = myFirstComparison.getTextRange().getStartOffset();
      int end = myLastComparison.getTextRange().getEndOffset();
      return TextRange.create(start, end);
    }

    @NotNull ExpressionToConstantReplacementContext getWritable(@NotNull ModPsiUpdater updater) {
      return new ExpressionToConstantReplacementContext(this, updater);
    }

    @Nullable
    private PsiElement replace(@NotNull PsiField field) {
      Project project = field.getProject();
      PsiClass containingClass = field.getContainingClass();
      if (containingClass == null) return null;
      String name = field.getName();
      PsiExpression disjunction = tryCast(myFirstComparison.getParent(), PsiPolyadicExpression.class);
      if (disjunction == null) return null;
      int startOffset = myFirstComparison.getStartOffsetInParent();
      int endOffset = myLastComparison.getStartOffsetInParent() + myLastComparison.getTextLength();
      String origText = disjunction.getText();
      String fieldReference = PsiResolveHelper.getInstance(project).resolveReferencedVariable(name, disjunction) == field ?
                              name : containingClass.getQualifiedName() + "." + name;
      String replacementText = origText.substring(0, startOffset) +
                               fieldReference + ".contains(" + myExpression.getText() + ")" +
                               origText.substring(endOffset);
      PsiExpression replacement = JavaPsiFacade.getElementFactory(project).createExpressionFromText(replacementText, disjunction);
      if (replacement instanceof PsiMethodCallExpression && disjunction.getParent() instanceof PsiParenthesizedExpression) {
        disjunction = (PsiExpression)disjunction.getParent();
      }
      return disjunction.replace(replacement);
    }
  }

  static final class ExpressionToConstantComparison {
    @NotNull final PsiExpression myComparison;
    @NotNull final PsiExpression myExpression;
    @NotNull final PsiExpression myConstant;
    @NotNull final PsiType myType;
    @NotNull final String myConstantRepresentation;

    ExpressionToConstantComparison(@NotNull PsiExpression comparison,
                                   @NotNull PsiExpression expression,
                                   @NotNull PsiExpression constant,
                                   @NotNull String constantRepresentation) {
      myComparison = comparison;
      myExpression = expression;
      myConstant = constant;
      myType = Objects.requireNonNull(constant.getType());
      myConstantRepresentation = constantRepresentation;
    }

    boolean belongsToChain(@Nullable ExpressionToConstantComparison other) {
      return other != null && PsiEquivalenceUtil.areElementsEquivalent(myExpression, other.myExpression) && myType.equals(other.myType);
    }

    static ExpressionToConstantComparison create(PsiExpression candidate) {
      candidate = PsiUtil.skipParenthesizedExprDown(candidate);
      EqualityCheck check = EqualityCheck.from(candidate);
      if (check != null) {
        return fromComparison(candidate, check.getLeft(), check.getRight());
      }
      PsiBinaryExpression binOp = tryCast(candidate, PsiBinaryExpression.class);
      if (binOp != null && JavaTokenType.EQEQ.equals(binOp.getOperationTokenType())) {
        return fromComparison(candidate, binOp.getLOperand(), binOp.getROperand());
      }
      return null;
    }

    @Nullable
    private static ExpressionToConstantComparison fromComparison(PsiExpression candidate, PsiExpression left, PsiExpression right) {
      if (left == null || right == null) return null;
      ExpressionToConstantComparison fromLeft = tryExtract(candidate, left, right);
      if (fromLeft != null) return fromLeft;
      return tryExtract(candidate, right, left);
    }

    @Nullable
    private static ExpressionToConstantComparison tryExtract(PsiExpression candidate, PsiExpression constant, PsiExpression nonConstant) {
      String constantValue = tryCast(ExpressionUtils.computeConstantExpression(constant), String.class);
      if (constantValue != null) {
        return new ExpressionToConstantComparison(candidate, nonConstant, constant, constantValue);
      }
      PsiReferenceExpression ref = tryCast(PsiUtil.skipParenthesizedExprDown(constant), PsiReferenceExpression.class);
      if (ref != null) {
        PsiEnumConstant enumConstant = tryCast(ref.resolve(), PsiEnumConstant.class);
        if (enumConstant != null) {
          return new ExpressionToConstantComparison(candidate, nonConstant, ref, enumConstant.getName());
        }
      }
      return null;
    }
  }
}
