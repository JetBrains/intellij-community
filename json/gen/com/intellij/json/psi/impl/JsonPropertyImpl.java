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

  public JsonPropertyImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JsonElementVisitor) ((JsonElementVisitor)visitor).visitProperty(this);
    else super.accept(visitor);
  }

  @NotNull
  public String getName() {
    return JsonPsiImplUtils.getName(this);
  }

  @NotNull
  public JsonValue getNameElement() {
    return JsonPsiImplUtils.getNameElement(this);
  }

  @Nullable
  public JsonValue getValue() {
    return JsonPsiImplUtils.getValue(this);
  }

  @Nullable
  public ItemPresentation getPresentation() {
    return JsonPsiImplUtils.getPresentation(this);
  }

}
