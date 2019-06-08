// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.extension;

import com.intellij.json.pointer.JsonPointerPosition;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ThreeState;
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import com.jetbrains.jsonSchema.impl.JsonOriginalPsiWalker;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

/**
 * @author Irina.Chernushina on 2/15/2017.
 */
public interface JsonLikePsiWalker {
  /**
   * Returns YES in place where a property name is expected,
   *         NO in place where a property value is expected,
   *         UNSURE where both property name and property value can be present
   */
  ThreeState isName(PsiElement element);

  boolean isPropertyWithValue(@NotNull PsiElement element);

  PsiElement findElementToCheck(@NotNull final PsiElement element);

  @Nullable
  JsonPointerPosition findPosition(@NotNull final PsiElement element, boolean forceLastTransition);

  boolean requiresNameQuotes();
  default boolean requiresValueQuotes() { return true; }
  boolean allowsSingleQuotes();
  default boolean isValidIdentifier(String string, Project project) { return true; }

  boolean hasMissingCommaAfter(@NotNull PsiElement element);

  Set<String> getPropertyNamesOfParentObject(@NotNull PsiElement originalPosition, PsiElement computedPosition);

  @Nullable
  JsonPropertyAdapter getParentPropertyAdapter(@NotNull PsiElement element);
  boolean isTopJsonElement(@NotNull PsiElement element);
  @Nullable
  JsonValueAdapter createValueAdapter(@NotNull PsiElement element);

  default TextRange adjustErrorHighlightingRange(@NotNull PsiElement element) {
    return element.getTextRange();
  }

  default boolean acceptsEmptyRoot() { return false; }

  @Nullable
  Collection<PsiElement> getRoots(@NotNull PsiFile file);

  @Nullable
  static JsonLikePsiWalker getWalker(@NotNull final PsiElement element, JsonSchemaObject schemaObject) {
    if (JsonOriginalPsiWalker.INSTANCE.handles(element)) return JsonOriginalPsiWalker.INSTANCE;

    return JsonLikePsiWalkerFactory.EXTENSION_POINT_NAME.getExtensionList().stream()
      .filter(extension -> extension.handles(element))
      .findFirst()
      .map(extension -> extension.create(schemaObject))
      .orElse(null);
  }

  default String getDefaultObjectValue() { return "{}"; }
  default String getDefaultArrayValue() { return "[]"; }

  default boolean hasWhitespaceDelimitedCodeBlocks() { return false; }

  default String getNodeTextForValidation(PsiElement element) { return element.getText(); }

  default JsonLikeSyntaxAdapter getSyntaxAdapter(Project project) { return null; }

  @Nullable
  default PsiElement getParentContainer(PsiElement element) {
    return null;
  }

  @Nullable
  PsiElement getPropertyNameElement(@Nullable PsiElement property);
}
