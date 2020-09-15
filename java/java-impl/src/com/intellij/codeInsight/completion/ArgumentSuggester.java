// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.TypedLookupItem;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
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

public class ArgumentSuggester {
  static @NotNull Collection<LookupElement> suggestArgument(PsiReferenceExpression ref, List<ExpectedTypeInfo> expectedTypeInfos) {
    PsiExpression qualifier = ref.getQualifierExpression();
    
    if (qualifier == null) return Collections.emptyList();
    PsiType type = qualifier.getType();
    if (TypeUtils.isJavaLangString(type)) {
      if (PsiUtil.isLanguageLevel7OrHigher(ref)) {
        return StreamEx.of(new MethodWithArgument("toLowerCase", type, "java.util.Locale.ROOT", "Locale.ROOT"),
                           new MethodWithArgument("toUpperCase", type, "java.util.Locale.ROOT", "Locale.ROOT"),
                           new MethodWithArgument("getBytes", PsiType.BYTE.createArrayType(), "java.nio.charset.StandardCharsets.UTF_8", "StandardCharsets.UTF_8"))
          .filter(expectedTypeInfos.isEmpty() ? 
                  element -> true : 
                  element -> ContainerUtil.exists(expectedTypeInfos, ti -> ti.getType().equals(element.getType())))
          .map(element -> PrioritizedLookupElement.withPriority(element, 1)).toList();
      }
    }
    return Collections.emptyList();
  }

  private static class MethodWithArgument extends LookupElement implements TypedLookupItem {
    private final String myMethod;
    private final PsiType myType;
    private final String myArgument;
    private final String myPresentation;

    MethodWithArgument(String method, PsiType type, String argument, String presentation) {
      myMethod = method;
      myType = type;
      myArgument = argument;
      myPresentation = presentation;
    }

    @Override
    public @Nullable PsiType getType() {
      return myType;
    }

    @Override
    public @NotNull String getLookupString() {
      return myMethod + "(" + myPresentation + ")";
    }

    @Override
    public void renderElement(LookupElementPresentation presentation) {
      super.renderElement(presentation);
      presentation.setTypeText(myType.getPresentableText());
      presentation.setIcon(PlatformIcons.METHOD_ICON);
    }

    @Override
    public void handleInsert(@NotNull InsertionContext context) {
      String insertString = myMethod + "(" + myArgument + ")";
      context.getDocument().replaceString(context.getStartOffset(), context.getTailOffset(), insertString);
      context.commitDocument();
      PsiMethodCallExpression call =
        PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), PsiMethodCallExpression.class, false);
      if (call == null) return;

      JavaCodeStyleManager.getInstance(context.getProject()).shortenClassReferences(call.getArgumentList());
    }
  }
}
