/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.codeInsight.template;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class TextResult implements Result{
  private final @NlsSafe String myText;

  public TextResult(@NotNull @NlsSafe String text) {
    myText = text;
  }

  @NotNull
  public @NlsSafe String getText() {
    return myText;
  }

  @Override
  public boolean equalsToText(String text, PsiElement context) {
    return text.equals(myText);
  }

  public String toString() {
    return myText;
  }

  @Override
  public void handleFocused(final PsiFile psiFile, final Document document, final int segmentStart, final int segmentEnd) {
  }
}