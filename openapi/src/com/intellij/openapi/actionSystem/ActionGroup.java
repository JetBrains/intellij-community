/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.actionSystem;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * Represents a group of actions.
 */
public abstract class ActionGroup extends AnAction {
  private boolean myPopup;
  private PropertyChangeSupport myChangeSupport;

  /**
   * The actual value is a Boolean.
   */
  public static final String PROP_POPUP = "popup";

  /**
   * Creates a new <code>ActionGroup</code> with shortName set to <code>null</code> and
   * popup set to false.
   */
  public ActionGroup(){
    this(null, false);
  }

  /**
   * Creates a new <code>ActionGroup</code> with the specified shortName
   * and popup.
   *
   * @param shortName Text that represents a short name for this action group
   *
   * @param popup <code>true</code> if this group is a popup, <code>false</code>
   *  otherwise
   */
  public ActionGroup(String shortName, boolean popup){
    super(shortName);
    myChangeSupport = new PropertyChangeSupport(this);
    setPopup(popup);
  }

  /**
   * This method is final and empty because a group cannot have a handler.
   */
  public final void actionPerformed(AnActionEvent e){
  }

  /**
   * Returns the type of the group.
   *
   * @return <code>true</code> if the group is a popup, <code>false</code> otherwise
   */
  public final boolean isPopup(){
    return myPopup;
  }

  /**
   * Sets the type of the group.
   *
   * @param popup If <code>true</code> the group will be shown as a popup in menus
   */
  public final void setPopup(boolean popup){
    boolean oldPopup = myPopup;
    myPopup = popup;
    firePropertyChange(PROP_POPUP, oldPopup?Boolean.TRUE:Boolean.FALSE, myPopup?Boolean.TRUE:Boolean.FALSE);
  }

  public final void addPropertyChangeListener(PropertyChangeListener l){
    myChangeSupport.addPropertyChangeListener(l);
  }

  public final void removePropertyChangeListener(PropertyChangeListener l){
    myChangeSupport.removePropertyChangeListener(l);
  }

  protected final void firePropertyChange(String propertyName, Object oldValue, Object newValue){
    myChangeSupport.firePropertyChange(propertyName, oldValue, newValue);
  }

  /**
   * Returns the children of the group.
   *
   * @return An array represting children of this group. All returned children must be not <code>null</code>.
   */
  public abstract AnAction[] getChildren(AnActionEvent e);
}