// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.OpaquePanel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author Alexander Lobas
 */
public abstract class PluginsGroupComponent extends JBPanelWithEmptyText {

  private final @NotNull EventHandler myEventHandler;
  private final List<UIPluginGroup> myGroups = new ArrayList<>();

  public PluginsGroupComponent(@NotNull EventHandler eventHandler) {
    super(new PluginListLayout());
    myEventHandler = eventHandler;

    myEventHandler.connect(this);

    setOpaque(true);
    setBackground(PluginManagerConfigurable.MAIN_BG_COLOR);
  }

  protected abstract @NotNull ListPluginComponent createListComponent(@NotNull IdeaPluginDescriptor descriptor, @NotNull PluginsGroup group);

  public final @NotNull List<UIPluginGroup> getGroups() {
    return Collections.unmodifiableList(myGroups);
  }

  public void setSelectionListener(@Nullable Consumer<? super PluginsGroupComponent> listener) {
    myEventHandler.setSelectionListener(listener);
  }

  public final @NotNull List<ListPluginComponent> getSelection() {
    return myEventHandler.getSelection();
  }

  public void setSelection(@NotNull ListPluginComponent component) {
    myEventHandler.setSelection(component);
  }

  public void setSelection(@NotNull List<ListPluginComponent> components) {
    myEventHandler.setSelection(components);
  }

  public void addGroup(@NotNull PluginsGroup group) {
    addGroup(group, -1);
  }

  public void addGroup(@NotNull PluginsGroup group, int groupIndex) {
    addGroup(group, group.descriptors, groupIndex);
  }

  public void addLazyGroup(@NotNull PluginsGroup group, @NotNull JScrollBar scrollBar, int gapSize, @NotNull Runnable uiCallback) {
    if (group.descriptors.size() <= gapSize) {
      addGroup(group);
    }
    else {
      addGroup(group, group.descriptors.subList(0, gapSize), -1);
      AdjustmentListener listener = new AdjustmentListener() {
        @Override
        public void adjustmentValueChanged(AdjustmentEvent e) {
          if ((scrollBar.getValue() + scrollBar.getVisibleAmount()) >= scrollBar.getMaximum()) {
            int fromIndex = group.ui.plugins.size();
            int toIndex = Math.min(fromIndex + gapSize, group.descriptors.size());
            ListPluginComponent lastComponent = group.ui.plugins.get(fromIndex - 1);
            int uiIndex = getComponentIndex(lastComponent);
            int eventIndex = myEventHandler.getCellIndex(lastComponent);
            try {
              PluginLogo.startBatchMode();
              addToGroup(group, group.descriptors.subList(fromIndex, toIndex), uiIndex, eventIndex);
            }
            finally {
              PluginLogo.endBatchMode();
            }

            if (group.descriptors.size() == group.ui.plugins.size()) {
              scrollBar.removeAdjustmentListener(this);
              group.clearCallback = null;
            }

            uiCallback.run();
          }
        }
      };
      group.clearCallback = () -> scrollBar.removeAdjustmentListener(listener);
      scrollBar.addAdjustmentListener(listener);
    }
  }

  public static final Color SECTION_HEADER_FOREGROUND =
    JBColor.namedColor("Plugins.SectionHeader.foreground", new JBColor(0x787878, 0x999999));
  private static final Color SECTION_HEADER_BACKGROUND =
    JBColor.namedColor("Plugins.SectionHeader.background", new JBColor(0xF7F7F7, 0x3C3F41));

  private void addGroup(@NotNull PluginsGroup group, @NotNull List<? extends IdeaPluginDescriptor> descriptors, int groupIndex) {
    UIPluginGroup uiGroup = new UIPluginGroup();
    group.ui = uiGroup;
    myGroups.add(groupIndex == -1 ? myGroups.size() : groupIndex, uiGroup);

    OpaquePanel panel = new OpaquePanel(new BorderLayout(), SECTION_HEADER_BACKGROUND);
    panel.setBorder(JBUI.Borders.empty(4, 10));

    JLabel title = new JLabel(group.title) {
      @Override
      public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        Container parent = getParent();
        Insets insets = parent.getInsets();
        size.width = Math.min(parent.getWidth() - insets.left - insets.right -
                              (parent.getComponentCount() == 2 ? parent.getComponent(1).getWidth() + JBUIScale.scale(20) : 0), size.width);
        return size;
      }

      @Override
      public String getToolTipText() {
        return super.getPreferredSize().width > getWidth() ? super.getToolTipText() : null;
      }
    };
    title.setToolTipText(group.title);
    title.setForeground(SECTION_HEADER_FOREGROUND);
    panel.add(title, BorderLayout.WEST);
    group.titleLabel = title;

