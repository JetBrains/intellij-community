/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ui.tabs;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.Comparing;
import com.intellij.reference.SoftReference;
import com.intellij.ui.IconDeferrer;
import com.intellij.ui.PlaceProvider;
import com.intellij.ui.SimpleColoredText;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.content.AlertIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeSupport;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;

public final class TabInfo implements Queryable, PlaceProvider<String> {

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

  private JComponent myComponent;
  private JComponent myPreferredFocusableComponent;

  private ActionGroup myGroup;

  private final PropertyChangeSupport myChangeSupport = new PropertyChangeSupport(this);

  private Icon myIcon;
  private String myPlace;
  private Object myObject;
  private JComponent mySideComponent;
  private WeakReference<JComponent> myLastFocusOwner;


  private ActionGroup myTabLabelActions;
  private String myTabActionPlace;

  private AlertIcon myAlertIcon;

  private int myBlinkCount;
  private boolean myAlertRequested;
  private boolean myHidden;
  private JComponent myActionsContextComponent;

  private final SimpleColoredText myText = new SimpleColoredText();
  private String myTooltipText;

  private int myDefaultStyle = -1;
  private Color myDefaultForeground;
  private Color myDefaultWaveColor;

  private SimpleTextAttributes myDefaultAttributes;
  private static final AlertIcon DEFAULT_ALERT_ICON = new AlertIcon(AllIcons.Nodes.TabAlert);

  private boolean myEnabled = true;
  private Color myTabColor;

  private Queryable myQueryable;
  private DragOutDelegate myDragOutDelegate;

  /**
   * The tab which was selected before the mouse was pressed on this tab. Focus will be transferred to that tab if this tab is dragged
   * out of its container. (IDEA-61536)
   */
  private WeakReference<TabInfo> myPreviousSelection = new WeakReference<>(null);

  public TabInfo(final JComponent component) {
    myComponent = component;
    myPreferredFocusableComponent = component;
  }

  public PropertyChangeSupport getChangeSupport() {
    return myChangeSupport;
  }

  public TabInfo setText(String text) {
    List<SimpleTextAttributes> attributes = myText.getAttributes();
    TextAttributes textAttributes = attributes.size() == 1 ? attributes.get(0).toTextAttributes() : null;
    TextAttributes defaultAttributes = getDefaultAttributes().toTextAttributes();
    if (!myText.toString().equals(text) || !Comparing.equal(textAttributes, defaultAttributes)) {
      clearText(false);
      append(text, getDefaultAttributes());
    }
    return this;
  }

  @NotNull
  private SimpleTextAttributes getDefaultAttributes() {
    SimpleTextAttributes attributes = myDefaultAttributes;
    if (attributes == null) {
      myDefaultAttributes = attributes = new SimpleTextAttributes(myDefaultStyle != -1 ? myDefaultStyle : SimpleTextAttributes.STYLE_PLAIN,
                                                     myDefaultForeground, myDefaultWaveColor);

    }
    return attributes;
  }

  public TabInfo clearText(final boolean invalidate) {
    final String old = myText.toString();
    myText.clear();
    if (invalidate) {
      myChangeSupport.firePropertyChange(TEXT, old, myText.toString());
    }
    return this;
  }

  public TabInfo append(String fragment, SimpleTextAttributes attributes) {
    final String old = myText.toString();
    myText.append(fragment, attributes);
    myChangeSupport.firePropertyChange(TEXT, old, myText.toString());
    return this;
  }

  public TabInfo setIcon(Icon icon) {
    Icon old = myIcon;
    if (!IconDeferrer.getInstance().equalIcons(old, icon)) {
      myIcon = icon;
      myChangeSupport.firePropertyChange(ICON, old, icon);
    }
    return this;
  }

  public TabInfo setComponent(Component c) {
    if (myComponent == c) return this;
    JComponent old = myComponent;
    myComponent = (JComponent)c;
    myChangeSupport.firePropertyChange(COMPONENT, old, myComponent);
    return this;
  }

  public ActionGroup getGroup() {
    return myGroup;
  }

  public JComponent getComponent() {
    return myComponent;
  }

  public String getText() {
    return myText.toString();
  }

  public SimpleColoredText getColoredText() {
    return myText;
  }

  public Icon getIcon() {
    return myIcon;
  }

  @Override
  public String getPlace() {
    return myPlace;
  }

  public TabInfo setSideComponent(JComponent comp) {
    mySideComponent = comp;
    return this;
  }

  public JComponent getSideComponent() {
    return mySideComponent;
  }

  public TabInfo setActions(ActionGroup group, String place) {
    ActionGroup old = myGroup;
    myGroup = group;
    myPlace = place;
    myChangeSupport.firePropertyChange(ACTION_GROUP, old, myGroup);
    return this;
  }

  public TabInfo setActionsContextComponent(JComponent c) {
    myActionsContextComponent = c;
    return this;
  }

  public JComponent getActionsContextComponent() {
    return myActionsContextComponent;
  }

  public TabInfo setObject(final Object object) {
    myObject = object;
    return this;
  }

  public Object getObject() {
    return myObject;
  }

