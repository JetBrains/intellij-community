package com.intellij.ui.tabs;

import com.intellij.openapi.actionSystem.ActionGroup;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.beans.PropertyChangeSupport;
import java.lang.ref.WeakReference;

public final class TabInfo {

  static final String ACTION_GROUP = "actionGroup";
  static final String ICON = "icon";
  static final String TEXT = "text";
  static final String TAB_ACTION_GROUP = "tabActionGroup";

  private JComponent myComponent;
  private JComponent myPreferredFocusableComponent;

  private ActionGroup myGroup;

  private PropertyChangeSupport myChangeSupport = new PropertyChangeSupport(this);

  private String myText;
  private Icon myIcon;
  private String myPlace;
  private Object myObject;
  private JComponent mySideComponent;
  private WeakReference<JComponent> myLastFocusOwner;


  private ActionGroup myTabActions;

  public TabInfo(final JComponent component) {
    myComponent = component;
    myPreferredFocusableComponent = component;
  }

  PropertyChangeSupport getChangeSupport() {
    return myChangeSupport;
  }

  public TabInfo setText(String text) {
    String old = myText;
    myText = text;
    myChangeSupport.firePropertyChange(TEXT, old, text);
    return this;
  }

  public TabInfo setIcon(Icon icon) {
    Icon old = myIcon;
    myIcon = icon;
    myChangeSupport.firePropertyChange(ICON, old, icon);
    return this;
  }

  ActionGroup getGroup() {
    return myGroup;
  }

  public JComponent getComponent() {
    return myComponent;
  }

  public String getText() {
    return myText;
  }

  Icon getIcon() {
    return myIcon;
  }

  String getPlace() {
    return myPlace;
  }

  public TabInfo setSideComponent(JComponent comp) {
    mySideComponent = comp;
    return this;
  }

  JComponent getSideComponent() {
    return mySideComponent;
  }

  public TabInfo setActions(ActionGroup group, String place) {
    ActionGroup old = myGroup;
    myGroup = group;
    myPlace = place;
    myChangeSupport.firePropertyChange(ACTION_GROUP, old, myGroup);
    return this;
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

  void setLastFocusOwner(final JComponent owner) {
    myLastFocusOwner = new WeakReference<JComponent>(owner);
  }

  public ActionGroup getTabActions() {
    return myTabActions;
  }

  public void setTabActions(final ActionGroup tabActions) {
    ActionGroup old = myTabActions;
    myTabActions = tabActions;
    myChangeSupport.firePropertyChange(TAB_ACTION_GROUP, old, myTabActions);
  }

  @Nullable
  JComponent getLastFocusOwner() {
    return myLastFocusOwner != null ? myLastFocusOwner.get() : null;
  }

}
