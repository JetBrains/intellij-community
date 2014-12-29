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
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;

public class JsonStringLiteralImpl extends JsonStringLiteralMixin implements JsonStringLiteral {

  public JsonStringLiteralImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JsonElementVisitor) ((JsonElementVisitor)visitor).visitStringLiteral(this);
    else super.accept(visitor);
  }

  @NotNull
  public List<Pair<TextRange, String>> getTextFragments() {
    return JsonPsiImplUtils.getTextFragments(this);
  }

  @NotNull
  public String getValue() {
    return JsonPsiImplUtils.getValue(this);
  }

}