    if (group.rightAction != null) {
      panel.add(group.rightAction, BorderLayout.EAST);
    }
    else if (!ContainerUtil.isEmpty(group.rightActions)) {
      JPanel actions = new NonOpaquePanel(new HorizontalLayout(JBUIScale.scale(5)));
      panel.add(actions, BorderLayout.EAST);

      for (JComponent action : group.rightActions) {
        actions.add(action);
      }
    }

    int index;
    int eventIndex;

    if (groupIndex == 0) {
      add(panel, 0);
      index = 1;
      eventIndex = 0;
    }
    else if (groupIndex == -1) {
      add(panel);
      index = eventIndex = -1;
    }
    else {
      assert groupIndex < myGroups.size();
      index = getComponentIndex(myGroups.get(groupIndex + 1).panel);
      assert index != -1;
      add(panel, index++);

      eventIndex = getEventIndexForGroup(groupIndex + 1);
    }

    uiGroup.panel = panel;

    addToGroup(group, descriptors, index, eventIndex);
  }

  private int getEventIndexForGroup(int groupIndex) {
    for (int i = groupIndex; i >= 0; i--) {
      List<ListPluginComponent> plugins = myGroups.get(i).plugins;
      if (!plugins.isEmpty()) {
        return myEventHandler.getCellIndex(plugins.get(0));
      }
    }
    return -1;
  }

  private void addToGroup(@NotNull PluginsGroup group,
                          @NotNull List<? extends IdeaPluginDescriptor> descriptors,
                          int index,
                          int eventIndex) {
    for (IdeaPluginDescriptor descriptor : descriptors) {
      ListPluginComponent pluginComponent = createListComponent(descriptor, group);
      group.ui.plugins.add(pluginComponent);
      add(pluginComponent, index);
      myEventHandler.addCell(pluginComponent, eventIndex);
      pluginComponent.setListeners(myEventHandler);
      if (index != -1) {
        index++;
      }
      if (eventIndex != -1) {
        eventIndex++;
      }
    }
  }

  public void addToGroup(@NotNull PluginsGroup group, @NotNull IdeaPluginDescriptor descriptor) {
    int index = group.addWithIndex(descriptor);
    ListPluginComponent anchor = null;
    int uiIndex = -1;

    if (index == group.ui.plugins.size()) {
      int groupIndex = myGroups.indexOf(group.ui);
      if (groupIndex < myGroups.size() - 1) {
        UIPluginGroup nextGroup = myGroups.get(groupIndex + 1);
        anchor = nextGroup.plugins.get(0);
        uiIndex = getComponentIndex(nextGroup.panel);
      }
    }
    else {
      anchor = group.ui.plugins.get(index);
      uiIndex = getComponentIndex(anchor);
    }

    ListPluginComponent pluginComponent = createListComponent(descriptor, group);
    group.ui.plugins.add(index, pluginComponent);
    add(pluginComponent, uiIndex);
    myEventHandler.addCell(pluginComponent, anchor);
    pluginComponent.setListeners(myEventHandler);
  }

  public void removeGroup(@NotNull PluginsGroup group) {
    myGroups.remove(group.ui);
    remove(group.ui.panel);

    for (ListPluginComponent plugin : group.ui.plugins) {
      plugin.close();
      remove(plugin);
      myEventHandler.removeCell(plugin);
    }

    myEventHandler.updateSelection();
    group.clear();
  }

  public void removeFromGroup(@NotNull PluginsGroup group, @NotNull IdeaPluginDescriptor descriptor) {
    int index = ContainerUtil.indexOf(group.ui.plugins, component -> component.getPluginDescriptor() == descriptor);
    assert index != -1;
    ListPluginComponent component = group.ui.plugins.remove(index);
    component.close();
    remove(component);
    myEventHandler.removeCell(component);
    if (component.getSelection() == EventHandler.SelectionType.SELECTION) {
      myEventHandler.updateSelection();
    }
    group.descriptors.remove(descriptor);
  }

  private int getComponentIndex(@NotNull Component component) {
    int components = getComponentCount();
    for (int i = 0; i < components; i++) {
      if (getComponent(i) == component) {
        return i;
      }
    }
    return -1;
  }

  public void clear() {
    for (UIPluginGroup group : myGroups) {
      for (ListPluginComponent plugin : group.plugins) {
        plugin.close();
      }
    }

    myGroups.clear();
    myEventHandler.clear();
    removeAll();
  }

  public void initialSelection() {
    initialSelection(true);
  }

  public void initialSelection(boolean scrollAndFocus) {
    SwingUtilities.invokeLater(() -> {
      myEventHandler.initialSelection(scrollAndFocus);
      if (!myGroups.isEmpty()) {
        scrollRectToVisible(myGroups.get(0).panel.getBounds());
      }
    });
  }
}