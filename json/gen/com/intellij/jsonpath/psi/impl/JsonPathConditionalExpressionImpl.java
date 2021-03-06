// This is a generated file. Not intended for manual editing.
package com.intellij.jsonpath.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.intellij.jsonpath.psi.JsonPathTypes.*;
import com.intellij.jsonpath.psi.*;

public class JsonPathConditionalExpressionImpl extends JsonPathExpressionImpl implements JsonPathConditionalExpression {

  public JsonPathConditionalExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull JsonPathVisitor visitor) {
    visitor.visitConditionalExpression(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JsonPathVisitor) accept((JsonPathVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public JsonPathBinaryConditionalOperator getBinaryConditionalOperator() {
    return findNotNullChildByClass(JsonPathBinaryConditionalOperator.class);
  }

  @Override
  @NotNull
  public List<JsonPathExpression> getExpressionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, JsonPathExpression.class);
  }

}
