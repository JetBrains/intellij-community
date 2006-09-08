/*
 * Copyright 2000-2006 JetBrains s.r.o.
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
 *
 */

package com.intellij.psi;

import java.lang.ref.SoftReference;

/**
 * @author mike
 */
public class PsiInvalidElementAccessException extends RuntimeException {
  private final SoftReference<PsiElement> myElementReference;  // to prevent leaks, exceptions are stored in IdeaLogger

  public PsiInvalidElementAccessException(PsiElement element) {
    myElementReference = new SoftReference<PsiElement>(element);
  }

  public PsiInvalidElementAccessException(PsiElement element, String message) {
    super(message);
    myElementReference = new SoftReference<PsiElement>(element);
  }

  public PsiInvalidElementAccessException(PsiElement element, String message, Throwable cause) {
    super(message, cause);
    myElementReference = new SoftReference<PsiElement>(element);
  }

  public PsiInvalidElementAccessException(PsiElement element, Throwable cause) {
    super(cause);
    myElementReference = new SoftReference<PsiElement>(element);
  }
}
