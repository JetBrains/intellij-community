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
package com.jetbrains.jsonSchema.impl;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.AnnotationSession;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Irina.Chernushina on 8/31/2015.
 */
public class JsonSchemaAnnotator implements Annotator {
  private static final Key<Set<PsiElement>> ANNOTATED_PROPERTIES = Key.create("JsonSchema.Properties.Annotated");

  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (element.getContainingFile() == null) return;

    final JsonSchemaObject rootSchema =
      JsonSchemaService.Impl.get(element.getProject()).getSchemaObject(element.getContainingFile().getViewProvider().getVirtualFile());
    if (rootSchema == null) return;

    final JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(element, rootSchema);
    if (walker == null) return;
    new Worker(rootSchema, holder, walker).annotate(element);
  }

  public static class Worker {
    @NotNull private final JsonSchemaObject myRootSchema;
    @NotNull private final AnnotationHolder myHolder;
    @NotNull private final JsonLikePsiWalker myWalker;

    public Worker(@NotNull JsonSchemaObject rootSchema,
                  @NotNull AnnotationHolder holder,
                  @NotNull JsonLikePsiWalker walker) {
      myRootSchema = rootSchema;
      myHolder = holder;
      myWalker = walker;
    }

    public void annotate(@NotNull final PsiElement element) {
      final JsonPropertyAdapter firstProp = myWalker.getParentPropertyAdapter(element);
      if (firstProp != null && firstProp.getValue() != null) {
        final List<JsonSchemaVariantsTreeBuilder.Step> position = myWalker.findPosition(firstProp.getDelegate(), false, true);
        if (position == null || position.isEmpty()) return;
        final MatchResult result = new JsonSchemaResolver(myRootSchema, false, position).detailedResolve();
        createWarnings(JsonSchemaAnnotatorChecker.checkByMatchResult(firstProp.getValue(), result));
      }
      checkRoot(element, firstProp);
    }

    private void checkRoot(@NotNull PsiElement element, JsonPropertyAdapter firstProp) {
      JsonValueAdapter rootToCheck;
      if (firstProp == null) {
        rootToCheck = findTopLevelElement(myWalker, element);
      } else {
        rootToCheck = firstProp.getParentObject();
        if (rootToCheck == null) rootToCheck = firstProp.getParentArray();
        if (rootToCheck == null || !myWalker.isTopJsonElement(rootToCheck.getDelegate().getParent())) {
          return;
        }
      }
      if (rootToCheck != null) {
        final MatchResult matchResult = new JsonSchemaResolver(myRootSchema).detailedResolve();
        createWarnings(JsonSchemaAnnotatorChecker.checkByMatchResult(rootToCheck, matchResult));
      }
    }

    private void createWarnings(@Nullable JsonSchemaAnnotatorChecker checker) {
      if (checker != null && ! checker.isCorrect()) {
        for (Map.Entry<PsiElement, String> entry : checker.getErrors().entrySet()) {
          if (checkIfAlreadyProcessed(myHolder, entry.getKey())) continue;
          myHolder.createWarningAnnotation(entry.getKey(), entry.getValue());
        }
      }
    }

    private static JsonValueAdapter findTopLevelElement(@NotNull JsonLikePsiWalker walker, @NotNull PsiElement element) {
      final Ref<PsiElement> ref = new Ref<>();
      PsiTreeUtil.findFirstParent(element, el -> {
        final boolean isTop = walker.isTopJsonElement(el);
        if (!isTop) ref.set(el);
        return isTop;
      });
      return ref.isNull() ? null : walker.createValueAdapter(ref.get());
    }

    private static boolean checkIfAlreadyProcessed(@NotNull AnnotationHolder holder, PsiElement property) {
      final AnnotationSession session = holder.getCurrentAnnotationSession();
      Set<PsiElement> data = session.getUserData(ANNOTATED_PROPERTIES);
      if (data == null) {
        data = new HashSet<>();
        session.putUserData(ANNOTATED_PROPERTIES, data);
      }
      if (data.contains(property)) return true;
      data.add(property);
      return false;
    }
  }
}
