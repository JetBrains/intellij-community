// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem;

import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents a group of actions.
 *
 * @see com.intellij.openapi.actionSystem.DefaultActionGroup
 * @see com.intellij.openapi.actionSystem.ComputableActionGroup
 * @see com.intellij.openapi.actionSystem.CheckedActionGroup
 * @see com.intellij.openapi.actionSystem.CompactActionGroup
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
  @NonNls private static final String PROP_POPUP = "popup";

  private Boolean myDumbAware;

  /**
   * Creates a new {@code ActionGroup} with shortName set to {@code null} and
   * popup set to {@code false}.
   */
  public ActionGroup() {
    // avoid eagerly creating template presentation
  }

  /**
   * Creates a new {@code ActionGroup} with the specified shortName
   * and popup.
   *
   * @param shortName Text that represents a short name for this action group
   *
   * @param popup {@code true} if this group is a popup, {@code false}
   *  otherwise
   */
  public ActionGroup(@Nls(capitalization = Nls.Capitalization.Title) String shortName, boolean popup){
    super(shortName);
    setPopup(popup);
  }

  public ActionGroup(@Nls(capitalization = Nls.Capitalization.Title) String text,
                     @Nls(capitalization = Nls.Capitalization.Sentence) String description,
                     Icon icon) {
    super(text, description, icon);
  }

  /**
   * This method can be called in popup menus if {@link #canBePerformed(DataContext)} is {@code true}.
   */
  @Override
  public void actionPerformed(@NotNull AnActionEvent e){
  }

  /**
   * @return {@code true} if {@link #actionPerformed(AnActionEvent)} should be called.
   */
  public boolean canBePerformed(@NotNull DataContext context) {
    return false;
  }

  /**
   * Returns the type of the group.
   *
   * @return {@code true} if the group is a popup, {@code false} otherwise
   */
  public boolean isPopup(){
    return myPopup;
  }

  public boolean isPopup(@NotNull String place) {
    return isPopup();
  }

  /**
   * Sets the type of the group.
   *
   * @param popup If {@code true} the group will be shown as a popup in menus.
   */
  public final void setPopup(boolean popup){
    boolean oldPopup = myPopup;
    myPopup = popup;
    firePropertyChange(PROP_POPUP, oldPopup, myPopup);
  }

  public final void addPropertyChangeListener(@NotNull PropertyChangeListener l){
    myChangeSupport.addPropertyChangeListener(l);
  }

  public final void removePropertyChangeListener(@NotNull PropertyChangeListener l){
    myChangeSupport.removePropertyChangeListener(l);
  }

  protected final void firePropertyChange(String propertyName, Object oldValue, Object newValue){
    myChangeSupport.firePropertyChange(propertyName, oldValue, newValue);
  }

  /**
   * Returns the children of the group.
   *
   * @return An array representing children of this group. All returned children must be not {@code null}.
   */
  @NotNull
  public abstract AnAction[] getChildren(@Nullable AnActionEvent e);

  @NotNull
  public AnAction[] getChildren(@Nullable AnActionEvent e, @NotNull ActionManager actionManager) {
    return getChildren(null);
  }

  final void setAsPrimary(@NotNull AnAction action, boolean isPrimary) {
    if (isPrimary) {
      if (mySecondaryActions != null) {
        mySecondaryActions.remove(action);
      }
    } else {
      if (mySecondaryActions == null) {
        mySecondaryActions = new HashSet<>();
      }

      mySecondaryActions.add(action);
    }
  }

  public final boolean isPrimary(@NotNull AnAction action) {
    return mySecondaryActions == null || !mySecondaryActions.contains(action);
  }

  protected final void replace(@NotNull AnAction originalAction, @NotNull AnAction newAction) {
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
      myDumbAware = Boolean.TRUE;
    } else {
      if (myDumbAware == null) {
        Class<?> declaringClass = ReflectionUtil.getMethodDeclaringClass(getClass(), "update", AnActionEvent.class);
        myDumbAware = AnAction.class.equals(declaringClass) || ActionGroup.class.equals(declaringClass);
      }
    }

    return myDumbAware;
  }

  public boolean hideIfNoVisibleChildren() {
    return false;
  }

  public boolean disableIfNoVisibleChildren() {
    return true;
  }
}
