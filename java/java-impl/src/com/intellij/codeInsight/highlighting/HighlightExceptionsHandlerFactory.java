/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.highlighting;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.stream.Stream;

/**
 * @author yole
 */
public class HighlightExceptionsHandlerFactory extends HighlightUsagesHandlerFactoryBase {
  @Override
  public HighlightUsagesHandlerBase createHighlightUsagesHandler(@NotNull Editor editor, @NotNull PsiFile file, @NotNull PsiElement target) {
    if (target instanceof PsiKeyword) {
      PsiElement parent = target.getParent();
      if (PsiKeyword.TRY.equals(target.getText()) && parent instanceof PsiTryStatement) {
        return createHighlightTryHandler(editor, file, target, parent);
      }
      if (PsiKeyword.CATCH.equals(target.getText()) && parent instanceof PsiCatchSection) {
        return createHighlightCatchHandler(editor, file, target, parent);
      }
      if (PsiKeyword.THROWS.equals(target.getText())) {
        return createThrowsHandler(editor, file, target);
      }
    }
    return null;
  }

  @Nullable
  private static HighlightUsagesHandlerBase createHighlightTryHandler(Editor editor, PsiFile file, PsiElement target, PsiElement parent) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("codeassists.highlight.throws");

    PsiCodeBlock tryBlock = ((PsiTryStatement)parent).getTryBlock();
    if (tryBlock == null) return null;

    Collection<PsiClassType> unhandled = ExceptionUtil.collectUnhandledExceptions(tryBlock, tryBlock);
    PsiClassType[] types = unhandled.toArray(PsiClassType.EMPTY_ARRAY);
    return new HighlightExceptionsHandler(editor, file, target, types, tryBlock, null, Conditions.alwaysTrue());
  }

  @Nullable
  private static HighlightUsagesHandlerBase createHighlightCatchHandler(Editor editor, PsiFile file, PsiElement target, PsiElement parent) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("codeassists.highlight.throws");

    PsiTryStatement tryStatement = ((PsiCatchSection)parent).getTryStatement();
    PsiParameter parameter = ((PsiCatchSection)parent).getParameter();
    PsiCodeBlock tryBlock = tryStatement.getTryBlock();
    PsiResourceList resourceList = tryStatement.getResourceList();
    if (parameter == null || tryBlock == null) return null;

    PsiParameter[] parameters = tryStatement.getCatchBlockParameters();
    Condition<PsiType> filter = type -> {
      for (PsiParameter p : parameters) {
        boolean isAssignable = p.getType().isAssignableFrom(type);
        if (p == parameter) return isAssignable;
        else if (isAssignable) return false;
      }
      return false;
    };

    Stream<PsiClassType> unhandled = ExceptionUtil.collectUnhandledExceptions(tryBlock, tryBlock).stream();
    if (resourceList != null) {
      unhandled = Stream.concat(unhandled, ExceptionUtil.collectUnhandledExceptions(resourceList, resourceList).stream());
    }
    PsiClassType[] types = unhandled.filter(filter::value).toArray(PsiClassType[]::new);
    return new HighlightExceptionsHandler(editor, file, target, types, tryBlock, resourceList, filter);
  }

  @Nullable
  private static HighlightUsagesHandlerBase createThrowsHandler(Editor editor, PsiFile file, PsiElement target) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("codeassists.highlight.throws");

    PsiElement grand = target.getParent().getParent();
    if (!(grand instanceof PsiMethod)) return null;
    PsiCodeBlock body = ((PsiMethod)grand).getBody();
    if (body == null) return null;

    Collection<PsiClassType> unhandled = ExceptionUtil.collectUnhandledExceptions(body, body);
    PsiClassType[] types = unhandled.toArray(PsiClassType.EMPTY_ARRAY);
    return new HighlightExceptionsHandler(editor, file, target, types, body, null, Conditions.alwaysTrue());
  }
}