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

public class JsonPathPathExpressionImpl extends JsonPathExpressionImpl implements JsonPathPathExpression {

  public JsonPathPathExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull JsonPathVisitor visitor) {
    visitor.visitPathExpression(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JsonPathVisitor) accept((JsonPathVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public JsonPathEvalSegment getEvalSegment() {
    return findChildByClass(JsonPathEvalSegment.class);
  }

  @Override
  @NotNull
  public List<JsonPathFunctionCall> getFunctionCallList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, JsonPathFunctionCall.class);
  }

  @Override
  @NotNull
  public List<JsonPathIdSegment> getIdSegmentList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, JsonPathIdSegment.class);
  }

  @Override
  @NotNull
  public List<JsonPathQuotedSegment> getQuotedSegmentList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, JsonPathQuotedSegment.class);
  }

  @Override
  @Nullable
  public JsonPathRootSegment getRootSegment() {
    return findChildByClass(JsonPathRootSegment.class);
  }

  @Override
  @NotNull
  public List<JsonPathSegmentExpression> getSegmentExpressionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, JsonPathSegmentExpression.class);
  }

  @Override
  @NotNull
  public List<JsonPathWildcardSegment> getWildcardSegmentList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, JsonPathWildcardSegment.class);
  }

}
