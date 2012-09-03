/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle.arrangement;

import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Encapsulates language-specific rearrangement logic.
 * <p/>
 * Implementations of this interface are expected to be thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 7/16/12 3:23 PM
 * @param <E>   entry type
 */
public interface Rearranger<E extends ArrangementEntry> {

  LanguageExtension<Rearranger<?>> EXTENSION = new LanguageExtension<Rearranger<?>>("com.intellij.lang.rearranger");

  /**
   * Allows to build rearranger-interested data for the given element.
   *
   * @param root      root element which children should be parsed for the rearrangement
   * @param document  document which corresponds to the target PSI tree
   * @param ranges    target offsets ranges to use for filtering given root's children
   * @return       given root's children which are subject for further rearrangement
   */
  @NotNull
  Collection<E> parse(@NotNull PsiElement root, @NotNull Document document, @NotNull Collection<TextRange> ranges);

  /**
   * Allows to answer how many blank lines should be inserted before the target arrangement entry which position is changed.
   * 
   * @param settings  code style settings to use (it's assumed that returned result is derived from 'blank lines' code style settings)
   * @param parent    target entry's parent (if available)
   * @param previous  previous entry (if available)
   * @param target    target entry which blank lines number the caller is interested in
   * @return          number of blank lines to insert before the target entry;
   *                  negative as an indication that no blank lines adjustment is necessary
   */
  int getBlankLines(@NotNull CodeStyleSettings settings, @Nullable E parent, @Nullable E previous, @NotNull E target);
}
