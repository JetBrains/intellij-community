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
    "(" + CommonClassNames.JAVA_UTIL_ARRAYS + ".asList(new String[] '{'{0}'}')))";
  private static final String INITIALIZER_FORMAT_JAVA5 =
    CommonClassNames.JAVA_UTIL_COLLECTIONS + ".unmodifiableSet(" +
    "new " + CommonClassNames.JAVA_UTIL_HASH_SET + "<" + CommonClassNames.JAVA_LANG_STRING + ">" +
    "(" + CommonClassNames.JAVA_UTIL_ARRAYS + ".asList({0})))";
  private static final String INITIALIZER_FORMAT_JAVA9 = CommonClassNames.JAVA_UTIL_SET + ".of({0})";

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    List<StringToConstantComparison> comparisons = comparisons(element).toList();
    if (comparisons.size() < 2) return;
    PsiExpression firstComparison = comparisons.get(0).myComparison;
    PsiExpression lastComparison = comparisons.get(comparisons.size() - 1).myComparison;
    PsiExpression disjunction = ObjectUtils.tryCast(firstComparison.getParent(), PsiPolyadicExpression.class);
    if (disjunction == null) return;
    PsiExpression stringExpression = comparisons.get(0).myStringExpression;
    PsiClass containingClass = ClassUtils.getContainingStaticClass(disjunction);
    if (containingClass == null) return;
    JavaCodeStyleManager manager = JavaCodeStyleManager.getInstance(project);
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    LinkedHashSet<String> suggestions = getSuggestions(comparisons);
    String name = manager.suggestUniqueVariableName(suggestions.iterator().next(), containingClass, false);
    String fieldInitializer = StreamEx.of(comparisons).map(cmp -> cmp.myConstant.getText()).joining(",");
    String pattern = getInitializer(containingClass);
    String initializer = MessageFormat.format(pattern, fieldInitializer);
    String modifiers = containingClass.isInterface() ? "" : "private static final ";
    String type = CommonClassNames.JAVA_UTIL_SET +
                  (PsiUtil.isLanguageLevel5OrHigher(containingClass) ? "<" + CommonClassNames.JAVA_LANG_STRING + ">" : "");
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
  String getInitializer(PsiClass containingClass) {
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
  private static LinkedHashSet<String> getSuggestions(List<StringToConstantComparison> comparisons) {
    PsiExpression stringExpression = comparisons.get(0).myStringExpression;
    Project project = stringExpression.getProject();
    JavaCodeStyleManager manager = JavaCodeStyleManager.getInstance(project);
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    SuggestedNameInfo info = manager.suggestVariableName(VariableKind.STATIC_FINAL_FIELD, null, stringExpression,
                                                         factory.createTypeFromText(CommonClassNames.JAVA_LANG_STRING, stringExpression),
                                                         false);
    // Suggestions like OBJECT and AN_OBJECT appear because Object.equals argument type is an Object,
    // such names are rarely appropriate
    LinkedHashSet<String> suggestions =
      StreamEx.of(info.names).without("OBJECT", "AN_OBJECT").map(StringUtil::pluralize).nonNull().toCollection(LinkedHashSet::new);
    Pair<String, String> prefixSuffix = comparisons.stream().map(cmp -> cmp.myComputedConstant).collect(
      MoreCollectors.pairing(MoreCollectors.commonPrefix(), MoreCollectors.commonSuffix(), Pair::create));
    StreamEx.of(prefixSuffix.first, prefixSuffix.second).flatMap(str -> StreamEx.split(str, "\\W+").limit(3))
      .filter(str -> str.length() >= 3 && StringUtil.isJavaIdentifier(str))
      .flatMap(str -> StreamEx.of(manager.suggestVariableName(VariableKind.STATIC_FINAL_FIELD, str, null, null).names))
      .limit(5)
      .map(StringUtil::pluralize)
      .forEach(suggestions::add);
    suggestions.add("STRINGS");
    return suggestions;
  }

  private static StreamEx<StringToConstantComparison> comparisons(PsiElement element) {
    PsiPolyadicExpression disjunction = PsiTreeUtil.getParentOfType(element, PsiPolyadicExpression.class);
    if (disjunction == null || disjunction.getOperationTokenType() != JavaTokenType.OROR) return StreamEx.empty();
    PsiExpression[] operands = disjunction.getOperands();
    int offset = element.getTextOffset() - disjunction.getTextOffset();
    int index = IntStreamEx.ofIndices(operands, op -> op.getStartOffsetInParent() + op.getTextLength() > offset)
      .findFirst().orElse(operands.length - 1);
    StringToConstantComparison anchorComparison = StringToConstantComparison.create(operands[index]);
    if (anchorComparison == null) return StreamEx.empty();
    List<StringToConstantComparison> prefix = IntStreamEx.rangeClosed(index - 1, 0, -1)
      .elements(operands)
      .map(StringToConstantComparison::create)
      .takeWhile(anchorComparison::sameStringExpression)
      .toList();
    List<StringToConstantComparison> suffix = StreamEx.of(operands, index + 1, operands.length)
      .map(StringToConstantComparison::create)
      .takeWhile(anchorComparison::sameStringExpression)
      .toList();
    return StreamEx.ofReversed(prefix).append(anchorComparison).append(suffix);
  }

  static final class StringToConstantComparison {
    @NotNull PsiExpression myComparison;
    @NotNull PsiExpression myStringExpression;
    @NotNull PsiExpression myConstant;
    @NotNull String myComputedConstant;

    StringToConstantComparison(@NotNull PsiExpression comparison,
                               @NotNull PsiExpression stringExpression,
                               @NotNull PsiExpression constant,
                               @NotNull String computedConstant) {
      myComparison = comparison;
      myStringExpression = stringExpression;
      myConstant = constant;
      myComputedConstant = computedConstant;
    }

    boolean sameStringExpression(@Nullable StringToConstantComparison other) {
      return other != null && PsiEquivalenceUtil.areElementsEquivalent(myStringExpression, other.myStringExpression);
    }

    static StringToConstantComparison create(PsiExpression candidate) {
      PsiMethodCallExpression call = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(candidate), PsiMethodCallExpression.class);
      if (call == null) return null;
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

    @Nullable
    private static StringToConstantComparison fromComparison(PsiExpression candidate,
                                                             PsiExpression left,
                                                             PsiExpression right) {
      if (left == null || right == null) return null;
      String leftConstant = ObjectUtils.tryCast(ExpressionUtils.computeConstantExpression(left), String.class);
      if (leftConstant != null) {
        return new StringToConstantComparison(candidate, right, left, leftConstant);
      }
      String rightConstant = ObjectUtils.tryCast(ExpressionUtils.computeConstantExpression(right), String.class);
      if (rightConstant != null) {
        return new StringToConstantComparison(candidate, left, right, rightConstant);
      }
      return null;
    }
  }
}
