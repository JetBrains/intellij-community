// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JsonSchemaComplianceChecker {
  private static final Key<Set<PsiElement>> ANNOTATED_PROPERTIES = Key.create("JsonSchema.Properties.Annotated");

  @NotNull private final JsonSchemaObject myRootSchema;
  @NotNull private final ProblemsHolder myHolder;
  @NotNull private final JsonLikePsiWalker myWalker;
  private final LocalInspectionToolSession mySession;
  @Nullable private final String myMessagePrefix;

  public JsonSchemaComplianceChecker(@NotNull JsonSchemaObject rootSchema,
                                     @NotNull ProblemsHolder holder,
                                     @NotNull JsonLikePsiWalker walker,
                                     @NotNull LocalInspectionToolSession session) {
    this(rootSchema, holder, walker, session, null);
  }

  public JsonSchemaComplianceChecker(@NotNull JsonSchemaObject rootSchema,
                                     @NotNull ProblemsHolder holder,
                                     @NotNull JsonLikePsiWalker walker,
                                     @NotNull LocalInspectionToolSession session,
                                     @Nullable String messagePrefix) {
    myRootSchema = rootSchema;
    myHolder = holder;
    myWalker = walker;
    mySession = session;
    myMessagePrefix = messagePrefix;
  }

  public void annotate(@NotNull final PsiElement element) {
    final JsonPropertyAdapter firstProp = myWalker.getParentPropertyAdapter(element);
    if (firstProp != null && firstProp.getValue() != null) {
      final List<JsonSchemaVariantsTreeBuilder.Step> position = myWalker.findPosition(firstProp.getDelegate(), true);
      if (position == null || position.isEmpty()) return;
      final MatchResult result = new JsonSchemaResolver(myRootSchema, false, position).detailedResolve();
      createWarnings(JsonSchemaAnnotatorChecker.checkByMatchResult(firstProp.getValue(), result));
    }
    checkRoot(element, firstProp);
  }

  private void checkRoot(@NotNull PsiElement element, @Nullable JsonPropertyAdapter firstProp) {
    JsonValueAdapter rootToCheck;
    if (firstProp == null) {
      rootToCheck = findTopLevelElement(myWalker, element);
    } else {
      rootToCheck = firstProp.getParentObject();
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
      for (Map.Entry<PsiElement, JsonValidationError> entry : checker.getErrors().entrySet()) {
        if (checkIfAlreadyProcessed(entry.getKey())) continue;
        String value = entry.getValue().getMessage();
        if (myMessagePrefix != null) value = myMessagePrefix + value;
        LocalQuickFix fix = entry.getValue().createFix(myWalker.getQuickFixAdapter(myHolder.getProject()));
        if (fix == null) {
          myHolder.registerProblem(entry.getKey(), value);
        }
        else {
          myHolder.registerProblem(entry.getKey(), value, fix);
        }
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

  private boolean checkIfAlreadyProcessed(@NotNull PsiElement property) {
    Set<PsiElement> data = mySession.getUserData(ANNOTATED_PROPERTIES);
    if (data == null) {
      data = new HashSet<>();
      mySession.putUserData(ANNOTATED_PROPERTIES, data);
    }
    if (data.contains(property)) return true;
    data.add(property);
    return false;
  }
}
