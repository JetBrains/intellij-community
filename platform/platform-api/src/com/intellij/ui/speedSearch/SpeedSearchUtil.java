// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.speedSearch;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.text.Matcher;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class SpeedSearchUtil {

  private SpeedSearchUtil() {
  }

  public static @NotNull String getDefaultHardSeparators() {
    return "\u001F";
  }

  public static void applySpeedSearchHighlighting(@NotNull JComponent speedSearchEnabledComponent,
                                                  @NotNull SimpleColoredComponent coloredComponent,
                                                  boolean mainTextOnly,
                                                  boolean selected) {
    SpeedSearchSupply speedSearch = SpeedSearchSupply.getSupply(speedSearchEnabledComponent);
    if (speedSearch == null) return;
    applySpeedSearchHighlighting(speedSearch, coloredComponent, mainTextOnly, selected);
  }

  public static void applySpeedSearchHighlighting(@NotNull SpeedSearchSupply speedSearch,
                                                  @NotNull SimpleColoredComponent coloredComponent,
                                                  boolean mainTextOnly,
                                                  boolean selected) {
    // The bad thing is that SpeedSearch model is decoupled from UI presentation so we don't know the real matched text.
    // Our best guess is to get string from the ColoredComponent. We can only provide main-text-only option.
    Iterable<TextRange> ranges = speedSearch.matchingFragments(coloredComponent.getCharSequence(mainTextOnly).toString());
    applySpeedSearchHighlighting(coloredComponent, ranges, selected);
  }

  public static void applySpeedSearchHighlighting(@NotNull SimpleColoredComponent coloredComponent,
                                                  @Nullable Iterable<TextRange> ranges,
                                                  boolean selected) {
    Iterator<TextRange> rangesIterator = ranges != null ? ranges.iterator() : null;
    if (rangesIterator == null || !rangesIterator.hasNext()) return;
    Color bg = UIUtil.getTreeBackground(selected, true);

    SimpleColoredComponent.ColoredIterator coloredIterator = coloredComponent.iterator();
    TextRange range = rangesIterator.next();
    main: while (coloredIterator.hasNext()) {
      coloredIterator.next();
      int offset = coloredIterator.getOffset();
      int endOffset = coloredIterator.getEndOffset();
      if (!range.intersectsStrict(offset, endOffset)) continue;
      SimpleTextAttributes attributes = coloredIterator.getTextAttributes();
      SimpleTextAttributes highlighted = new SimpleTextAttributes(bg, attributes.getFgColor(), null, attributes.getStyle() | SimpleTextAttributes.STYLE_SEARCH_MATCH);
      do {
        if (range.getStartOffset() > offset) {
          offset = coloredIterator.split(range.getStartOffset() - offset, attributes);
        }
        if (range.getEndOffset() <= endOffset) {
          offset = coloredIterator.split(range.getEndOffset() - offset, highlighted);
          if (rangesIterator.hasNext()) {
            range = rangesIterator.next();
          }
          else {
            break main;
          }
        }
        else {
          coloredIterator.split(endOffset - offset, highlighted);
          continue main;
        }
      }
      while (range.intersectsStrict(offset, endOffset));
    }
  }

  public static void appendFragmentsForSpeedSearch(@NotNull JComponent speedSearchEnabledComponent,
                                                   @NotNull @NlsContexts.Label String text,
                                                   @NotNull SimpleTextAttributes attributes,
                                                   boolean selected,
                                                   @NotNull SimpleColoredComponent simpleColoredComponent) {
    final SpeedSearchSupply speedSearch = SpeedSearchSupply.getSupply(speedSearchEnabledComponent);
    if (speedSearch != null) {
      final Iterable<TextRange> fragments = speedSearch.matchingFragments(text);
      if (fragments != null) {
        appendSpeedSearchColoredFragments(simpleColoredComponent, text, fragments, attributes, selected);
        return;
      }
    }
    simpleColoredComponent.append(text, attributes);
  }

  public static void appendColoredFragmentForMatcher(@NotNull @NlsContexts.Label String text,
                                                     SimpleColoredComponent component,
                                                     @NotNull final SimpleTextAttributes attributes,
                                                     @Nullable Matcher matcher,
                                                     Color selectedBg,
                                                     boolean selected) {
    if (!(matcher instanceof MinusculeMatcher) || (Registry.is("ide.highlight.match.in.selected.only") && !selected)) {
      component.append(text, attributes);
      return;
    }

    final Iterable<TextRange> iterable = ((MinusculeMatcher)matcher).matchingFragments(text);
    component.setDynamicSearchMatchHighlighting(iterable != null);
    if (iterable != null) {
      final Color fg = attributes.getFgColor();
      final int style = attributes.getStyle();
      final SimpleTextAttributes plain = new SimpleTextAttributes(style, fg);
      final SimpleTextAttributes highlighted = new SimpleTextAttributes(selectedBg, fg, null, style | SimpleTextAttributes.STYLE_SEARCH_MATCH);
      appendColoredFragments(component, text, iterable, plain, highlighted);
    }
    else {
      component.append(text, attributes);
    }
  }

  public static void appendSpeedSearchColoredFragments(@NotNull SimpleColoredComponent simpleColoredComponent,
                                                       @NotNull @NlsContexts.Label String text,
                                                       @NotNull Iterable<? extends TextRange> colored,
                                                       @NotNull SimpleTextAttributes attributes,
                                                       boolean selected) {
    final Color fg = attributes.getFgColor();
    final Color bg = UIUtil.getTreeBackground(selected, true);
    final int style = attributes.getStyle();
    final SimpleTextAttributes plain = new SimpleTextAttributes(style, fg);
    final SimpleTextAttributes highlighted = new SimpleTextAttributes(bg, fg, null, style | SimpleTextAttributes.STYLE_SEARCH_MATCH);
    appendColoredFragments(simpleColoredComponent, text, colored, plain, highlighted);
  }

  public static void appendColoredFragments(final SimpleColoredComponent simpleColoredComponent,
                                            final @Nls String text,
                                            Iterable<? extends TextRange> colored,
                                            final SimpleTextAttributes plain, final SimpleTextAttributes highlighted) {
    final List<Pair<String, Integer>> searchTerms = new ArrayList<>();
    for (TextRange fragment : colored) {
      searchTerms.add(Pair.create(fragment.substring(text), fragment.getStartOffset()));
    }

    int lastOffset = 0;
    for (Pair<String, Integer> pair : searchTerms) {
      if (pair.second > lastOffset) {
        simpleColoredComponent.append(text.substring(lastOffset, pair.second), plain);
      }

      simpleColoredComponent.append(text.substring(pair.second, pair.second + pair.first.length()), highlighted);
      lastOffset = pair.second + pair.first.length();
    }

    if (lastOffset < text.length()) {
      simpleColoredComponent.append(text.substring(lastOffset), plain);
    }
  }

  public static void applySpeedSearchHighlightingFiltered(JTree tree, Object value, ColoredTreeCellRenderer coloredTreeCellRenderer, boolean mainTextOnly, boolean selected) {
    SpeedSearchSupply speedSearch = SpeedSearchSupply.getSupply(tree);
    if (speedSearch != null && !speedSearch.isObjectFilteredOut(value)){
      applySpeedSearchHighlighting(tree, coloredTreeCellRenderer, mainTextOnly, selected);
    }
  }
}
