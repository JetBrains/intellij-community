// This is a generated file. Not intended for manual editing.
package com.intellij.json.psi;

import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;

public class JsonElementVisitor extends PsiElementVisitor {

  public void visitArray(@NotNull JsonArray o) {
    visitContainer(o);
  }

  public void visitBooleanLiteral(@NotNull JsonBooleanLiteral o) {
    visitLiteral(o);
  }

  public void visitContainer(@NotNull JsonContainer o) {
    visitValue(o);
  }

  public void visitLiteral(@NotNull JsonLiteral o) {
    visitValue(o);
  }

  public void visitNullLiteral(@NotNull JsonNullLiteral o) {
    visitLiteral(o);
  }

  public void visitNumberLiteral(@NotNull JsonNumberLiteral o) {
    visitLiteral(o);
  }

  public void visitObject(@NotNull JsonObject o) {
    visitContainer(o);
  }

  public void visitProperty(@NotNull JsonProperty o) {
    visitElement(o);
    // visitPsiNamedElement(o);
  }

  public void visitReferenceExpression(@NotNull JsonReferenceExpression o) {
    visitValue(o);
  }

  public void visitStringLiteral(@NotNull JsonStringLiteral o) {
    visitLiteral(o);
  }

  public void visitValue(@NotNull JsonValue o) {
    visitElement(o);
  }

  public void visitElement(@NotNull JsonElement o) {
    visitPsiElement(o);
  }

  public void visitPsiElement(@NotNull PsiElement o) {
    visitElement(o);
  }

}
