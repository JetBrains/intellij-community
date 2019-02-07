// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerConfigurableNew;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.ui.components.panels.OpaquePanel;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class PluginsGroupComponent extends JBPanelWithEmptyText {
  private final EventHandler myEventHandler;
  private final LinkListener<IdeaPluginDescriptor> myListener;
  private final LinkListener<String> mySearchListener;
  private final Function<? super IdeaPluginDescriptor, ? extends CellPluginComponent> myFunction;
  private final List<UIPluginGroup> myGroups = new ArrayList<>();

  public PluginsGroupComponent(@NotNull LayoutManager layout,
                               @NotNull EventHandler eventHandler,
                               @NotNull LinkListener<IdeaPluginDescriptor> listener,
                               @NotNull LinkListener<String> searchListener,
                               @NotNull Function<? super IdeaPluginDescriptor, ? extends CellPluginComponent> function) {
    super(layout);
    myEventHandler = eventHandler;
    myListener = listener;
    mySearchListener = searchListener;
    myFunction = function;

    myEventHandler.connect(this);

    setOpaque(true);
    setBackground(PluginManagerConfigurableNew.MAIN_BG_COLOR);
  }

  @NotNull
  public List<UIPluginGroup> getGroups() {
    return myGroups;
  }

  @NotNull
  public List<CellPluginComponent> getSelection() {
    return myEventHandler.getSelection();
  }

  public void setSelection(@NotNull CellPluginComponent component) {
    myEventHandler.setSelection(component);
  }

  public void setSelection(@NotNull List<CellPluginComponent> components) {
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
            int uiIndex = getComponentIndex(group.ui.plugins.get(fromIndex - 1));
            PluginLogo.startBatchMode();
            addToGroup(group, group.descriptors.subList(fromIndex, toIndex), uiIndex);
            PluginLogo.endBatchMode();

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

  private static final Color SECTION_HEADER_FOREGROUND =
    JBColor.namedColor("Plugins.SectionHeader.foreground", new JBColor(0x787878, 0x999999));
  private static final Color SECTION_HEADER_BACKGROUND =
    JBColor.namedColor("Plugins.SectionHeader.background", new JBColor(0xF7F7F7, 0x3C3F41));

  private void addGroup(@NotNull PluginsGroup group, @NotNull List<IdeaPluginDescriptor> descriptors, int groupIndex) {
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
                              (parent.getComponentCount() == 2 ? parent.getComponent(1).getWidth() + JBUI.scale(20) : 0), size.width);
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

    int index;

    if (groupIndex == 0) {
      add(panel, 0);
      index = 1;
    }
    else if (groupIndex == -1) {
      add(panel);
      index = -1;
    }
    else {
      Component anchorPanel = myGroups.get(groupIndex + 1).panel;
      int components = getComponentCount();
      index = -1;

      for (int i = 0; i < components; i++) {
        if (getComponent(i) == anchorPanel) {
          index = i;
          break;
        }
      }

      assert index != -1;
      add(panel, index++);
    }

    uiGroup.panel = panel;

    addToGroup(group, descriptors, index);
  }

  private void addToGroup(@NotNull PluginsGroup group, @NotNull List<IdeaPluginDescriptor> descriptors, int index) {
    for (IdeaPluginDescriptor descriptor : descriptors) {
      CellPluginComponent pluginComponent = myFunction.fun(descriptor);
      group.ui.plugins.add(pluginComponent);
      add(pluginComponent, index);
      myEventHandler.addCell(pluginComponent, index);
      pluginComponent.setListeners(myListener, mySearchListener, myEventHandler);
      if (index != -1) {
        index++;
      }
    }
  }

  public void addToGroup(@NotNull PluginsGroup group, @NotNull IdeaPluginDescriptor descriptor) {
    int index = group.addWithIndex(descriptor);
    CellPluginComponent anchor = null;
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

    CellPluginComponent pluginComponent = myFunction.fun(descriptor);
    group.ui.plugins.add(index, pluginComponent);
    add(pluginComponent, uiIndex);
    myEventHandler.addCell(pluginComponent, anchor);
    pluginComponent.setListeners(myListener, mySearchListener, myEventHandler);
  }

  public void removeGroup(@NotNull PluginsGroup group) {
    myGroups.remove(group.ui);
    remove(group.ui.panel);

    for (CellPluginComponent plugin : group.ui.plugins) {
      plugin.close();
      remove(plugin);
      myEventHandler.removeCell(plugin);
    }

    group.clear();
  }

  public void removeFromGroup(@NotNull PluginsGroup group, @NotNull IdeaPluginDescriptor descriptor) {
    int index = ContainerUtil.indexOf(group.ui.plugins, component -> component.myPlugin == descriptor);
    assert index != -1;
    CellPluginComponent component = group.ui.plugins.remove(index);
    component.close();
    remove(component);
    myEventHandler.removeCell(component);
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
      for (CellPluginComponent plugin : group.plugins) {
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
    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(() -> {
      myEventHandler.initialSelection(scrollAndFocus);
      if (!myGroups.isEmpty()) {
        scrollRectToVisible(myGroups.get(0).panel.getBounds());
      }
    });
  }
}