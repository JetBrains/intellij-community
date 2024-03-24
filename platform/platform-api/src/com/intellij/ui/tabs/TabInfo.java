// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.reference.SoftReference;
import com.intellij.ui.ClientProperty;
import com.intellij.ui.PlaceProvider;
import com.intellij.ui.SimpleColoredText;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.content.AlertIcon;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeSupport;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TabInfo implements Queryable, PlaceProvider {
  public static final String ACTION_GROUP = "actionGroup";
  public static final String ICON = "icon";
  public static final String TAB_COLOR = "color";
  public static final String COMPONENT = "component";
  public static final String TEXT = "text";
  public static final String TAB_ACTION_GROUP = "tabActionGroup";
  public static final String ALERT_ICON = "alertIcon";

  public static final String ALERT_STATUS = "alertStatus";
  public static final String HIDDEN = "hidden";
  public static final String ENABLED = "enabled";

  private JComponent component;
  private JComponent preferredFocusableComponent;

  private ActionGroup group;

  private final PropertyChangeSupport changeSupport = new PropertyChangeSupport(this);

  private Icon icon;
  private @NonNls String place;
  private Object object;
  private JComponent sideComponent;
  private JComponent foreSideComponent;
  private Reference<JComponent> lastFocusOwner;

  private ActionGroup tabLabelActions;
  private @Nullable ActionGroup tabPaneActions;
  private String tabActionPlace;

  private AlertIcon alertIcon;

  private int blinkCount;
  private boolean isAlertRequested;
  private boolean isHidden;
  private JComponent actionContextComponent;

  private final SimpleColoredText text = new SimpleColoredText();
  private @NlsContexts.Tooltip String tooltipText;

  private int defaultStyle = -1;
  private Color defaultForeground;
  private TextAttributes editorAttributes;
  private SimpleTextAttributes defaultAttributes;
  private static final AlertIcon DEFAULT_ALERT_ICON = new AlertIcon(AllIcons.Nodes.TabAlert, 0, -JBUI.scale(6));

  private boolean isEnabled = true;
  private Color tabColor;

  private Queryable queryable;
  private DragOutDelegate dragOutDelegate;
  private DragDelegate dragDelegate;

  /**
   * The tab which was selected before the mouse was pressed on this tab. Focus will be transferred to that tab if this tab is dragged
   * out of its container. (IDEA-61536)
   */
  private WeakReference<TabInfo> previousSelection = new WeakReference<>(null);

  public TabInfo(@Nullable JComponent component) {
    this.component = component;
    preferredFocusableComponent = component;
  }

  public @NotNull PropertyChangeSupport getChangeSupport() {
    return changeSupport;
  }

  public @NotNull TabInfo setText(@NlsContexts.TabTitle @NotNull String text) {
    List<SimpleTextAttributes> attributes = this.text.getAttributes();
    TextAttributes textAttributes = attributes.size() == 1 ? attributes.get(0).toTextAttributes() : null;
    SimpleTextAttributes defaultAttributes = getDefaultAttributes();
    if (!this.text.toString().equals(text) || !Comparing.equal(textAttributes, defaultAttributes.toTextAttributes())) {
      clearText(false);
      //noinspection DialogTitleCapitalization
      append(text, defaultAttributes);
    }
    return this;
  }

  private @NotNull SimpleTextAttributes getDefaultAttributes() {
    if (defaultAttributes == null) {
      int style = (defaultStyle != -1 ? defaultStyle : SimpleTextAttributes.STYLE_PLAIN)
                  | SimpleTextAttributes.STYLE_USE_EFFECT_COLOR;
      if (editorAttributes != null) {
        SimpleTextAttributes attr = SimpleTextAttributes.fromTextAttributes(editorAttributes);
        attr = SimpleTextAttributes.merge(new SimpleTextAttributes(style, defaultForeground), attr);
        defaultAttributes = attr;
      }
      else {
        defaultAttributes = new SimpleTextAttributes(style, defaultForeground);
      }
    }
    return defaultAttributes;
  }

  public @NotNull TabInfo clearText(final boolean invalidate) {
    final String old = text.toString();
    text.clear();
    if (invalidate) {
      changeSupport.firePropertyChange(TEXT, old, text.toString());
    }
    return this;
  }

  public @NotNull TabInfo append(@NotNull @NlsContexts.Label String fragment, @NotNull SimpleTextAttributes attributes) {
    final String old = text.toString();
    text.append(fragment, attributes);
    changeSupport.firePropertyChange(TEXT, old, text.toString());
    return this;
  }

  public @NotNull TabInfo setIcon(Icon icon) {
    Icon old = this.icon;
    if (!Objects.equals(old, icon)) {
      this.icon = icon;
      changeSupport.firePropertyChange(ICON, old, icon);
    }
    return this;
  }

  public @NotNull TabInfo setComponent(Component c) {
    if (component != c) {
      JComponent old = component;
      component = (JComponent)c;
      changeSupport.firePropertyChange(COMPONENT, old, component);
    }
    return this;
  }

  public ActionGroup getGroup() {
    return group;
  }

  public JComponent getComponent() {
    return component;
  }

  public boolean isPinned() {
    return ClientProperty.isTrue(getComponent(), JBTabsImpl.PINNED);
  }

  public @NotNull @NlsContexts.TabTitle String getText() {
    return text.toString();
  }

  public @NotNull SimpleColoredText getColoredText() {
    return text;
  }

  public Icon getIcon() {
    return icon;
  }

  @Override
  public String getPlace() {
    return place;
  }

  public @NotNull TabInfo setSideComponent(JComponent comp) {
    sideComponent = comp;
    return this;
  }

  public JComponent getSideComponent() {
    return sideComponent;
  }

  public @NotNull TabInfo setForeSideComponent(JComponent comp) {
    foreSideComponent = comp;
    return this;
  }

  public JComponent getForeSideComponent() {
    return foreSideComponent;
  }

  public @NotNull TabInfo setActions(ActionGroup group, @NonNls String place) {
    ActionGroup old = this.group;
    this.group = group;
    this.place = place;
    changeSupport.firePropertyChange(ACTION_GROUP, old, this.group);
    return this;
  }

  public @NotNull TabInfo setActionsContextComponent(JComponent c) {
    actionContextComponent = c;
    return this;
  }

  public JComponent getActionsContextComponent() {
    return actionContextComponent;
  }

  public @NotNull TabInfo setObject(final Object object) {
    this.object = object;
    return this;
  }

  public Object getObject() {
    return object;
  }

  public JComponent getPreferredFocusableComponent() {
    return preferredFocusableComponent != null ? preferredFocusableComponent : component;
  }

  public @NotNull TabInfo setPreferredFocusableComponent(final JComponent component) {
    preferredFocusableComponent = component;
    return this;
  }

  public void setLastFocusOwner(final JComponent owner) {
    lastFocusOwner = owner == null ? null : new WeakReference<>(owner);
  }

  public ActionGroup getTabLabelActions() {
    return tabLabelActions;
  }

  public String getTabActionPlace() {
    return tabActionPlace;
  }

  public @NotNull TabInfo setTabLabelActions(final ActionGroup tabActions, @NotNull String place) {
    ActionGroup old = tabLabelActions;
    tabLabelActions = tabActions;
    tabActionPlace = place;
    changeSupport.firePropertyChange(TAB_ACTION_GROUP, old, tabLabelActions);
    return this;
  }

  public @Nullable ActionGroup getTabPaneActions() {
    return tabPaneActions;
  }

  /**
   * Sets the actions that will be displayed on the right side of the tabs
   */
  public @NotNull TabInfo setTabPaneActions(final @Nullable ActionGroup tabPaneActions) {
    this.tabPaneActions = tabPaneActions;
    return this;
  }

  public @Nullable JComponent getLastFocusOwner() {
    return SoftReference.dereference(lastFocusOwner);
  }

  public @NotNull TabInfo setAlertIcon(AlertIcon alertIcon) {
    AlertIcon old = this.alertIcon;
    this.alertIcon = alertIcon;
    changeSupport.firePropertyChange(ALERT_ICON, old, this.alertIcon);
    return this;
  }

  public void fireAlert() {
    isAlertRequested = true;
    changeSupport.firePropertyChange(ALERT_STATUS, null, true);
  }

  public void stopAlerting() {
    isAlertRequested = false;
    changeSupport.firePropertyChange(ALERT_STATUS, null, false);
  }

  public int getBlinkCount() {
    return blinkCount;
  }

  public void setBlinkCount(final int blinkCount) {
    this.blinkCount = blinkCount;
  }

  @Override
  public String toString() {
    return getText();
  }

  public @NotNull AlertIcon getAlertIcon() {
    return alertIcon == null ? DEFAULT_ALERT_ICON : alertIcon;
  }

  public void resetAlertRequest() {
    isAlertRequested = false;
  }

  public boolean isAlertRequested() {
    return isAlertRequested;
  }

  public void setHidden(boolean hidden) {
    boolean old = isHidden;
    isHidden = hidden;
    changeSupport.firePropertyChange(HIDDEN, old, isHidden);
  }

  public boolean isHidden() {
    return isHidden;
  }

  public void setEnabled(boolean enabled) {
    boolean old = isEnabled;
    isEnabled = enabled;
    changeSupport.firePropertyChange(ENABLED, old, isEnabled);
  }

  public boolean isEnabled() {
    return isEnabled;
  }

  public @NotNull TabInfo setDefaultStyle(@SimpleTextAttributes.StyleAttributeConstant int style) {
    defaultStyle = style;
    defaultAttributes = null;
    update();
    return this;
  }

  public @NotNull TabInfo setDefaultForeground(final Color fg) {
    defaultForeground = fg;
    defaultAttributes = null;
    update();
    return this;
  }

  public Color getDefaultForeground() {
    return defaultForeground;
  }

  public @NotNull TabInfo setDefaultAttributes(@Nullable TextAttributes attributes) {
    editorAttributes = attributes;
    defaultAttributes = null;
    update();
    return this;
  }


  private void update() {
    setText(getText());
  }

  public void revalidate() {
    defaultAttributes = null;
    update();
  }

  public @NotNull TabInfo setTooltipText(@NlsContexts.Tooltip String text) {
    String old = tooltipText;
    if (!Objects.equals(old, text)) {
      tooltipText = text;
      changeSupport.firePropertyChange(TEXT, old, tooltipText);
    }
    return this;
  }

  public @NlsContexts.Tooltip String getTooltipText() {
    return tooltipText;
  }

  public @NotNull TabInfo setTabColor(Color color) {
    Color old = tabColor;
    if (!Comparing.equal(color, old)) {
      tabColor = color;
      changeSupport.firePropertyChange(TAB_COLOR, old, color);
    }
    return this;
  }

  public Color getTabColor() {
    return tabColor;
  }

  public @NotNull TabInfo setTestableUi(Queryable queryable) {
    this.queryable = queryable;
    return this;
  }

  @Override
  public void putInfo(@NotNull Map<? super String, ? super String> info) {
    if (queryable != null) {
      queryable.putInfo(info);
    }
  }

  public @NotNull TabInfo setDragOutDelegate(DragOutDelegate delegate) {
    dragOutDelegate = delegate;
    return this;
  }

  public boolean canBeDraggedOut() {
    return dragOutDelegate != null;
  }

  public DragOutDelegate getDragOutDelegate() {
    return dragOutDelegate;
  }

  public void setPreviousSelection(@Nullable TabInfo previousSelection) {
    this.previousSelection = new WeakReference<>(previousSelection);
  }

  public DragDelegate getDragDelegate() {
    return dragDelegate;
  }

  public void setDragDelegate(DragDelegate dragDelegate) {
    this.dragDelegate = dragDelegate;
  }

  public @Nullable TabInfo getPreviousSelection() {
    return previousSelection.get();
  }

  public interface DragDelegate {
    void dragStarted(@NotNull MouseEvent mouseEvent);
    void dragFinishedOrCanceled();
  }

  public interface DragOutDelegate {
    void dragOutStarted(@NotNull MouseEvent mouseEvent, @NotNull TabInfo info);
    void processDragOut(@NotNull MouseEvent event, @NotNull TabInfo source);
    void dragOutFinished(@NotNull MouseEvent event, TabInfo source);
    void dragOutCancelled(TabInfo source);
  }
}
