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

package com.intellij.lang.refactoring;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public abstract class InlineActionHandler {
  public static final ExtensionPointName<InlineActionHandler> EP_NAME = ExtensionPointName.create("com.intellij.inlineActionHandler");

  /**
   * Fast check to see if the handler can possibly inline the element. Called from action update.
   *
   * @param element the element under caret
   * @return true if the handler can possibly inline the element (with some additional conditions), false otherwise.
   */
  public boolean isEnabledOnElement(PsiElement element) {
    return canInlineElement(element);
  }

  public boolean isEnabledOnElement(PsiElement element, @Nullable Editor editor) {
    return isEnabledOnElement(element);
  }

  public abstract boolean isEnabledForLanguage(Language l);

  public abstract boolean canInlineElement(PsiElement element);

  public boolean canInlineElementInEditor(PsiElement element) {
    return canInlineElement(element);
  }

  public abstract void inlineElement(Project project, Editor editor, PsiElement element);
}
