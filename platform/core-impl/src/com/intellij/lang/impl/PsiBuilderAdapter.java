// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  public @NotNull PsiBuilder getDelegate() {
    return myDelegate;
  }

  @Override
  public Project getProject() {
    return myDelegate.getProject();
  }

  @Override
  public @NotNull CharSequence getOriginalText() {
    return myDelegate.getOriginalText();
  }

  @Override
  public void advanceLexer() {
    myDelegate.advanceLexer();
  }

  @Override
  public @Nullable IElementType getTokenType() {
    return myDelegate.getTokenType();
  }

  @Override
  public void setTokenTypeRemapper(final ITokenTypeRemapper remapper) {
    myDelegate.setTokenTypeRemapper(remapper);
  }

  @Override
  public void setWhitespaceSkippedCallback(final @Nullable WhitespaceSkippedCallback callback) {
    myDelegate.setWhitespaceSkippedCallback(callback);
  }

  @Override
  public void remapCurrentToken(@NotNull IElementType type) {
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

  @Override
  public @Nullable @NonNls String getTokenText() {
    return myDelegate.getTokenText();
  }

  @Override
  public int getCurrentOffset() {
    return myDelegate.getCurrentOffset();
  }

  @Override
  public @NotNull Marker mark() {
    return myDelegate.mark();
  }

  @Override
  public void error(final @NotNull String messageText) {
    myDelegate.error(messageText);
  }

  @Override
  public boolean eof() {
    return myDelegate.eof();
  }

  @Override
  public @NotNull ASTNode getTreeBuilt() {
    return myDelegate.getTreeBuilt();
  }

  @Override
  public @NotNull FlyweightCapableTreeStructure<LighterASTNode> getLightTree() {
    return myDelegate.getLightTree();
  }

  @Override
  public void setDebugMode(final boolean dbgMode) {
    myDelegate.setDebugMode(dbgMode);
  }

  @Override
  public void enforceCommentTokens(final @NotNull TokenSet tokens) {
    myDelegate.enforceCommentTokens(tokens);
  }

  @Override
  public @Nullable LighterASTNode getLatestDoneMarker() {
    return myDelegate.getLatestDoneMarker();
  }

  @Override
  public @Nullable <T> T getUserData(final @NotNull Key<T> key) {
    return myDelegate.getUserData(key);
  }

  @Override
  public <T> void putUserData(final @NotNull Key<T> key, final @Nullable T value) {
    myDelegate.putUserData(key, value);
  }

  @SuppressWarnings("deprecation")
  @Override
  public <T> T getUserDataUnprotected(final @NotNull Key<T> key) {
    return myDelegate.getUserDataUnprotected(key);
  }

  @SuppressWarnings("deprecation")
  @Override
  public <T> void putUserDataUnprotected(final @NotNull Key<T> key, final @Nullable T value) {
    myDelegate.putUserDataUnprotected(key, value);
  }
}
