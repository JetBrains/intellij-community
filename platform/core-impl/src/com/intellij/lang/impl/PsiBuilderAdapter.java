// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.impl;

import com.intellij.lang.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.diff.FlyweightCapableTreeStructure;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiBuilderAdapter implements PsiBuilder {
  protected final PsiBuilder myDelegate;

  public PsiBuilderAdapter(@NotNull PsiBuilder delegate) {
    myDelegate = delegate;
  }

  @NotNull
  public PsiBuilder getDelegate() {
    return myDelegate;
  }

  @Override
  public Project getProject() {
    return myDelegate.getProject();
  }

  @NotNull
  @Override
  public CharSequence getOriginalText() {
    return myDelegate.getOriginalText();
  }

  @Override
  public void advanceLexer() {
    myDelegate.advanceLexer();
  }

  @Override @Nullable
  public IElementType getTokenType() {
    return myDelegate.getTokenType();
  }

  @Override
  public void setTokenTypeRemapper(final ITokenTypeRemapper remapper) {
    myDelegate.setTokenTypeRemapper(remapper);
  }

  @Override
  public void setWhitespaceSkippedCallback(@Nullable final WhitespaceSkippedCallback callback) {
    myDelegate.setWhitespaceSkippedCallback(callback);
  }

  @Override
  public void remapCurrentToken(IElementType type) {
    myDelegate.remapCurrentToken(type);
  }

  @Override
  public IElementType lookAhead(int steps) {
    return myDelegate.lookAhead(steps);
  }

  @Override
  public IElementType rawLookup(int steps) {
    return myDelegate.rawLookup(steps);
  }

  @Override
  public int rawTokenTypeStart(int steps) {
    return myDelegate.rawTokenTypeStart(steps);
  }

  @Override
  public int rawTokenIndex() {
    return myDelegate.rawTokenIndex();
  }

  @Override @Nullable @NonNls
  public String getTokenText() {
    return myDelegate.getTokenText();
  }

  @Override
  public int getCurrentOffset() {
    return myDelegate.getCurrentOffset();
  }

  @NotNull
  @Override
  public Marker mark() {
    return myDelegate.mark();
  }

  @Override
  public void error(final String messageText) {
    myDelegate.error(messageText);
  }

  @Override
  public boolean eof() {
    return myDelegate.eof();
  }

  @NotNull
  @Override
  public ASTNode getTreeBuilt() {
    return myDelegate.getTreeBuilt();
  }

  @NotNull
  @Override
  public FlyweightCapableTreeStructure<LighterASTNode> getLightTree() {
    return myDelegate.getLightTree();
  }

  @Override
  public void setDebugMode(final boolean dbgMode) {
    myDelegate.setDebugMode(dbgMode);
  }

  @Override
  public void enforceCommentTokens(@NotNull final TokenSet tokens) {
    myDelegate.enforceCommentTokens(tokens);
  }

  @Override @Nullable
  public LighterASTNode getLatestDoneMarker() {
    return myDelegate.getLatestDoneMarker();
  }

  @Override @Nullable
  public <T> T getUserData(@NotNull final Key<T> key) {
    return myDelegate.getUserData(key);
  }

  @Override
  public <T> void putUserData(@NotNull final Key<T> key, @Nullable final T value) {
    myDelegate.putUserData(key, value);
  }

  @SuppressWarnings("deprecation")
  @Override
  public <T> T getUserDataUnprotected(@NotNull final Key<T> key) {
    return myDelegate.getUserDataUnprotected(key);
  }

  @SuppressWarnings("deprecation")
  @Override
  public <T> void putUserDataUnprotected(@NotNull final Key<T> key, @Nullable final T value) {
    myDelegate.putUserDataUnprotected(key, value);
  }
}
