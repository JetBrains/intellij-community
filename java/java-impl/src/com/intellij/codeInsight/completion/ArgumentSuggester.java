// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.TypedLookupItem;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.TypeUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static com.intellij.codeInsight.completion.ReferenceExpressionCompletionContributor.getSpace;

class ArgumentSuggester {
  static @NotNull Collection<LookupElement> suggestArgument(PsiReferenceExpression ref, List<ExpectedTypeInfo> expectedTypeInfos) {
    PsiExpression qualifier = ref.getQualifierExpression();

    if (qualifier == null) return Collections.emptyList();
    PsiType type = qualifier.getType();
    if (!TypeUtils.isJavaLangString(type) || !PsiUtil.isLanguageLevel7OrHigher(ref)) return Collections.emptyList();
    PsiClass stringClass = PsiUtil.resolveClassInClassTypeOnly(type);
    if (stringClass == null) return Collections.emptyList();
    PsiMethod toLowerCase = findMethod(stringClass, "toLowerCase", "java.util.Locale");
    PsiMethod toUpperCase = findMethod(stringClass, "toUpperCase", "java.util.Locale");
    PsiMethod getBytes = findMethod(stringClass, "getBytes", "java.nio.charset.Charset");
    if (toLowerCase == null || toUpperCase == null || getBytes == null) return Collections.emptyList();
    StreamEx<MethodWithArgument> stream = StreamEx.of(
      new MethodWithArgument(toLowerCase, type, "java.util.Locale.ROOT", "Locale.ROOT"),
      new MethodWithArgument(toUpperCase, type, "java.util.Locale.ROOT", "Locale.ROOT"),
      new MethodWithArgument(getBytes, PsiType.BYTE.createArrayType(), "java.nio.charset.StandardCharsets.UTF_8",
                             "StandardCharsets.UTF_8"));
    if (!expectedTypeInfos.isEmpty()) {
      stream =
        stream.filter(element -> ContainerUtil.exists(expectedTypeInfos, info -> info.getType().isAssignableFrom(element.getType())));
    }
    return stream.map(element -> PrioritizedLookupElement.withPriority(element, 1)).toList();
  }

  @Nullable
  private static PsiMethod findMethod(PsiClass stringClass, String aCase, String typeName) {
    return ContainerUtil.find(stringClass.findMethodsByName(aCase, false), m -> {
      PsiParameter[] parameters = m.getParameterList().getParameters();
      return parameters.length == 1 && TypeUtils.typeEquals(typeName, parameters[0].getType());
    });
  }

  private static class MethodWithArgument extends LookupElement implements TypedLookupItem {
    private final @NotNull PsiMethod myMethod;
    private final @NotNull PsiType myType;
    private final @NotNull String myArgument;
    private final @NotNull String myArgumentPresentation;

    MethodWithArgument(@NotNull PsiMethod method, @NotNull PsiType type, @NotNull String argument, @NotNull String argumentPresentation) {
      myMethod = method;
      myType = type;
      myArgument = argument;
      myArgumentPresentation = argumentPresentation;
      JavaMethodMergingContributor.disallowMerge(this);
    }

    @Override
    public @NotNull PsiType getType() {
      return myType;
    }

    @Override
    public @NotNull String getLookupString() {
      return myMethod.getName() + "(" + myArgumentPresentation + ")";
    }

    @Override
    public @NotNull Object getObject() {
      return myMethod;
    }

    @Override
    public void renderElement(LookupElementPresentation presentation) {
      super.renderElement(presentation);
      presentation.setTypeText(myType.getPresentableText());
      presentation.setIcon(PlatformIcons.METHOD_ICON);
    }

    @Override
    public void handleInsert(@NotNull InsertionContext context) {
      PsiFile file = context.getFile();
      CommonCodeStyleSettings settings = CodeStyle.getLanguageSettings(file);
      String callSpace = getSpace(settings.SPACE_WITHIN_METHOD_CALL_PARENTHESES);
      String insertString = myMethod.getName() + "(" + callSpace + myArgument + callSpace + ")";
      int offset = context.getStartOffset();
      context.getDocument().replaceString(offset, context.getTailOffset(), insertString);
      context.commitDocument();
      JavaCodeStyleManager.getInstance(context.getProject())
        .shortenClassReferences(file, offset, offset + insertString.length());
    }
  }
}
