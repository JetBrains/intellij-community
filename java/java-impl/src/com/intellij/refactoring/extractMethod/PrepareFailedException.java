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
package com.intellij.refactoring.extractMethod;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

/**
 * @author dsl
 */
public class PrepareFailedException extends Exception {
  private final PsiFile myContainingFile;
  private final TextRange myTextRange;

  public PrepareFailedException(@NlsContexts.DialogMessage String message, PsiElement errorElement) {
    super(message);
    myContainingFile = errorElement.getContainingFile();
    myTextRange = errorElement.getTextRange();
  }

  @Override
  public @NlsContexts.DialogMessage String getMessage() {
    //noinspection HardCodedStringLiteral
    return super.getMessage();
  }

  public PsiFile getFile() {
    return myContainingFile;
  }

  public TextRange getTextRange() {
    return myTextRange;
  }
}
