// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.extension;

import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter;
import com.jetbrains.jsonSchema.impl.JsonSchemaType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface JsonLikeSyntaxAdapter {
  default @NotNull PsiElement adjustValue(@NotNull PsiElement value) { return value; }
  @NotNull PsiElement createProperty(final @NotNull String name, final @NotNull String value, PsiElement element);
  void ensureComma(PsiElement self, PsiElement newElement);
  void removeIfComma(PsiElement forward);
  boolean fixWhitespaceBefore(PsiElement initialElement, PsiElement element);
  default @NotNull String getDefaultValueFromType(@Nullable JsonSchemaType type) {
    return type == null ? "" : type.getDefaultValue();
  }
  PsiElement adjustNewProperty(PsiElement element);
  PsiElement adjustPropertyAnchor(LeafPsiElement element);

  /**
   * Inserts a property into an existing object
   * @param contextForInsertion either the object itself or the property before which the new property is inserted
   * @param newProperty property to insert
   * @return inserted property
   */
  default PsiElement addProperty(@NotNull PsiElement contextForInsertion, @NotNull PsiElement newProperty) {
    JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(contextForInsertion);
    PsiElement newElement;
    JsonPropertyAdapter parentPropertyAdapter;
    if (walker == null) parentPropertyAdapter = null; else parentPropertyAdapter = walker.getParentPropertyAdapter(contextForInsertion);
    boolean isProcessingProperty = parentPropertyAdapter != null && parentPropertyAdapter.getDelegate() == contextForInsertion;

    if (contextForInsertion instanceof LeafPsiElement) {
      newElement = adjustPropertyAnchor((LeafPsiElement)contextForInsertion).addBefore(newProperty, null);
    }
    else {
      if (isProcessingProperty) {
        newElement = contextForInsertion.getParent().addBefore(newProperty, contextForInsertion);
      }
      else {
        newElement = contextForInsertion.addBefore(newProperty, contextForInsertion.getLastChild());
      }
    }
    PsiElement adjusted = adjustNewProperty(newElement);

    ensureComma(adjusted, PsiTreeUtil.skipWhitespacesAndCommentsForward(newElement));
    ensureComma(PsiTreeUtil.skipWhitespacesAndCommentsBackward(newElement), adjusted);

    return adjusted;
  }

  /**
   * Deletes a property performing the cleanup if needed
   * @param property property to delete
   */
  default void removeProperty(PsiElement property) {
    PsiElement forward = PsiTreeUtil.skipWhitespacesForward(property);
    property.delete();
    removeIfComma(forward);
  }
}
