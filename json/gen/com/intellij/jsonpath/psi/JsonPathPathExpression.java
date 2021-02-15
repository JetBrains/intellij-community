// This is a generated file. Not intended for manual editing.
package com.intellij.jsonpath.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface JsonPathPathExpression extends JsonPathExpression {

  @Nullable
  JsonPathEvalSegment getEvalSegment();

  @NotNull
  List<JsonPathFunctionCall> getFunctionCallList();

  @NotNull
  List<JsonPathIdSegment> getIdSegmentList();

  @NotNull
  List<JsonPathQuotedSegment> getQuotedSegmentList();

  @Nullable
  JsonPathRootSegment getRootSegment();

  @NotNull
  List<JsonPathSegmentExpression> getSegmentExpressionList();

  @NotNull
  List<JsonPathWildcardSegment> getWildcardSegmentList();

}
