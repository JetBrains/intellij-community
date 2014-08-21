package com.intellij.json.psi.impl;

import com.intellij.json.psi.JsonProperty;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
abstract class JsonPropertyMixin extends JsonElementImpl implements JsonProperty {
  public JsonPropertyMixin(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }
}
