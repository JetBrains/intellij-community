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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.IncorrectOperationException;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

public class WrapWithAdapterMethodCallFix extends LocalQuickFixAndIntentionActionOnPsiElement implements HighPriorityAction {
  static class Wrapper extends ArgumentFixerActionFactory {
    final Predicate<PsiType> myInTypeFilter;
    final Predicate<PsiType> myOutTypeFilter;
    final String myTemplate;

    /**
     * @param template      template for replacement (original expression is referenced as {@code {0}})
     * @param inTypeFilter  filter for input type (must return true if supplied type is acceptable as input type for this wrapper)
     * @param outTypeFilter quick filter for output type (must return true if supplied output type is acceptable for this wrapper).
     *                      It's allowed to check imprecisely (return true even if output type is not acceptable) as more
     *                      expensive type check will be performed automatically.
     */
    Wrapper(String template, Predicate<PsiType> inTypeFilter, Predicate<PsiType> outTypeFilter) {
      myInTypeFilter = inTypeFilter;
      myOutTypeFilter = outTypeFilter;
      myTemplate = template;
    }

    boolean isApplicable(PsiElement context, PsiType inType, PsiType outType) {
      if (inType == null ||
          outType == null ||
          inType.equals(PsiType.NULL) ||
          !myInTypeFilter.test(inType) ||
          !myOutTypeFilter.test(outType)) {
        return false;
      }
      PsiType resultType =
        createReplacement(context, "((" + GenericsUtil.getVariableTypeByExpressionType(inType).getCanonicalText() + ")null)").getType();
      return resultType != null && outType.isAssignableFrom(resultType);
    }

    @NotNull
    private PsiExpression createReplacement(PsiElement context, String replacement) {
      return JavaPsiFacade.getElementFactory(context.getProject()).createExpressionFromText(
        myTemplate.replace("{0}", replacement), context);
    }

    @Nullable
    @Override
    protected PsiExpression getModifiedArgument(final PsiExpression expression, final PsiType toType) throws IncorrectOperationException {
      if (isApplicable(expression, expression.getType(), toType)) {
        return (PsiExpression)JavaCodeStyleManager.getInstance(expression.getProject())
          .shortenClassReferences(createReplacement(expression, expression.getText()));
      }
      return null;
    }

    @Override
    public boolean areTypesConvertible(@NotNull final PsiType exprType,
                                       @NotNull final PsiType parameterType,
                                       @NotNull final PsiElement context) {
      return parameterType.isConvertibleFrom(exprType) || isApplicable(context, exprType, parameterType);
    }

    @Override
    public MethodArgumentFix createFix(final PsiExpressionList list, final int i, final PsiType toType) {
      return new MyMethodArgumentFix(list, i, toType, this);
    }

    public String toString() {
      return myTemplate.replace("{0}", "").replaceAll("\\b[a-z.]+\\.", "");
    }
  }

  private static final Wrapper[] WRAPPERS = {
    new Wrapper("new java.io.File({0})",
                inType -> inType.equalsToText(CommonClassNames.JAVA_LANG_STRING),
                outType -> outType.equalsToText(CommonClassNames.JAVA_IO_FILE)),
    new Wrapper("java.nio.file.Paths.get({0})",
                inType -> inType.equalsToText(CommonClassNames.JAVA_LANG_STRING),
                outType -> outType.equalsToText("java.nio.file.Path")),
    new Wrapper("java.util.Arrays.asList({0})",
                inType -> inType instanceof PsiArrayType && ((PsiArrayType)inType).getComponentType() instanceof PsiClassType,
                outType -> InheritanceUtil.isInheritor(outType, CommonClassNames.JAVA_LANG_ITERABLE)),
    new Wrapper("java.lang.Math.toIntExact({0})",
                inType -> PsiType.LONG.equals(inType) || inType.equalsToText(CommonClassNames.JAVA_LANG_LONG),
                outType -> PsiType.INT.equals(outType) || outType.equalsToText(CommonClassNames.JAVA_LANG_INTEGER)),
    new Wrapper("java.util.Collections.singleton({0})",
                inType -> true,
                outType -> InheritanceUtil.isInheritor(outType, CommonClassNames.JAVA_LANG_ITERABLE)),
    new Wrapper("java.util.Collections.singletonList({0})",
                inType -> true,
                outType -> outType instanceof PsiClassType &&
                           ((PsiClassType)outType).rawType().equalsToText(CommonClassNames.JAVA_UTIL_LIST)),
    new Wrapper("java.util.Arrays.stream({0})",
                inType -> inType instanceof PsiArrayType,
                outType -> InheritanceUtil.isInheritor(outType, CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM))
  };

  @Nullable private final PsiType myType;
  @Nullable private final Wrapper myWrapper;

  public WrapWithAdapterMethodCallFix(@Nullable PsiType type, @NotNull PsiExpression expression) {
    super(expression);
    myType = type;
    myWrapper = StreamEx.of(WRAPPERS).findFirst(w -> w.isApplicable(expression, expression.getType(), type)).orElse(null);
  }

  @Nls
  @NotNull
  @Override
  public String getText() {
    return QuickFixBundle.message("wrap.with.adapter.text", myWrapper);
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("wrap.with.adapter.call.family.name");
  }


  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    return myType != null &&
           myWrapper != null &&
           myType.isValid() &&
           startElement.isValid() &&
           startElement.getManager().isInProject(startElement);
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(startElement.replace(getModifiedExpression(startElement)));
  }

  private PsiExpression getModifiedExpression(@NotNull PsiElement expression) {
    assert myWrapper != null;
    return myWrapper.createReplacement(expression, expression.getText());
  }

  private static class MyMethodArgumentFix extends MethodArgumentFix implements HighPriorityAction {

    protected MyMethodArgumentFix(@NotNull PsiExpressionList list,
                                  int i,
                                  @NotNull PsiType toType,
                                  @NotNull Wrapper fixerActionFactory) {
      super(list, i, toType, fixerActionFactory);
    }

    @Nls
    @NotNull
    @Override
    public String getText() {
      return myArgList.getExpressions().length == 1
             ? QuickFixBundle.message("wrap.with.adapter.parameter.single.text", myArgumentFixerActionFactory)
             : QuickFixBundle.message("wrap.with.adapter.parameter.multiple.text", myIndex + 1, myArgumentFixerActionFactory);
    }
  }

  public static void registerCastActions(@NotNull CandidateInfo[] candidates,
                                         @NotNull PsiCall call,
                                         HighlightInfo highlightInfo,
                                         final TextRange fixRange) {
    for (Wrapper wrapper : WRAPPERS) {
      wrapper.registerCastActions(candidates, call, highlightInfo, fixRange);
    }
  }
}
