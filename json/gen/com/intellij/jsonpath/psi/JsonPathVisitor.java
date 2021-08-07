// This is a generated file. Not intended for manual editing.
package com.intellij.jsonpath.psi;

import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiElement;

public class JsonPathVisitor extends PsiElementVisitor {

  public void visitAndExpression(@NotNull JsonPathAndExpression o) {
    visitExpression(o);
  }

  public void visitArrayValue(@NotNull JsonPathArrayValue o) {
    visitValue(o);
  }

  public void visitBinaryConditionalOperator(@NotNull JsonPathBinaryConditionalOperator o) {
    visitPsiElement(o);
  }

  public void visitBooleanLiteral(@NotNull JsonPathBooleanLiteral o) {
    visitLiteralValue(o);
  }

  public void visitConditionalExpression(@NotNull JsonPathConditionalExpression o) {
    visitExpression(o);
  }

  public void visitDivideExpression(@NotNull JsonPathDivideExpression o) {
    visitExpression(o);
  }

  public void visitEvalSegment(@NotNull JsonPathEvalSegment o) {
    visitPsiElement(o);
  }

  public void visitExpression(@NotNull JsonPathExpression o) {
    visitPsiElement(o);
  }

  public void visitFilterExpression(@NotNull JsonPathFilterExpression o) {
    visitPsiElement(o);
  }

  public void visitFunctionArgsList(@NotNull JsonPathFunctionArgsList o) {
    visitPsiElement(o);
  }

  public void visitFunctionCall(@NotNull JsonPathFunctionCall o) {
    visitPsiElement(o);
  }

  public void visitId(@NotNull JsonPathId o) {
    visitPsiElement(o);
  }

  public void visitIdSegment(@NotNull JsonPathIdSegment o) {
    visitPsiElement(o);
  }

  public void visitIndexExpression(@NotNull JsonPathIndexExpression o) {
    visitPsiElement(o);
  }

  public void visitIndexesList(@NotNull JsonPathIndexesList o) {
    visitPsiElement(o);
  }

  public void visitLiteralValue(@NotNull JsonPathLiteralValue o) {
    visitValue(o);
  }

  public void visitMinusExpression(@NotNull JsonPathMinusExpression o) {
    visitExpression(o);
  }

  public void visitMultiplyExpression(@NotNull JsonPathMultiplyExpression o) {
    visitExpression(o);
  }

  public void visitNullLiteral(@NotNull JsonPathNullLiteral o) {
    visitLiteralValue(o);
  }

  public void visitNumberLiteral(@NotNull JsonPathNumberLiteral o) {
    visitLiteralValue(o);
  }

  public void visitObjectProperty(@NotNull JsonPathObjectProperty o) {
    visitPsiElement(o);
  }

  public void visitObjectValue(@NotNull JsonPathObjectValue o) {
    visitValue(o);
  }

  public void visitOrExpression(@NotNull JsonPathOrExpression o) {
    visitExpression(o);
  }

  public void visitParenthesizedExpression(@NotNull JsonPathParenthesizedExpression o) {
    visitExpression(o);
  }

  public void visitPathExpression(@NotNull JsonPathPathExpression o) {
    visitExpression(o);
  }

  public void visitPlusExpression(@NotNull JsonPathPlusExpression o) {
    visitExpression(o);
  }

  public void visitQuotedPathsList(@NotNull JsonPathQuotedPathsList o) {
    visitPsiElement(o);
  }

  public void visitQuotedSegment(@NotNull JsonPathQuotedSegment o) {
    visitPsiElement(o);
  }

  public void visitRegexExpression(@NotNull JsonPathRegexExpression o) {
    visitExpression(o);
  }

  public void visitRegexLiteral(@NotNull JsonPathRegexLiteral o) {
    visitPsiElement(o);
  }

  public void visitRootSegment(@NotNull JsonPathRootSegment o) {
    visitPsiElement(o);
  }

  public void visitSegmentExpression(@NotNull JsonPathSegmentExpression o) {
    visitPsiElement(o);
  }

  public void visitSliceExpression(@NotNull JsonPathSliceExpression o) {
    visitPsiElement(o);
  }

  public void visitStringLiteral(@NotNull JsonPathStringLiteral o) {
    visitLiteralValue(o);
  }

  public void visitUnaryMinusExpression(@NotNull JsonPathUnaryMinusExpression o) {
    visitExpression(o);
  }

  public void visitUnaryNotExpression(@NotNull JsonPathUnaryNotExpression o) {
    visitExpression(o);
  }

  public void visitValue(@NotNull JsonPathValue o) {
    visitExpression(o);
  }

  public void visitWildcardSegment(@NotNull JsonPathWildcardSegment o) {
    visitPsiElement(o);
  }

  public void visitPsiElement(@NotNull PsiElement o) {
    visitElement(o);
  }

}
