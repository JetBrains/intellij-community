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
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.HashSet;

/**
 * Represents a group of actions.
 */
public abstract class ActionGroup extends AnAction {
  private boolean myPopup;
  private final PropertyChangeSupport myChangeSupport = new PropertyChangeSupport(this);
  public static final ActionGroup EMPTY_GROUP = new ActionGroup() {
    @NotNull
    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
      return EMPTY_ARRAY;
    }
  };

  private Set<AnAction> mySecondaryActions;

  /**
   * The actual value is a Boolean.
   */
  @NonNls public static final String PROP_POPUP = "popup";

  private Boolean myDumbAware;

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
    setPopup(popup);
  }

  public ActionGroup(String text, String description, Icon icon) {
    super(text, description, icon);
  }

  /**
   * This method can be called in popup menus if {@link #canBePerformed(DataContext)} is true
   */
  public void actionPerformed(AnActionEvent e){
  }

  /**
   * @return true if {@link #actionPerformed(AnActionEvent)} should be called
   */
  public boolean canBePerformed(DataContext context) {
    return false;
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
  @NotNull
  public abstract AnAction[] getChildren(@Nullable AnActionEvent e);

  final void setAsPrimary(AnAction action, boolean isPrimary) {
    if (isPrimary) {
      if (mySecondaryActions != null) {
        mySecondaryActions.remove(action);
      }
    } else {
      if (mySecondaryActions == null) {
        mySecondaryActions = new HashSet<AnAction>();
      }

      mySecondaryActions.add(action);
    }
  }

  public final boolean isPrimary(AnAction action) {
    return mySecondaryActions == null || !mySecondaryActions.contains(action);
  }

  protected final void replace(AnAction originalAction, AnAction newAction) {
    if (mySecondaryActions != null) {
      if (mySecondaryActions.contains(originalAction)) {
        mySecondaryActions.remove(originalAction);
        mySecondaryActions.add(newAction);
      }
    }
  }

  @Override
  public boolean isDumbAware() {
    if (myDumbAware != null) {
      return myDumbAware;
    }

    boolean dumbAware = super.isDumbAware();
    if (dumbAware) {
      myDumbAware = Boolean.valueOf(dumbAware);
    } else {
      if (myDumbAware == null) {
        try {
          Method updateMethod = getClass().getMethod("update", AnActionEvent.class);
          myDumbAware = updateMethod.getDeclaringClass().equals(AnAction.class);
        }
        catch (NoSuchMethodException e) {
          myDumbAware = Boolean.FALSE;
        }
      }
    }

    return myDumbAware;
  }
}
