// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle.arrangement;

import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Encapsulates language-specific rearrangement logic.
 * <p/>
 * Implementations of this interface are expected to be thread-safe.
 * 
 * @author Denis Zhdanov
 * @param <E>   entry type
 */
public interface Rearranger<E extends ArrangementEntry> {

  LanguageExtension<Rearranger<?>> EXTENSION = new LanguageExtension<>("com.intellij.lang.rearranger");

  /**
   * Tries to wrap given element into arrangement entry at the target context.
   * <p/>
   * This is useful in a situation when new element is generated and we're deciding where to insert it (e.g. new field is
   * generated and we want to insert it according to the arrangement rules like 'fields before methods').
   * 
   *
   * @param element   element to wrap into format eligible for further processing by arrangement engine
   * @param settings  arrangement settings to use. The primary idea is to make the rearranger aware about
   *                  {@link StdArrangementTokens.Grouping grouping rules} (if any). E.g. it's not worth to process java method bodies
   *                  in order to build method dependency graph if no such grouping rule is defined
   * @return          arrangement entry for the given element if it's possible to perform the mapping and list of arrangement entries
   *                  available at the given context plus newly created entry for the given element;
   *                  {@code null} otherwise
   */
  @Nullable
  Pair<E, List<E>> parseWithNew(@NotNull PsiElement root,
                                @Nullable Document document,
                                @NotNull Collection<? extends TextRange> ranges,
                                @NotNull PsiElement element,
                                @NotNull ArrangementSettings settings);
  
  /**
   * Allows to build rearranger-interested data for the given element.
   *
   * @param root      root element which children should be parsed for the rearrangement
   * @param document  document which corresponds to the target PSI tree
   * @param ranges    target offsets ranges to use for filtering given root's children
   * @param settings  arrangement settings to use. The primary idea is to make the rearranger aware about
   *                  {@link StdArrangementTokens.Grouping grouping rules} (if any). E.g. it's not worth to process java method bodies
   *                  in order to build method dependency graph if no such grouping rule is defined
   * @return          given root's children which are subject for further rearrangement
   */
  @NotNull
  List<E> parse(@NotNull PsiElement root,
                @Nullable Document document,
                @NotNull Collection<? extends TextRange> ranges,
                @NotNull ArrangementSettings settings);

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


  /**
   * @return serializer to save {@link ArrangementSettings arrangement settings}.
   * Serializer is expected to be lazy and don't save
   * {@link com.intellij.psi.codeStyle.arrangement.std.ArrangementStandardSettingsAware#getDefaultSettings() default settings}.
   * <p/>
   * @see DefaultArrangementSettingsSerializer
   */
  @NotNull
  ArrangementSettingsSerializer getSerializer();
}
