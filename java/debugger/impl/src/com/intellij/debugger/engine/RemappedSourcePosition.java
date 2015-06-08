/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.debugger.engine;

import com.intellij.debugger.SourcePosition;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author egor
 */
abstract class RemappedSourcePosition extends SourcePosition {
  private SourcePosition myDelegate;
  private boolean myMapped = false;

  public RemappedSourcePosition(SourcePosition delegate) {
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
