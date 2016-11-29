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

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Interface that should be implemented by the language in order to provide inline functionality and possibly
 * participate in inline of elements in other languages this language may reference.
 * @author ven
 * @see InlineHandlers#getInlineHandlers(com.intellij.lang.Language)
 */
public interface InlineHandler {

  ExtensionPointName<InlineHandler> EP_NAME = new ExtensionPointName<>("com.intellij.refactoring.inlineHandler");

  interface Settings {
    /**
     * @return true if as a result of refactoring setup only the reference where refactoring
     * was triggered should be inlined.
     */
    boolean isOnlyOneReferenceToInline();

    /**
     * Special settings for the case when inline cannot be performed due to already reported (by error hint) problem
     */
    Settings CANNOT_INLINE_SETTINGS = new Settings() {
      @Override
      public boolean isOnlyOneReferenceToInline() {
        return false;
      }
    };
  }

  /**
   * @param element element to be inlined
   * @param invokedOnReference true if the user invoked the refactoring on an element reference
   * @param editor in case refactoring has been called in the editor
   * @return <code>Settings</code> object in case refactoring should be performed or null otherwise

   */
  @Nullable Settings prepareInlineElement(@NotNull PsiElement element, @Nullable Editor editor, boolean invokedOnReference);

  /**
   * @param element inlined element
   */
  void removeDefinition(@NotNull PsiElement element, @NotNull Settings settings);

  /**
   * @param element inlined element
   * @param settings
   * @return Inliner instance to be used for inlining references in this language
   */
  @Nullable Inliner createInliner(@NotNull PsiElement element, @NotNull Settings settings);

  interface Inliner {
    /**
     * @param reference reference to inlined element
     * @param referenced inlined element
     * @return set of conflicts inline of this element to the place denoted by reference would incur
     * or null if no conflicts detected.
     */
    @Nullable
    MultiMap<PsiElement, String> getConflicts(@NotNull PsiReference reference, @NotNull PsiElement referenced);

    /**
     * Perform actual inline of element to the point where it is referenced
     * @param usage usage of inlined element
     * @param referenced inlined element
     */
    void inlineUsage(@NotNull UsageInfo usage, @NotNull PsiElement referenced);
  }
}
