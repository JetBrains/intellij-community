// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.content;

import com.intellij.ide.dnd.DnDTarget;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.ui.ComponentContainer;
import com.intellij.openapi.util.BusyObject;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;

/**
 * Represents a tab or pane displayed in a toolwindow or in another content manager.
 *
 * @see ContentFactory#createContent(JComponent, String, boolean)
 */
public interface Content extends UserDataHolder, ComponentContainer {
  String PROP_DISPLAY_NAME = "displayName";
  String PROP_ICON = "icon";
  String PROP_ACTIONS = "actions";
  String PROP_DESCRIPTION = "description";
  String PROP_COMPONENT = "component";
  String IS_CLOSABLE = "isClosable";
  String PROP_ALERT = "alerting";

  Key<Boolean> TABBED_CONTENT_KEY = Key.create("tabbedContent");
  Key<String> TAB_GROUP_NAME_KEY = Key.create("tabbedGroupName");
  Key<ComponentOrientation> TAB_LABEL_ORIENTATION_KEY = Key.create("tabLabelComponentOrientation");
  Key<DnDTarget> TAB_DND_TARGET_KEY = Key.create("tabDndTarget");

  void setComponent(JComponent component);

  void setPreferredFocusableComponent(JComponent component);

  void setPreferredFocusedComponent(Computable<? extends JComponent> computable);

  void setIcon(Icon icon);
  Icon getIcon();

  void setDisplayName(String displayName);
  String getDisplayName();

  void setTabName(String tabName);
  String getTabName();

  void setToolwindowTitle(String toolwindowTitle);
  String getToolwindowTitle();

  Disposable getDisposer();
  void setDisposer(@NotNull Disposable disposer);

  void setShouldDisposeContent(boolean value);
  boolean shouldDisposeContent();

  String getDescription();
  void setDescription(String description);

  void addPropertyChangeListener(PropertyChangeListener l);

  void removePropertyChangeListener(PropertyChangeListener l);

  @Nullable
  ContentManager getManager();

  boolean isSelected();

  void release();

  boolean isValid();

  void setPinned(boolean locked);
  boolean isPinned();

  void setPinnable(boolean pinnable);
  boolean isPinnable();

  boolean isCloseable();
  void setCloseable(boolean closeable);

  void setActions(ActionGroup actions, String place, @Nullable JComponent contextComponent);
  ActionGroup getActions();

  void setSearchComponent(@Nullable JComponent comp);
  @Nullable JComponent getSearchComponent();

  String getPlace();
  JComponent getActionsContextComponent();

  void setAlertIcon(@Nullable AlertIcon icon);
  @Nullable AlertIcon getAlertIcon();

  void fireAlert();

  @Nullable BusyObject getBusyObject();
  void setBusyObject(BusyObject object);

  String getSeparator();
  void setSeparator(String separator);

  void setPopupIcon(Icon icon);
  Icon getPopupIcon();

  /**
   * @param executionId supposed to identify group of contents (for example "Before Launch" tasks and the main Run Configuration)
   */
  void setExecutionId(long executionId);
  long getExecutionId();

  default void setHelpId(String helpId) { }
  default @Nullable String getHelpId() { return null; }
}