  public JComponent getPreferredFocusableComponent() {
    return myPreferredFocusableComponent != null ? myPreferredFocusableComponent : myComponent;
  }

  public TabInfo setPreferredFocusableComponent(final JComponent component) {
    myPreferredFocusableComponent = component;
    return this;
  }

  public void setLastFocusOwner(final JComponent owner) {
    myLastFocusOwner = new WeakReference<>(owner);
  }

  public ActionGroup getTabLabelActions() {
    return myTabLabelActions;
  }

  public String getTabActionPlace() {
    return myTabActionPlace;
  }

  public TabInfo setTabLabelActions(final ActionGroup tabActions, String place) {
    ActionGroup old = myTabLabelActions;
    myTabLabelActions = tabActions;
    myTabActionPlace = place;
    myChangeSupport.firePropertyChange(TAB_ACTION_GROUP, old, myTabLabelActions);
    return this;
  }

  @Nullable
  public JComponent getLastFocusOwner() {
    return SoftReference.dereference(myLastFocusOwner);
  }

  public TabInfo setAlertIcon(final AlertIcon alertIcon) {
    AlertIcon old = myAlertIcon;
    myAlertIcon = alertIcon;
    myChangeSupport.firePropertyChange(ALERT_ICON, old, myAlertIcon);
    return this;
  }

  public void fireAlert() {
    myAlertRequested = true;
    myChangeSupport.firePropertyChange(ALERT_STATUS, null, true);
  }

  public void stopAlerting() {
    myAlertRequested = false;
    myChangeSupport.firePropertyChange(ALERT_STATUS, null, false);
  }

  public int getBlinkCount() {
    return myBlinkCount;
  }

  public void setBlinkCount(final int blinkCount) {
    myBlinkCount = blinkCount;
  }

  public String toString() {
    return getText();
  }

  public AlertIcon getAlertIcon() {
    return myAlertIcon == null ? DEFAULT_ALERT_ICON : myAlertIcon;
  }

  public void resetAlertRequest() {
    myAlertRequested = false;
  }

  public boolean isAlertRequested() {
    return myAlertRequested;
  }

  public void setHidden(boolean hidden) {
    boolean old = myHidden;
    myHidden = hidden;
    myChangeSupport.firePropertyChange(HIDDEN, old, myHidden);
  }

  public boolean isHidden() {
    return myHidden;
  }

  public void setEnabled(boolean enabled) {
    boolean old = myEnabled;
    myEnabled = enabled;
    myChangeSupport.firePropertyChange(ENABLED, old, myEnabled);
  }

  public boolean isEnabled() {
    return myEnabled;
  }

  public TabInfo setDefaultStyle(@SimpleTextAttributes.StyleAttributeConstant int style) {
    myDefaultStyle = style;
    myDefaultAttributes = null;
    update();
    return this;
  }

  public TabInfo setDefaultForeground(final Color fg) {
    myDefaultForeground = fg;
    myDefaultAttributes = null;
    update();
    return this;
  }

  public Color getDefaultForeground() {
    return myDefaultForeground;
  }

  public TabInfo setDefaultWaveColor(final Color waveColor) {
    myDefaultWaveColor = waveColor;
    myDefaultAttributes = null;
    update();
    return this;
  }


  private void update() {
    setText(getText());
  }

  public void revalidate() {
    myDefaultAttributes = null;
    update();
  }

  public TabInfo setTooltipText(final String text) {
    String old = myTooltipText;
    if (!Comparing.equal(old, text)) {
      myTooltipText = text;
      myChangeSupport.firePropertyChange(TEXT, old, myTooltipText);
    }
    return this;
  }

  public String getTooltipText() {
    return myTooltipText;
  }

  public TabInfo setTabColor(Color color) {
    Color old = myTabColor;
    if (!Comparing.equal(color, old)) {
      myTabColor = color;
      myChangeSupport.firePropertyChange(TAB_COLOR, old, color);
    }
    return this;
  }

  public Color getTabColor() {
    return myTabColor;
  }

  public TabInfo setTestableUi(Queryable queryable) {
    myQueryable = queryable;
    return this;
  }

  @Override
  public void putInfo(@NotNull Map<String, String> info) {
    if (myQueryable != null) {
      myQueryable.putInfo(info);
    }
  }

  public TabInfo setDragOutDelegate(DragOutDelegate delegate) {
    myDragOutDelegate = delegate;
    return this;
  }

  public boolean canBeDraggedOut() {
    return myDragOutDelegate != null;
  }

  public DragOutDelegate getDragOutDelegate() {
    return myDragOutDelegate;
  }

  public void setPreviousSelection(@Nullable TabInfo previousSelection) {
    myPreviousSelection = new WeakReference<>(previousSelection);
  }

  @Nullable
  public TabInfo getPreviousSelection() {
    return myPreviousSelection.get();
  }

  public interface DragOutDelegate {

    void dragOutStarted(MouseEvent mouseEvent, TabInfo info);
    void processDragOut(MouseEvent event, TabInfo source);
    void dragOutFinished(MouseEvent event, TabInfo source);
    void dragOutCancelled(TabInfo source);
  }

}
