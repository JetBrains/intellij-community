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

public class JsonArrayImpl extends JsonContainerImpl implements JsonArray {

  public JsonArrayImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JsonElementVisitor) ((JsonElementVisitor)visitor).visitArray(this);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<JsonValue> getValueList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, JsonValue.class);
  }

  @Nullable
  public ItemPresentation getPresentation() {
    return JsonPsiImplUtils.getPresentation(this);
  }

}
