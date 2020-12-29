// This is a generated file. Not intended for manual editing.
package com.intellij.jsonpath.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface JsonPathQuotedSegment extends PsiElement {

  @NotNull
  JsonPathQuotedPathsList getQuotedPathsList();

  @NotNull
  List<JsonPathSegmentExpression> getSegmentExpressionList();

}
