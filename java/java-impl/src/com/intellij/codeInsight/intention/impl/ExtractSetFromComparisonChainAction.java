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
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.rename.inplace.MemberInplaceRenamer;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import one.util.streamex.IntStreamEx;
import one.util.streamex.MoreCollectors;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * @author Tagir Valeev
 */
public class ExtractSetFromComparisonChainAction extends PsiElementBaseIntentionAction {
  private static final CallMatcher OBJECT_EQUALS = CallMatcher.anyOf(
    CallMatcher.staticCall("java.util.Objects", "equals").parameterCount(2),
    CallMatcher.staticCall("com.google.common.base.Objects", "equal").parameterCount(2));

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

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    List<ExpressionToConstantComparison> comparisons = comparisons(element).toList();
    if (comparisons.size() < 2) return;
    PsiExpression firstComparison = comparisons.get(0).myComparison;
    PsiExpression lastComparison = comparisons.get(comparisons.size() - 1).myComparison;
    PsiExpression disjunction = ObjectUtils.tryCast(firstComparison.getParent(), PsiPolyadicExpression.class);
    if (disjunction == null) return;
    PsiExpression stringExpression = comparisons.get(0).myExpression;
    PsiClass containingClass = ClassUtils.getContainingStaticClass(disjunction);
    if (containingClass == null) return;
    JavaCodeStyleManager manager = JavaCodeStyleManager.getInstance(project);
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    LinkedHashSet<String> suggestions = getSuggestions(comparisons);
    String name = manager.suggestUniqueVariableName(suggestions.iterator().next(), containingClass, false);
    String fieldInitializer = StreamEx.of(comparisons).map(cmp -> cmp.myConstant.getText()).joining(",");
    String pattern = getInitializer(comparisons.get(0).myType, containingClass);
    String elementType = comparisons.get(0).myType.getCanonicalText();
    String initializer = MessageFormat.format(pattern, fieldInitializer, elementType);
    String modifiers = containingClass.isInterface() ? "" : "private static final ";
    String type = CommonClassNames.JAVA_UTIL_SET +
                  (PsiUtil.isLanguageLevel5OrHigher(containingClass) ? "<" + elementType + ">" : "");
    PsiField field = factory.createFieldFromText(modifiers + type + " " +
                                                 name + "=" + initializer + ";", containingClass);
    field = (PsiField)containingClass.add(field);
    PsiDiamondTypeUtil.removeRedundantTypeArguments(field);
    CodeStyleManager.getInstance(project).reformat(manager.shortenClassReferences(field));

    int startOffset = firstComparison.getStartOffsetInParent();
    int endOffset = lastComparison.getStartOffsetInParent() + lastComparison.getTextLength();
    String origText = disjunction.getText();
    String fieldReference = PsiResolveHelper.SERVICE.getInstance(project).resolveReferencedVariable(name, disjunction) == field ?
                            name : containingClass.getQualifiedName() + "." + name;
    String replacementText = origText.substring(0, startOffset) +
                             fieldReference + ".contains(" + stringExpression.getText() + ")" +
                             origText.substring(endOffset);
    PsiExpression replacement = factory.createExpressionFromText(replacementText, disjunction);
    if (replacement instanceof PsiMethodCallExpression && disjunction.getParent() instanceof PsiParenthesizedExpression) {
      disjunction = (PsiExpression)disjunction.getParent();
    }
    PsiElement result = disjunction.replace(replacement);

    PsiReferenceExpression fieldRef =
      ObjectUtils.tryCast(ReferencesSearch.search(field, new LocalSearchScope(result)).findFirst(), PsiReferenceExpression.class);
    if (fieldRef == null) return;

    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.getDocument());
    editor.getCaretModel().moveToOffset(fieldRef.getTextOffset());
    editor.getSelectionModel().removeSelection();
    new MemberInplaceRenamer(field, field, editor).performInplaceRefactoring(suggestions);
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
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    return comparisons(element).count() > 1;
  }

  @NotNull
  @Override
  public String getText() {
    return getFamilyName();
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.extract.set.from.comparison.chain.family");
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
      PsiMethodCallExpression call = ObjectUtils.tryCast(candidate, PsiMethodCallExpression.class);
      if (call != null) {
        if (MethodCallUtils.isEqualsCall(call)) {
          PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
          PsiExpression argument = ArrayUtil.getFirstElement(call.getArgumentList().getExpressions());
          return fromComparison(candidate, qualifier, argument);
        }
        if (OBJECT_EQUALS.test(call)) {
          PsiExpression[] arguments = call.getArgumentList().getExpressions();
          return fromComparison(candidate, arguments[0], arguments[1]);
        }
        return null;
      }
      PsiBinaryExpression binOp = ObjectUtils.tryCast(candidate, PsiBinaryExpression.class);
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
      String constantValue = ObjectUtils.tryCast(ExpressionUtils.computeConstantExpression(constant), String.class);
      if (constantValue != null) {
        return new ExpressionToConstantComparison(candidate, nonConstant, constant, constantValue);
      }
      PsiReferenceExpression ref = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(constant), PsiReferenceExpression.class);
      if (ref != null) {
        PsiEnumConstant enumConstant = ObjectUtils.tryCast(ref.resolve(), PsiEnumConstant.class);
        if (enumConstant != null && enumConstant.getName() != null) {
          return new ExpressionToConstantComparison(candidate, nonConstant, ref, enumConstant.getName());
        }
      }
      return null;
    }
  }
}
