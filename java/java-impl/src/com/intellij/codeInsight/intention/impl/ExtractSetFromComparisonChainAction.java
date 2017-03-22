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
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import one.util.streamex.IntStreamEx;
import one.util.streamex.MoreCollectors;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.text.MessageFormat;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * @author Tagir Valeev
 */
public class ExtractSetFromComparisonChainAction extends PsiElementBaseIntentionAction {
  private static final String INITIALIZER_FORMAT_JAVA8 = "new " +
                                                         CommonClassNames.JAVA_UTIL_HASH_SET +
                                                         "<" +
                                                         CommonClassNames.JAVA_LANG_STRING +
                                                         ">(" +
                                                         CommonClassNames.JAVA_UTIL_ARRAYS +
                                                         ".asList({0}))";
  private static final String INITIALIZER_FORMAT_JAVA9 = CommonClassNames.JAVA_UTIL_SET + ".of({0})";

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    List<PsiExpression> disjuncts = disjuncts(element).toList();
    if (disjuncts.size() < 2) return;
    PsiExpression disjunction = ObjectUtils.tryCast(disjuncts.get(0).getParent(), PsiPolyadicExpression.class);
    if (disjunction == null) return;
    PsiExpression stringExpression = getValueComparedToString(disjuncts.get(0));
    if (stringExpression == null) return;
    PsiClass containingClass = ClassUtils.getContainingStaticClass(disjunction);
    if (containingClass == null) return;
    JavaCodeStyleManager manager = JavaCodeStyleManager.getInstance(project);
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    LinkedHashSet<String> suggestions = getSuggestions(disjuncts, stringExpression);
    String name = manager.suggestUniqueVariableName(suggestions.iterator().next(), containingClass, false);
    String fieldInitializer = qualifiers(disjuncts).map(PsiElement::getText).joining(",");
    String pattern = PsiUtil.isLanguageLevel9OrHigher(containingClass) ? INITIALIZER_FORMAT_JAVA9 : INITIALIZER_FORMAT_JAVA8;
    String initializer = MessageFormat.format(pattern, fieldInitializer);
    PsiField field = factory.createFieldFromText("private static final " +
                                                 CommonClassNames.JAVA_UTIL_SET + "<" + CommonClassNames.JAVA_LANG_STRING + "> " +
                                                 name + "=" + initializer + ";", containingClass);
    field = (PsiField)containingClass.add(field);
    PsiDiamondTypeUtil.removeRedundantTypeArguments(field);
    CodeStyleManager.getInstance(project).reformat(manager.shortenClassReferences(field));

    int startOffset = disjuncts.get(0).getStartOffsetInParent();
    int endOffset = disjuncts.get(disjuncts.size() - 1).getStartOffsetInParent() + disjuncts.get(disjuncts.size() - 1).getTextLength();
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

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    return PsiUtil.isLanguageLevel5OrHigher(element) && disjuncts(element).count() > 1;
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
  private static LinkedHashSet<String> getSuggestions(List<PsiExpression> disjuncts, PsiExpression stringExpression) {
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
    Pair<String, String> prefixSuffix = qualifiers(disjuncts).map(ExpressionUtils::computeConstantExpression).select(String.class).collect(
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

  private static StreamEx<PsiExpression> qualifiers(List<PsiExpression> expressions) {
    return StreamEx.of(expressions).map(PsiUtil::skipParenthesizedExprDown).select(PsiMethodCallExpression.class)
      .map(call -> call.getMethodExpression().getQualifierExpression()).nonNull();
  }

  private static StreamEx<PsiExpression> disjuncts(PsiElement element) {
    PsiPolyadicExpression disjunction = PsiTreeUtil.getParentOfType(element, PsiPolyadicExpression.class);
    if (disjunction == null || disjunction.getOperationTokenType() != JavaTokenType.OROR) return StreamEx.empty();
    PsiExpression[] operands = disjunction.getOperands();
    int offset = element.getTextOffset() - disjunction.getTextOffset();
    int index = IntStreamEx.ofIndices(operands, op -> op.getStartOffsetInParent() + op.getTextLength() > offset)
      .findFirst().orElse(operands.length - 1);
    PsiExpression comparedValue = getValueComparedToString(operands[index]);
    if (comparedValue == null) return StreamEx.empty();
    EquivalenceChecker checker = EquivalenceChecker.getCanonicalPsiEquivalence();
    List<PsiExpression> prefix = IntStreamEx.rangeClosed(index - 1, 0, -1)
      .elements(operands)
      .takeWhile(op -> checker.expressionsAreEquivalent(getValueComparedToString(op), comparedValue))
      .toList();
    List<PsiExpression> suffix = StreamEx.of(operands, index + 1, operands.length)
      .takeWhile(op -> checker.expressionsAreEquivalent(getValueComparedToString(op), comparedValue))
      .toList();
    return StreamEx.ofReversed(prefix).append(operands[index]).append(suffix);
  }

  private static PsiExpression getValueComparedToString(PsiExpression expression) {
    PsiMethodCallExpression call = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(expression), PsiMethodCallExpression.class);
    if (call == null || !MethodCallUtils.isEqualsCall(call)) return null;
    if (!(ExpressionUtils.computeConstantExpression(call.getMethodExpression().getQualifierExpression()) instanceof String)) return null;
    return ArrayUtil.getFirstElement(call.getArgumentList().getExpressions());
  }
}
