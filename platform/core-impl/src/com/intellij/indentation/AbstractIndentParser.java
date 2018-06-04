/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.indentation;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractIndentParser implements PsiParser {
  protected IndentPsiBuilder myBuilder;

  @NotNull
  public ASTNode parse(@NotNull IElementType root, @NotNull PsiBuilder builder) {
    myBuilder = createPsiBuilder(builder);
    parseRoot(root);
    return myBuilder.getTreeBuilt();
  }

  @NotNull
  protected IndentPsiBuilder createPsiBuilder(@NotNull PsiBuilder builder) {
    return new IndentPsiBuilder(builder);
  }

  protected abstract void parseRoot(IElementType root);

  public PsiBuilder.Marker mark(boolean couldBeRolledBack) {
    if (couldBeRolledBack) {
      return myBuilder.markWithRollbackPossibility();
    }
    return myBuilder.mark();
  }

  public PsiBuilder.Marker mark() {
    return mark(false);
  }

  public void done(@NotNull final PsiBuilder.Marker marker, @NotNull final IElementType elementType) {
    marker.done(elementType);
  }

  public static void collapse(@NotNull final PsiBuilder.Marker marker, @NotNull final IElementType elementType) {
    marker.collapse(elementType);
  }

  protected static void drop(@NotNull final PsiBuilder.Marker marker) {
    marker.drop();
  }

  protected void rollbackTo(@NotNull final PsiBuilder.Marker marker) {
    marker.rollbackTo();
  }

  protected boolean eof() {
    return myBuilder.eof();
  }

  protected int getCurrentOffset() {
    return myBuilder.getCurrentOffset();
  }

  public int getCurrentIndent() {
    return myBuilder.getCurrentIndent();
  }

  protected void error(String message) {
    myBuilder.error(message);
  }

  @Nullable
  public IElementType getTokenType() {
    return myBuilder.getTokenType();
  }

  protected static boolean tokenIn(@Nullable final IElementType elementType, IElementType... tokens) {
    for (IElementType token : tokens) {
      if (elementType == token) {
        return true;
      }
    }
    return false;
  }

  protected boolean currentTokenIn(IElementType... tokens) {
    return tokenIn(getTokenType(), tokens);
  }

  protected boolean currentTokenIn(@NotNull final TokenSet tokenSet) {
    return tokenSet.contains(getTokenType());
  }

  protected static boolean tokenIn(@Nullable final IElementType elementType, @NotNull final TokenSet tokenSet) {
    return tokenSet.contains(elementType);
  }

  @NotNull
  protected String getTokenText() {
    String result = myBuilder.getTokenText();
    if (result == null) {
      result = "";
    }
    return result;
  }

  protected boolean expect(@NotNull final IElementType elementType) {
    return expect(elementType, "Expected: " + elementType);
  }

  protected boolean expect(@NotNull final IElementType elementType, String expectedMessage) {
    if (getTokenType() == elementType) {
      advance();
      return true;
    }
    error(expectedMessage);
    return false;
  }

  @Nullable
  public IElementType lookAhead(int step) {
    return myBuilder.lookAhead(step);
  }

  @Nullable
  public IElementType rawLookup(int step) {
    return myBuilder.rawLookup(step);
  }

  public boolean isNewLine() {
    return myBuilder.isNewLine();
  }

  public void advance() {
    myBuilder.advanceLexer();
  }

  public void recalculateCurrentIndent() {
   myBuilder.recalculateCurrentIndent(getEolElementType(), getIndentElementType());
  }

  protected void advanceUntil(TokenSet tokenSet) {
    while (getTokenType() != null && !isNewLine() && !tokenSet.contains(getTokenType())) {
      advance();
    }
  }

  protected void advanceUntilEol() {
    advanceUntil(TokenSet.EMPTY);
  }

  protected void errorUntil(TokenSet tokenSet, String message) {
    PsiBuilder.Marker errorMarker = mark();
    advanceUntil(tokenSet);
    errorMarker.error(message);
  }

  protected void errorUntilEol(@NotNull String message) {
    PsiBuilder.Marker errorMarker = mark();
    advanceUntilEol();
    errorMarker.error(message);
  }
  
  protected void errorUntilEof(@NotNull String message) {
    PsiBuilder.Marker errorMarker = mark();
    while (!eof()) {
      advance();
    }
    errorMarker.error(message);
  }

  protected void expectEolOrEof() {
    if (!isNewLine() && !eof()) {
      errorUntilEol("End of line expected");
    }
  }

  protected abstract IElementType getIndentElementType();
  protected abstract IElementType getEolElementType();
}
