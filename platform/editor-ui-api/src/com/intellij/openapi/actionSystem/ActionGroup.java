// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem;

import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static com.intellij.openapi.util.NlsActions.ActionDescription;
import static com.intellij.openapi.util.NlsActions.ActionText;

/**
 * Represents a group of actions.
 *
 * @see com.intellij.openapi.actionSystem.DefaultActionGroup
 * @see com.intellij.openapi.actionSystem.ComputableActionGroup
 * @see com.intellij.openapi.actionSystem.CheckedActionGroup
 * @see com.intellij.openapi.actionSystem.CompactActionGroup
 */
public abstract class ActionGroup extends AnAction {
  private boolean mySearchable = true;
  private final PropertyChangeSupport myChangeSupport = new PropertyChangeSupport(this);
  public static final ActionGroup EMPTY_GROUP = new ActionGroup() {
    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
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
   * @param popup     {@code true} if this group is a popup, {@code false}
   *                  otherwise
   */
  public ActionGroup(@Nullable @ActionText String shortName, boolean popup) {
    this(() -> shortName, popup);
  }

  public ActionGroup(@NotNull Supplier<@ActionText String> shortName, boolean popup) {
    super(shortName);
    setPopup(popup);
  }

  public ActionGroup(@Nullable @ActionText String text,
                     @Nullable @ActionDescription String description,
                     @Nullable Icon icon) {
    super(text, description, icon);
  }

  public ActionGroup(@NotNull Supplier<@ActionText String> dynamicText,
                     @NotNull Supplier<@ActionDescription String> dynamicDescription,
                     @Nullable Icon icon) {
    super(dynamicText, dynamicDescription, icon);
  }

  /**
   * This method can be called in popup menus if {@link #canBePerformed(DataContext)} is {@code true}.
   */
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
  }

  /**
   * @return {@code true} if {@link #actionPerformed(AnActionEvent)} should be called.
   * @deprecated Use {@link Presentation#isPerformGroup()} instead.
   */
  @Deprecated
  public boolean canBePerformed(@NotNull DataContext context) {
    return false;
  }

  /**
   * Returns the default value of the popup flag for the group.
   * @see Presentation#setPopupGroup(boolean)
   */
  public boolean isPopup() {
    return getTemplatePresentation().isPopupGroup();
  }

  /** @deprecated Use {@link Presentation#setPopupGroup(boolean)} instead. */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
  public boolean isPopup(@NotNull String place) {
    return isPopup();
  }

  /**
   * Sets the default value of the popup flag for the group.
   * A popup group is shown as a popup in menus.
   *
   * In the {@link AnAction#update(AnActionEvent)} method {@code event.getPresentation().setPopupGroup(value)}
   * shall be used instead of this method to control the popup flag for the particular event and place.
   *
   * If the {@link #isPopup()} method is overridden, this method could be useless.
   *
   * @param popup If {@code true} the group will be shown as a popup in menus.
   * @see Presentation#setPopupGroup(boolean)
   */
  public final void setPopup(boolean popup) {
    Presentation presentation = getTemplatePresentation();
    boolean oldPopup = presentation.isPopupGroup();
    presentation.setPopupGroup(popup);
    firePropertyChange(PROP_POPUP, oldPopup, popup);
  }

  public boolean isSearchable() {
    return mySearchable;
  }

  public void setSearchable(boolean searchable) {
    mySearchable = searchable;
  }

  public final void addPropertyChangeListener(@NotNull PropertyChangeListener l) {
    myChangeSupport.addPropertyChangeListener(l);
  }

  public final void removePropertyChangeListener(@NotNull PropertyChangeListener l) {
    myChangeSupport.removePropertyChangeListener(l);
  }

  protected final void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
    myChangeSupport.firePropertyChange(propertyName, oldValue, newValue);
  }

  /**
   * Returns the children of the group.
   *
   * @return An array representing children of this group. All returned children must be not {@code null}.
   */
  public abstract AnAction @NotNull [] getChildren(@Nullable AnActionEvent e);

  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e, @NotNull ActionManager actionManager) {
    return getChildren(null);
  }

  final void setAsPrimary(@NotNull AnAction action, boolean isPrimary) {
    if (isPrimary) {
      if (mySecondaryActions != null) {
        mySecondaryActions.remove(action);
      }
    }
    else {
      if (mySecondaryActions == null) {
        mySecondaryActions = new HashSet<>();
      }

      mySecondaryActions.add(action);
    }
  }

  /**
   * Allows the group to intercept and transform its expanded content.
   */
  @NotNull
  @ApiStatus.Experimental
  public List<AnAction> postProcessVisibleChildren(@NotNull List<AnAction> visibleChildren,
                                                   @NotNull UpdateSession updateSession) {
    return visibleChildren;
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
    }
    else {
      Class<?> declaringClass = ReflectionUtil.getMethodDeclaringClass(getClass(), "update", AnActionEvent.class);
      myDumbAware = AnAction.class.equals(declaringClass) || ActionGroup.class.equals(declaringClass);
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
