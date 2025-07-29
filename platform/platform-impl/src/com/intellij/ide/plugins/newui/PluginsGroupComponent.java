// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui;

import com.intellij.accessibility.AccessibilityUtils;
import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.OpaquePanel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.table.ComponentsListFocusTraversalPolicy;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.*;
import java.awt.*;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;

@ApiStatus.Internal
public abstract class PluginsGroupComponent extends JBPanelWithEmptyText {

  private final @NotNull EventHandler myEventHandler;
  private final List<UIPluginGroup> myGroups = new ArrayList<>();

  public PluginsGroupComponent(@NotNull EventHandler eventHandler) {
    super(new PluginListLayout());
    myEventHandler = eventHandler;

    myEventHandler.connect(this);

    setOpaque(true);
    setBackground(PluginManagerConfigurable.MAIN_BG_COLOR);

    setFocusTraversalPolicyProvider(true);
    // Focus traversal policy that makes focus order similar to lists and trees, where Tab doesn't move focus between list items,
    // but instead moves focus to the next component. It also keeps group header buttons and buttons inside list items focusable.
    setFocusTraversalPolicy(new ComponentsListFocusTraversalPolicy(true) {
      @Override
      protected @NotNull List<Component> getOrderedComponents() {
        List<Component> orderedComponents = new ArrayList<>();
        List<ListPluginComponent> selectedComponents = getSelection();
        Set<PluginsGroup> addedGroups = new HashSet<>();

        for (ListPluginComponent component : selectedComponents) {
          PluginsGroup group = component.getGroup();
          if (!addedGroups.contains(group)) {
            addedGroups.add(group);
            if (UIUtil.isFocusable(group.rightAction)) {
              orderedComponents.add(group.rightAction);
            }
            else if (!ContainerUtil.isEmpty(group.rightActions)) {
              orderedComponents.addAll(ContainerUtil.filter(group.rightActions, UIUtil::isFocusable));
            }
          }

          orderedComponents.add(component);
          orderedComponents.addAll(component.getFocusableComponents());
        }

        return orderedComponents;
      }
    });
  }

  protected abstract @NotNull ListPluginComponent createListComponent(@NotNull PluginUiModel model,
                                                                      @NotNull PluginsGroup group,
                                                                      @NotNull List<HtmlChunk> errors,
                                                                      @Nullable PluginUiModel installedDescriptorDorMarketplace);

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
    addGroup(group, group.getModels(), groupIndex);
  }

  public void addLazyGroup(@NotNull PluginsGroup group, @NotNull JScrollBar scrollBar, int gapSize, @NotNull Runnable uiCallback) {
    if (group.getModels().size() <= gapSize) {
      addGroup(group);
    }
    else {
      addGroup(group, group.getModels().subList(0, gapSize), -1);
      AdjustmentListener listener = new AdjustmentListener() {
        @Override
        public void adjustmentValueChanged(AdjustmentEvent e) {
          if ((scrollBar.getValue() + scrollBar.getVisibleAmount()) >= scrollBar.getMaximum()) {
            int fromIndex = group.ui.plugins.size();
            int toIndex = Math.min(fromIndex + gapSize, group.getDescriptors().size());
            ListPluginComponent lastComponent = group.ui.plugins.get(fromIndex - 1);
            int uiIndex = getComponentIndex(lastComponent);
            int eventIndex = myEventHandler.getCellIndex(lastComponent);
            try {
              PluginLogo.startBatchMode();
              addToGroup(group, group.getModels().subList(fromIndex, toIndex), uiIndex, eventIndex);
            }
            finally {
              PluginLogo.endBatchMode();
            }

            if (group.getDescriptors().size() == group.ui.plugins.size()) {
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

  private void addGroup(@NotNull PluginsGroup group, @NotNull List<PluginUiModel> models, int groupIndex) {
    UIPluginGroup uiGroup = new UIPluginGroup();
    group.ui = uiGroup;
    myGroups.add(groupIndex == -1 ? myGroups.size() : groupIndex, uiGroup);

    OpaquePanel panel = new OpaquePanel(new BorderLayout(), SECTION_HEADER_BACKGROUND) {
      @Override
      public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
          accessibleContext = new AccessibleOpaquePanelComponent();
        }
        return accessibleContext;
      }

      protected class AccessibleOpaquePanelComponent extends AccessibleJComponent {
        @Override
        public String getAccessibleName() {
          return group.title;
        }

        @Override
        public AccessibleRole getAccessibleRole() {
          return AccessibilityUtils.GROUPED_ELEMENTS;
        }
      }
    };
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

    addToGroup(group, models, index, eventIndex);
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
                          @NotNull List<PluginUiModel> models,
                          int index,
                          int eventIndex) {
    for (PluginUiModel pluginUiModel : models) {
      PluginUiModel installedDescriptor = group.getInstalledDescriptor(pluginUiModel.getPluginId());
      ListPluginComponent pluginComponent = createListComponent(pluginUiModel, group, group.getErrors(pluginUiModel), installedDescriptor);
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

  public void addToGroup(@NotNull PluginsGroup group, @NotNull PluginUiModel model) {
    int index = group.addWithIndex(model);
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

    ListPluginComponent pluginComponent =
      createListComponent(model, group, group.getErrors(model), group.getInstalledDescriptor(model.getPluginId()));
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

  public void removeFromGroup(@NotNull PluginsGroup group, @NotNull PluginUiModel descriptor) {
    int index = ContainerUtil.indexOf(group.ui.plugins, component -> component.getPluginModel() == descriptor);
    assert index != -1;
    ListPluginComponent component = group.ui.plugins.remove(index);
    component.close();
    remove(component);
    myEventHandler.removeCell(component);
    if (component.getSelection() == EventHandler.SelectionType.SELECTION) {
      myEventHandler.updateSelection();
    }
    group.removeDescriptor(descriptor);
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

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new AccessiblePluginsGroupComponent();
    }
    return accessibleContext;
  }

  protected class AccessiblePluginsGroupComponent extends AccessibleJComponent {
    @Override
    public AccessibleRole getAccessibleRole() {
      return AccessibilityUtils.GROUPED_ELEMENTS;
    }
  }
}