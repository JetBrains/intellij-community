// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.*;
import java.util.Collections;
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
 */
public abstract class ActionGroup extends AnAction {
  public static final ActionGroup EMPTY_GROUP = new ActionGroup() {
    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
      return EMPTY_ARRAY;
    }
  };

  @ApiStatus.Internal
  public static final DataKey<ActionGroup> CONTEXT_ACTION_GROUP_KEY = DataKey.create("context.action.group");

  private boolean mySearchable = true;
  private Set<AnAction> mySecondaryActions;

  /**
   * Creates a new {@code ActionGroup} with shortName set to {@code null} and
   * popup set to {@code false}.
   */
  public ActionGroup() {
    // avoid template presentation creation
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
    // avoid template presentation creation
    if (popup) {
      getTemplatePresentation().setPopupGroup(popup);
    }
  }

  public ActionGroup(@Nullable @ActionText String text,
                     @Nullable @ActionDescription String description,
                     @Nullable Icon icon) {
    super(text, description, icon);
  }

  public ActionGroup(@NotNull Supplier<@ActionText String> text,
                     @NotNull Supplier<@ActionDescription String> description,
                     @Nullable Supplier<? extends @Nullable Icon> icon) {
    super(text, description, icon);
  }

  public ActionGroup(@NotNull Supplier<@ActionText String> dynamicText,
                     @NotNull Supplier<@ActionDescription String> dynamicDescription,
                     @Nullable Icon icon) {
    super(dynamicText, dynamicDescription, icon);
  }

  /**
   * This method can be called in popup menus if {@link Presentation#isPerformGroup()} is {@code true}.
   */
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
  }

  /**
   * A shortcut for {@code getTemplatePresentation().isPopupGroup()}
   */
  public final boolean isPopup() {
    return getTemplatePresentation().isPopupGroup();
  }

  /**
   * A shortcut for {@code getTemplatePresentation().setPopupGroup(popup)}
   *
   * A popup group is shown as a popup in menus.
   * <p>
   * In the {@link AnAction#update(AnActionEvent)} method {@code event.getPresentation().setPopupGroup(value)}
   * shall be used instead of this method to control the popup flag for the particular event and place.
   * <p>
   */
  public final void setPopup(boolean popup) {
    getTemplatePresentation().setPopupGroup(popup);
  }

  public final boolean isSearchable() {
    return mySearchable;
  }

  public final void setSearchable(boolean searchable) {
    mySearchable = searchable;
  }

  /**
   * Returns the child actions of the group.
   *
   * @see #getActionUpdateThread()
   */
  @ApiStatus.OverrideOnly
  public abstract AnAction @NotNull [] getChildren(@Nullable AnActionEvent e);

  @ApiStatus.Internal
  public final void setAsPrimary(@NotNull AnAction action, boolean isPrimary) {
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
   * Allows the group to intercept and transform its expanded visible children.
   */
  public @Unmodifiable @NotNull List<? extends @NotNull AnAction> postProcessVisibleChildren(
    @NotNull AnActionEvent e,
    @NotNull List<? extends @NotNull AnAction> visibleChildren) {
    return Collections.unmodifiableList(visibleChildren);
  }

  public final boolean isPrimary(@NotNull AnAction action) {
    return mySecondaryActions == null || !mySecondaryActions.contains(action);
  }

  @ApiStatus.Internal
  protected final void replace(@NotNull AnAction originalAction, @NotNull AnAction newAction) {
    if (mySecondaryActions != null) {
      if (mySecondaryActions.contains(originalAction)) {
        mySecondaryActions.remove(originalAction);
        mySecondaryActions.add(newAction);
      }
    }
  }
}
