// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Service interface for running networknt validation.
 * <p>
 * The implementation lives in {@code intellij.json.networknt.wrapper} module to avoid
 * a circular dependency between {@code intellij.json.backend} and the wrapper module.
 */
public interface NetworkntValidationBridge {

  static @NotNull NetworkntValidationBridge getInstance(@NotNull Project project) {
    return project.getService(NetworkntValidationBridge.class);
  }

  /**
   * Validates a JSON/YAML instance against a schema using networknt.
   * <p>
   * Re-throws {@code ProcessCanceledException} for proper inspection framework rescheduling.
   *
   * @param schemaFile    the schema VirtualFile
   * @param walker        the PSI walker for the instance format
   * @param rootElement   the root PSI element of the instance
   * @param schemaVersion the JSON Schema version
   * @return Map of PsiElement → JsonValidationError for all validation errors
   */
  @NotNull Map<PsiElement, JsonValidationError> validate(
    @NotNull VirtualFile schemaFile,
    @NotNull JsonLikePsiWalker walker,
    @NotNull PsiElement rootElement,
    @NotNull JsonSchemaVersion schemaVersion
  );

}
