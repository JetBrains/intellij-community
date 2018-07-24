// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.indentation;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.impl.DelegateMarker;
import com.intellij.lang.impl.PsiBuilderAdapter;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

public class IndentPsiBuilder extends PsiBuilderAdapter {
  protected boolean myNewLine = true;
  protected int myCurrentIndent;

  protected HashMap<Marker, Integer> myIndents = new HashMap<>();
  protected HashMap<PsiBuilder.Marker, Boolean> myNewLines = new HashMap<>();

  public IndentPsiBuilder(PsiBuilder delegate) {
    super(delegate);
  }

  @Override
  public void advanceLexer() {
    final String tokenText = myDelegate.getTokenText();
    final int tokenLength = tokenText == null ? 0 : tokenText.length();

    final int whiteSpaceStart = getCurrentOffset() + tokenLength;
    myDelegate.advanceLexer();
    final int whiteSpaceEnd = getCurrentOffset();
    final String whiteSpaceText = myDelegate.getOriginalText().subSequence(whiteSpaceStart, whiteSpaceEnd).toString();

    int i = whiteSpaceText.lastIndexOf('\n');
    if (i >= 0) {
      myCurrentIndent = whiteSpaceText.length() - i - 1;
      myNewLine = true;
    }
    else {
      myNewLine = false;
    }
  }

  @NotNull
  @Override
  public Marker mark() {
    Marker marker = super.mark();
    return createDelegateMarker(marker);
  }

  @NotNull
  public Marker markWithRollbackPossibility() {
    Marker marker = super.mark();
    Marker result = createDelegateMarker(marker);
    myIndents.put(result, myCurrentIndent);
    myNewLines.put(result, myNewLine);
    return result;
  }

  protected Marker createDelegateMarker(@NotNull Marker delegate) {
    return new MyMarker(delegate);
  }

  private void unregisterMarker(Marker marker) {
    myIndents.remove(marker);
    myNewLines.remove(marker);

  }

  public boolean isNewLine() {
    return myNewLine;
  }

  public int getCurrentIndent() {
    return myCurrentIndent;
  }

  public void recalculateCurrentIndent(@NotNull IElementType eolElementType, @NotNull IElementType indentElementType) {
    int i = 0;
    int firstIndentOffset = myDelegate.getCurrentOffset();
    while (myDelegate.rawLookup(i) != null && myDelegate.rawLookup(i) != eolElementType) {
      firstIndentOffset = myDelegate.rawTokenTypeStart(i);
      i--;
    }
    int lastIndentOffset = firstIndentOffset;
    i++;
    while (myDelegate.rawLookup(i) == indentElementType) {
      i++;
      lastIndentOffset = myDelegate.rawTokenTypeStart(i);
    }
    myCurrentIndent = lastIndentOffset - firstIndentOffset;
  }


  protected class MyMarker extends DelegateMarker {
    public MyMarker(@NotNull Marker delegate) {
      super(delegate);
    }

    @Override
    public void rollbackTo() {
      if (myIndents.containsKey(this)) {
        myCurrentIndent = myIndents.get(this);
      }
      if (myNewLines.containsKey(this)) {
        myNewLine = myNewLines.get(this);
      }

      unregisterMarker(this);
      super.rollbackTo();
    }

    @Override
    public void done(@NotNull IElementType type) {
      unregisterMarker(this);
      super.done(type);
    }

    @Override
    public void drop() {
      unregisterMarker(this);
      super.drop();
    }

    @NotNull
    @Override
    public Marker precede() {
      unregisterMarker(this);
      return super.precede();
    }

    @Override
    public void collapse(@NotNull IElementType type) {
      unregisterMarker(this);
      super.collapse(type);
    }

    @Override
    public void doneBefore(@NotNull IElementType type, @NotNull Marker before) {
      unregisterMarker(this);
      super.doneBefore(type, before);
    }

    @Override
    public void doneBefore(@NotNull IElementType type, @NotNull Marker before, String errorMessage) {
      unregisterMarker(this);
      super.doneBefore(type, before, errorMessage);
    }
  }
}
