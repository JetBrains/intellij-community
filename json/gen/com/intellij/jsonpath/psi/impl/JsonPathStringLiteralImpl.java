// This is a generated file. Not intended for manual editing.
package com.intellij.jsonpath.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.intellij.jsonpath.psi.JsonPathTypes.*;
import com.intellij.jsonpath.psi.JsonPathStringLiteralMixin;
import com.intellij.jsonpath.psi.*;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;

public class JsonPathStringLiteralImpl extends JsonPathStringLiteralMixin implements JsonPathStringLiteral {

  public JsonPathStringLiteralImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull JsonPathVisitor visitor) {
    visitor.visitStringLiteral(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JsonPathVisitor) accept((JsonPathVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  public @NotNull List<Pair<TextRange, String>> getTextFragments() {
    return JsonPathPsiUtils.getTextFragments(this);
  }

}
