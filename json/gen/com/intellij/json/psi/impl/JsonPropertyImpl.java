// This is a generated file. Not intended for manual editing.
package com.intellij.json.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.intellij.json.JsonElementTypes.*;
import com.intellij.json.psi.*;
import com.intellij.navigation.ItemPresentation;

public class JsonPropertyImpl extends JsonPropertyMixin implements JsonProperty {

  public JsonPropertyImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull JsonElementVisitor visitor) {
    visitor.visitProperty(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JsonElementVisitor) accept((JsonElementVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  public @NotNull String getName() {
    return JsonPsiImplUtils.getName(this);
  }

  @Override
  public @NotNull JsonValue getNameElement() {
    return JsonPsiImplUtils.getNameElement(this);
  }

  @Override
  public @Nullable JsonValue getValue() {
    return JsonPsiImplUtils.getValue(this);
  }

  @Override
  public @Nullable ItemPresentation getPresentation() {
    return JsonPsiImplUtils.getPresentation(this);
  }

}
