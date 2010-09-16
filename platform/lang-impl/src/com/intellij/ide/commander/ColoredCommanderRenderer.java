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

package com.intellij.ide.commander;

import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.SpeedSearchBase;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

final class ColoredCommanderRenderer extends ColoredListCellRenderer {
  private final CommanderPanel myCommanderPanel;

  public ColoredCommanderRenderer(@NotNull final CommanderPanel commanderPanel) {
    myCommanderPanel = commanderPanel;
  }

  public Component getListCellRendererComponent(final JList list, final Object value, final int index, boolean selected, boolean hasFocus){
    hasFocus = selected; // border around inactive items 

    if (!myCommanderPanel.isActive()) {
      selected = false;
    }

    return super.getListCellRendererComponent(list, value, index, selected, hasFocus);
  }

  protected void customizeCellRenderer(final JList list, final Object value, final int index, final boolean selected, final boolean hasFocus) {
    Color color = UIUtil.getListForeground();
    SimpleTextAttributes attributes = null;
    String locationString = null;

    if (value instanceof NodeDescriptor) {
      final NodeDescriptor descriptor = (NodeDescriptor)value;
      setIcon(descriptor.getClosedIcon());
      final Color elementColor = descriptor.getColor();
      
      if (elementColor != null) {
        color = elementColor;
      }

      if (descriptor instanceof AbstractTreeNode) {
        final AbstractTreeNode treeNode = (AbstractTreeNode)descriptor;
        final TextAttributesKey attributesKey = treeNode.getAttributesKey();

        if (attributesKey != null) {
          final TextAttributes textAttributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(attributesKey);

          if (textAttributes != null) attributes =  SimpleTextAttributes.fromTextAttributes(textAttributes);
        }
        locationString = treeNode.getLocationString();
      }
    }

    if(attributes == null) attributes = new SimpleTextAttributes(Font.PLAIN, color);
    final String text = value.toString();

    if (myCommanderPanel.isEnableSearchHighlighting() && SpeedSearchBase.hasActiveSpeedSearch(list)) {
      final SpeedSearchBase.SpeedSearchComparator comparator = myCommanderPanel.getListSpeedSearch().getComparator();
      final String recentSearchText = comparator.getRecentSearchText();
      if (recentSearchText != null && recentSearchText.length() > 0 && comparator.doCompare(recentSearchText, text)) {
        final Matcher matcher = comparator.getRecentSearchMatcher();
        final List<Pair<String, Integer>> searchTerms = new ArrayList<Pair<String, Integer>>();
        for (int i = 0; i < matcher.groupCount(); i++) {
          final int start = matcher.start(i + 1);
          if (searchTerms.size() > 0) {
            final Pair<String, Integer> recent = searchTerms.get(searchTerms.size() - 1);
            if (start == recent.second + recent.first.length()) {
              searchTerms.set(searchTerms.size() - 1, Pair.create(recent.first + matcher.group(i+1), recent.second));
              continue;
            }
          }

          searchTerms.add(Pair.create(matcher.group(i+1), start));
        }

        SearchUtil.appendFragmentsStrict(text, searchTerms, Font.PLAIN, attributes.getFgColor(),
                                         selected ? UIUtil.getTreeSelectionBackground() : UIUtil.getTreeTextBackground(), this);
      } else {
        append(text != null ? text : "", attributes);
      }
    }
    else {
      append(text != null ? text : "", attributes);
    }

    if (locationString != null && locationString.length() > 0) {
      append(" (" + locationString + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }
}
