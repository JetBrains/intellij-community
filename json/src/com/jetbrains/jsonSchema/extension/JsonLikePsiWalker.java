// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.jetbrains.jsonSchema.impl.JsonSchemaType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

public interface JsonLikePsiWalker {
  /**
   * Returns YES in place where a property name is expected,
   *         NO in place where a property value is expected,
   *         UNSURE where both property name and property value can be present
   */
  ThreeState isName(PsiElement element);

  boolean isPropertyWithValue(@NotNull PsiElement element);

  PsiElement findElementToCheck(final @NotNull PsiElement element);

  @Nullable
  JsonPointerPosition findPosition(final @NotNull PsiElement element, boolean forceLastTransition);

  // for languages where objects and arrays are syntactically indistinguishable
  default boolean hasObjectArrayAmbivalence() { return false; }

  boolean requiresNameQuotes();
  default boolean requiresValueQuotes() { return true; }
  boolean allowsSingleQuotes();
  default boolean isValidIdentifier(String string, Project project) { return true; }

  default boolean isQuotedString(@NotNull PsiElement element) { return false; }

  boolean hasMissingCommaAfter(@NotNull PsiElement element);

  Set<String> getPropertyNamesOfParentObject(@NotNull PsiElement originalPosition, PsiElement computedPosition);

  /** Returns the indent of the given element in its file expressed in number of spaces */
  default int indentOf(@NotNull PsiElement element) {
    return 0;
  }

  /** Returns the indent, expressed in number of spaces, that this file has per indent level */
  default int indentOf(@NotNull PsiFile file) {
    return 4;
  }

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

  /** @deprecated This is currently a hack. If you think you need this too, add another method, because this one WILL be removed. */
  @Deprecated(forRemoval = true)
  @ApiStatus.Experimental
  default boolean requiresReformatAfterArrayInsertion() {
    return true;
  }

  static @Nullable JsonLikePsiWalker getWalker(final @NotNull PsiElement element) {
    return getWalker(element, null);
  }

  static @Nullable JsonLikePsiWalker getWalker(final @NotNull PsiElement element, @Nullable JsonSchemaObject schemaObject) {
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

  default @Nullable PsiElement getParentContainer(PsiElement element) {
    return null;
  }

  @Nullable
  PsiElement getPropertyNameElement(@Nullable PsiElement property);

  default String getPropertyValueSeparator(@Nullable JsonSchemaType valueType) { return ":"; }
}
