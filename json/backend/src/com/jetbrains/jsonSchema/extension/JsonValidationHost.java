// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.extension;

import com.intellij.psi.PsiElement;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import com.jetbrains.jsonSchema.impl.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public interface JsonValidationHost {
  void error(final String error, final PsiElement holder, JsonErrorPriority priority);
  void error(final PsiElement newHolder, JsonValidationError error);
  void error(final String error, final PsiElement holder,
             JsonValidationError.FixableIssueKind fixableIssueKind,
             JsonValidationError.IssueData data,
             JsonErrorPriority priority);

  void typeError(final @NotNull PsiElement value, @Nullable JsonSchemaType currentType, final JsonSchemaType @NotNull ... allowedTypes);

  MatchResult resolve(JsonSchemaObject schemaObject, @Nullable JsonValueAdapter inspectedElementAdapter);

  @Nullable
  JsonValidationHost checkByMatchResult(JsonValueAdapter adapter, MatchResult result, JsonComplianceCheckerOptions options);

  boolean isValid();

  void checkObjectBySchemaRecordErrors(@NotNull JsonSchemaObject schema, @NotNull JsonValueAdapter object);

  void addErrorsFrom(JsonValidationHost otherHost);

  boolean hasRecordedErrorsFor(@NotNull JsonValueAdapter inspectedValueAdapter);

  @NotNull Map<PsiElement, JsonValidationError> getErrors();
}
