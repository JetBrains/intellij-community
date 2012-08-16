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
package com.intellij.application.options.codeStyle.arrangement;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryType;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementModifier;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingType;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingsNode;
import com.intellij.psi.codeStyle.arrangement.model.HierarchicalArrangementSettingsNode;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementMatcherSettings;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementStandardSettingsAware;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Contains various utility methods to be used during showing arrangement settings.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/8/12 9:14 AM
 */
public class ArrangementConfigUtil {

  private ArrangementConfigUtil() {
  }

  /**
   * Allows to answer what new settings are available for a particular {@link ArrangementMatcherSettings arrangement matcher rules}.
   *
   * @param filter    filter to use
   * @param settings  object that encapsulates information about current arrangement matcher settings
   * @return          map which contains information on what new new settings are available at the current situation
   */
  @NotNull
  public static Map<ArrangementSettingType, Collection<?>> buildAvailableOptions(@NotNull ArrangementStandardSettingsAware filter,
                                                                                 @Nullable ArrangementSettingsNode settings)
  {
    Map<ArrangementSettingType, Collection<?>> result = new EnumMap<ArrangementSettingType, Collection<?>>(ArrangementSettingType.class);
    processData(filter, settings, result, ArrangementSettingType.TYPE, ArrangementEntryType.values());
    processData(filter, settings, result, ArrangementSettingType.MODIFIER, ArrangementModifier.values());
    return result;
  }

  private static <T> void processData(@NotNull ArrangementStandardSettingsAware filter,
                                      @Nullable ArrangementSettingsNode settings,
                                      @NotNull Map<ArrangementSettingType, Collection<?>> result,
                                      @NotNull ArrangementSettingType type,
                                      @NotNull T[] values)
  {
    List<T> data = null;
    for (T v : values) {
      if (!isEnabled(v, filter, settings)) {
        continue;
      }
      if (data == null) {
        data = new ArrayList<T>();
      }
      data.add(v);
    }
    if (data != null) {
      result.put(type, data);
    }
  }

  public static boolean isEnabled(@NotNull Object conditionId,
                                  @NotNull ArrangementStandardSettingsAware filter,
                                  @Nullable ArrangementSettingsNode settings)
  {
    if (conditionId instanceof ArrangementEntryType) {
      return filter.isEnabled((ArrangementEntryType)conditionId, settings);
    }
    else if (conditionId instanceof ArrangementModifier) {
      return filter.isEnabled((ArrangementModifier)conditionId, settings);
    }
    else {
      return false;
    }
  }
  
  @Nullable
  public static Point getLocationOnScreen(@NotNull JComponent component) {
    int dx = 0;
    int dy = 0;
    for (Container c = component; c != null; c = c.getParent()) {
      if (c.isShowing()) {
        Point locationOnScreen = c.getLocationOnScreen();
        locationOnScreen.translate(dx, dy);
        return locationOnScreen;
      }
      else {
        Point location = c.getLocation();
        dx += location.x;
        dy += location.y;
      }
    }
    return null;
  }

  public static int getDepth(@NotNull HierarchicalArrangementSettingsNode node) {
    HierarchicalArrangementSettingsNode child = node.getChild();
    return child == null ? 1 : 1 + getDepth(child);
  }

  @NotNull
  public static HierarchicalArrangementSettingsNode getLast(@NotNull HierarchicalArrangementSettingsNode node) {
    HierarchicalArrangementSettingsNode result = node;
    for (HierarchicalArrangementSettingsNode child = node.getChild(); child != null; child = child.getChild()) {
      result = child;
    }
    return result;
  }

  @NotNull
  public static TreeNode getLastBefore(@NotNull TreeNode start, @NotNull TreeNode stop) throws IllegalArgumentException {
    TreeNode result = start;
    for (TreeNode n = start.getParent(); n != stop; n = n.getParent()) {
      if (n == null) {
        throw new IllegalArgumentException(String.format(
          "Non-crossing paths detected - start: %s, stop: %s", new TreePath(start), new TreePath(stop)
        ));
      }
      result = n;
    }
    return result;
  }
  
  public static int distance(@NotNull TreeNode parent, @NotNull TreeNode child) {
    if (parent == child) {
      return 1;
    }
    int result = 1;
    for (TreeNode n = child; n != null && n != parent; n = n.getParent()) {
      result++;
    }
    return result;
  }

  /**
   * @param uiParentNode UI tree node which should hold UI nodes created for representing given settings node
   * @param settingsNode settings node which should be represented at the UI tree denoted by the given UI tree node
   * @return             pair {@code (bottom-most leaf node created; number of rows created)}
   */
  @NotNull
  public static Pair<DefaultMutableTreeNode, Integer> map(@NotNull DefaultMutableTreeNode uiParentNode,
                                                          @NotNull HierarchicalArrangementSettingsNode settingsNode)
  {
    DefaultMutableTreeNode uiNode = null;
    int rowsCreated = 0;
    for (int i = uiParentNode.getChildCount() - 1; i >= 0; i--) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode)uiParentNode.getChildAt(i);
      if (settingsNode.getCurrent().equals(child.getUserObject())) {
        uiNode = child;
        break;
      }
    }
    if (uiNode == null) {
      uiNode = new DefaultMutableTreeNode(settingsNode.getCurrent());
      uiParentNode.add(uiNode);
      rowsCreated++;
    }
    DefaultMutableTreeNode leaf = uiNode;
    HierarchicalArrangementSettingsNode childSettingsNode = settingsNode.getChild();
    if (childSettingsNode != null) {
      Pair<DefaultMutableTreeNode, Integer> pair = map(uiNode, childSettingsNode);
      leaf = pair.first;
      rowsCreated += pair.second;
    }
    return Pair.create(leaf, rowsCreated);
  }
}
