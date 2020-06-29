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
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.function.Predicate;
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
    if (target instanceof PsiIdentifier) {
      PsiElement parent = target.getParent();
      if (parent instanceof PsiJavaCodeReferenceElement) {
        return createHighlightExceptionUsagesFromThrowsHandler(editor, file, target, (PsiJavaCodeReferenceElement)parent);
      }
    }
    return null;
  }

  @Nullable
  private static HighlightUsagesHandlerBase<PsiClass> createHighlightTryHandler(@NotNull Editor editor, @NotNull PsiFile file, @NotNull PsiElement target, @NotNull PsiElement parent) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("codeassists.highlight.throws");

    PsiCodeBlock tryBlock = ((PsiTryStatement)parent).getTryBlock();
    if (tryBlock == null) return null;

    Collection<PsiClassType> unhandled = ExceptionUtil.collectUnhandledExceptions(tryBlock, tryBlock);
    PsiClassType[] types = unhandled.toArray(PsiClassType.EMPTY_ARRAY);
    return new HighlightExceptionsHandler(editor, file, target, types, tryBlock, null, __->true);
  }

  @Nullable
  private static HighlightUsagesHandlerBase<PsiClass> createHighlightExceptionUsagesFromThrowsHandler(@NotNull Editor editor, @NotNull PsiFile file, @NotNull PsiElement target, @NotNull PsiJavaCodeReferenceElement parent) {
    PsiElement list = parent.getParent();
    if (!(list instanceof PsiReferenceList)) return null;
    PsiElement method = list.getParent();
    if (!(method instanceof PsiMethod)) return null;
    if (!file.getManager().areElementsEquivalent(list, ((PsiMethod)method).getThrowsList())) return null;

    PsiElement block = ((PsiMethod)method).getBody();
    if (block == null) return null;
    PsiElement resolved = parent.resolve();
    if (!(resolved instanceof PsiClass)) return null;
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(file.getProject());
    PsiClassType type = factory.createType(parent);
    return new HighlightThrowsClassesHandler(editor, file, target, type, block, resolved);
  }

  @Nullable
  private static HighlightUsagesHandlerBase<PsiClass> createHighlightCatchHandler(@NotNull Editor editor, @NotNull PsiFile file, @NotNull PsiElement target, @NotNull PsiElement parent) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("codeassists.highlight.throws");

    PsiTryStatement tryStatement = ((PsiCatchSection)parent).getTryStatement();
    PsiParameter parameter = ((PsiCatchSection)parent).getParameter();
    PsiCodeBlock tryBlock = tryStatement.getTryBlock();
    PsiResourceList resourceList = tryStatement.getResourceList();
    if (parameter == null || tryBlock == null) return null;

    PsiParameter[] parameters = tryStatement.getCatchBlockParameters();
    Predicate<PsiType> filter = type -> {
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
    PsiClassType[] types = unhandled.filter(filter).toArray(PsiClassType[]::new);
    return new HighlightExceptionsHandler(editor, file, target, types, tryBlock, resourceList, filter);
  }

  @Nullable
  private static HighlightUsagesHandlerBase<PsiClass> createThrowsHandler(@NotNull Editor editor, @NotNull PsiFile file, @NotNull PsiElement target) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("codeassists.highlight.throws");

    PsiElement grand = target.getParent().getParent();
    if (!(grand instanceof PsiMethod)) return null;
    PsiCodeBlock body = ((PsiMethod)grand).getBody();
    if (body == null) return null;

    Collection<PsiClassType> unhandled = ExceptionUtil.collectUnhandledExceptions(body, body);
    PsiClassType[] types = unhandled.toArray(PsiClassType.EMPTY_ARRAY);
    return new HighlightExceptionsHandler(editor, file, target, types, body, null, __->true);
  }
}