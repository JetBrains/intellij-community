// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine;

import com.intellij.debugger.SourcePosition;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

abstract class RemappedSourcePosition extends SourcePosition {
  private SourcePosition myDelegate;
  private boolean myMapped = false;

  RemappedSourcePosition(@NotNull SourcePosition delegate) {
    myDelegate = delegate;
  }

  @Override
  @NotNull
  public PsiFile getFile() {
    return myDelegate.getFile();
  }

  @Override
  public PsiElement getElementAt() {
    checkRemap();
    return myDelegate.getElementAt();
  }

  @Override
  public int getLine() {
    checkRemap();
    return myDelegate.getLine();
  }

  private void checkRemap() {
    if (!myMapped) {
      myMapped = true;
      myDelegate = mapDelegate(myDelegate);
    }
  }

  @Override
  public int getOffset() {
    checkRemap();
    return myDelegate.getOffset();
  }

  public abstract SourcePosition mapDelegate(SourcePosition original);

  @Override
  public Editor openEditor(boolean requestFocus) {
    return myDelegate.openEditor(requestFocus);
  }

  @Override
  public boolean equals(Object o) {
    return myDelegate.equals(o);
  }

  @Override
  public void navigate(boolean requestFocus) {
    myDelegate.navigate(requestFocus);
  }

  @Override
  public boolean canNavigate() {
    return myDelegate.canNavigate();
  }

  @Override
  public boolean canNavigateToSource() {
    return myDelegate.canNavigateToSource();
  }
}
