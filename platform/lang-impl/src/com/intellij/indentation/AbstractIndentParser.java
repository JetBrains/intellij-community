package com.intellij.indentation;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: Andrey.Vokin
 * Date: 3/20/12
 */
public abstract class AbstractIndentParser implements PsiParser {
  protected PsiBuilder myBuilder;

  protected int myCurrentIndent;

  protected HashMap<PsiBuilder.Marker, Integer> myIndents;
  protected HashMap<PsiBuilder.Marker, Boolean> myNewLines;

  protected boolean myNewLine = true;

  @NotNull
  public ASTNode parse(IElementType root, PsiBuilder builder) {
    myNewLines = new HashMap<PsiBuilder.Marker, Boolean>();
    myIndents = new HashMap<PsiBuilder.Marker, Integer>();
    myBuilder = builder;
    parseRoot(root);
    return myBuilder.getTreeBuilt();
  }

  protected abstract void parseRoot(IElementType root);

  public PsiBuilder.Marker mark(boolean couldBeRolledBack) {
    final PsiBuilder.Marker marker = myBuilder.mark();
    if (couldBeRolledBack) {
      myIndents.put(marker, myCurrentIndent);
      myNewLines.put(marker, myNewLine);
    }
    return marker;
  }

  public PsiBuilder.Marker mark() {
    return mark(false);
  }

  public void done(@NotNull final PsiBuilder.Marker marker, @NotNull final IElementType elementType) {
    myIndents.remove(marker);
    myNewLines.remove(marker);

    marker.done(elementType);
  }

  public static void collapse(@NotNull final PsiBuilder.Marker marker, @NotNull final IElementType elementType) {
    marker.collapse(elementType);
  }

  protected static void drop(@NotNull final PsiBuilder.Marker marker) {
    marker.drop();
  }

  protected void rollbackTo(@NotNull final PsiBuilder.Marker marker) {
    if (myIndents.get(marker) == null) {
      throw new RuntimeException("Parser can't rollback marker that was created by mark() method, use mark(true) instead");
    }
    myCurrentIndent = myIndents.get(marker);
    myNewLine = myNewLines.get(marker);

    myIndents.remove(marker);
    myNewLines.remove(marker);
    marker.rollbackTo();
  }

  protected boolean eof() {
    return myBuilder.eof();
  }

  protected int getCurrentOffset() {
    return myBuilder.getCurrentOffset();
  }

  public int getCurrentIndent() {
    return myCurrentIndent;
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
    return tokenIn(elementType, tokenSet.getTypes());
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
    return myNewLine;
  }

  public void advance() {
    final String tokenText = myBuilder.getTokenText();
    final int tokenLength = tokenText == null ? 0 : tokenText.length();

    final int whiteSpaceStart = getCurrentOffset() + tokenLength;
    myBuilder.advanceLexer();
    final int whiteSpaceEnd = getCurrentOffset();
    final String whiteSpaceText = myBuilder.getOriginalText().subSequence(whiteSpaceStart, whiteSpaceEnd).toString();

    int i = whiteSpaceText.lastIndexOf('\n');
    if (i >= 0) {
      myCurrentIndent = whiteSpaceText.length() - i - 1;
      myNewLine = true;
    }
    else {
      myNewLine = false;
    }
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

  protected void errorUntilEol(String message) {
    PsiBuilder.Marker errorMarker = mark();
    advanceUntilEol();
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
