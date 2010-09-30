/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.ui.speedSearch;

import com.intellij.openapi.util.Pair;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.PairFunction;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

/**
 * User: spLeaner
 */
public final class SpeedSearchUtil {

  private SpeedSearchUtil() {
  }

  public static void appendFragmentsForSpeedSearch(@NotNull final JComponent speedSearchEnabledComponent, @NotNull final String text,
                                                   @NotNull final SimpleTextAttributes attributes, final boolean selected,
                                                   @NotNull final SimpleColoredComponent simpleColoredComponent) {
    final SpeedSearchSupply speedSearch = SpeedSearchSupply.getSupply(speedSearchEnabledComponent);
    if (speedSearch != null) {
      final Matcher matcher = speedSearch.compareAndGetMatcher(text);
      if (matcher != null) {
        final List<Pair<String, Integer>> searchTerms = new ArrayList<Pair<String, Integer>>();
        for (int i = 0; i < matcher.groupCount(); i++) {
          final int start = matcher.start(i + 1);
          if (searchTerms.size() > 0) {
            final Pair<String, Integer> recent = searchTerms.get(searchTerms.size() - 1);
            if (start == recent.second + recent.first.length()) {
              searchTerms.set(searchTerms.size() - 1, Pair.create(recent.first + matcher.group(i + 1), recent.second));
              continue;
            }
          }

          searchTerms.add(Pair.create(matcher.group(i + 1), start));
        }

        appendFragmentsStrict(text, searchTerms, Font.PLAIN, attributes.getFgColor(),
                              selected ? UIUtil.getTreeSelectionBackground() : UIUtil.getTreeTextBackground(), simpleColoredComponent);
      }
      else {
        simpleColoredComponent.append(text, attributes);
      }
    } else {
      simpleColoredComponent.append(text, attributes);
    }
  }

  public static void appendFragmentsStrict(@NonNls final String text, @NotNull final List<Pair<String, Integer>> toHighlight,
                                           final int style, final Color foreground,
                                           final Color background, final SimpleColoredComponent c) {
    if (text == null) return;
    final SimpleTextAttributes plainAttributes = new SimpleTextAttributes(style, foreground);

    final int[] lastOffset = {0};
    ContainerUtil.process(toHighlight, new Processor<Pair<String, Integer>>() {
      @Override
      public boolean process(Pair<String, Integer> pair) {
        if (pair.second > lastOffset[0]) {
          c.append(text.substring(lastOffset[0], pair.second), new SimpleTextAttributes(style, foreground));
        }

        c.append(text.substring(pair.second, pair.second + pair.first.length()), new SimpleTextAttributes(background,
                                                                                                          foreground, null,
                                                                                                          style |
                                                                                                          SimpleTextAttributes.STYLE_SEARCH_MATCH));
        lastOffset[0] = pair.second + pair.first.length();
        return true;
      }
    });

    if (lastOffset[0] < text.length()) {
      c.append(text.substring(lastOffset[0]), plainAttributes);
    }
  }
}
