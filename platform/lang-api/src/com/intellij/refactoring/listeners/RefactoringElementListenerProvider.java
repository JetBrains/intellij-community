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
package com.intellij.refactoring.listeners;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * Refactorings invoke {@link #getListener(com.intellij.psi.PsiElement)} of registered
 * {@linkplain RefactoringElementListenerProvider} before particular element is subjected to refactoring.
 * @author dsl
 */
public interface RefactoringElementListenerProvider {
  ExtensionPointName<RefactoringElementListenerProvider> EP_NAME = ExtensionPointName.create("com.intellij.refactoring.elementListenerProvider");

  /**
   *
   * Should return a listener for particular element. Invoked in read action.
   */
  @Nullable RefactoringElementListener getListener(PsiElement element);
}
