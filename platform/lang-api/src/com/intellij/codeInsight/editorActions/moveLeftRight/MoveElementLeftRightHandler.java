/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.editorActions.moveLeftRight;

import com.intellij.lang.LanguageExtension;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Instances of this class implement language-specific logic of 'move element left/right' actions
 */
public abstract class MoveElementLeftRightHandler {
  public static final LanguageExtension<MoveElementLeftRightHandler> EXTENSION = 
    new LanguageExtension<MoveElementLeftRightHandler>("com.intellij.moveLeftRightHandler");
  
  /**
   * Returns a list of PSI elements on which 'move element left/right' actions should operate for a given context element.
   * Should return <code>null</code> if the actions are not applicable in given context.
   */
  @Nullable
  public abstract PsiElement[] getElementListInContext(@NotNull PsiElement element);
}
