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
package com.intellij.ui.content;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.ui.ComponentContainer;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.UserDataHolder;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.beans.PropertyChangeListener;

public interface Content extends UserDataHolder, ComponentContainer {
  @NonNls
  String PROP_DISPLAY_NAME = "displayName";
  @NonNls
  String PROP_ICON = "icon";
  String PROP_ACTIONS = "actions";
  @NonNls String PROP_DESCRIPTION = "description";
  @NonNls 
  String PROP_COMPONENT = "component";

  String PROP_ALERT = "alerting";

  void setComponent(JComponent component);

  void setPreferredFocusableComponent(JComponent component);

  void setPreferredFocusedComponent(Computable<JComponent> computable);

  void setIcon(Icon icon);

  Icon getIcon();

  void setDisplayName(String displayName);

  String getDisplayName();

  void setTabName(String tabName);

  String getTabName();

  void setToolwindowTitle(String toolwindowTitle);

  String getToolwindowTitle();

  Disposable getDisposer();

  /**
   * @param disposer a Disposable object whoes dispose() method will be invoked upon this content release.
   */
  void setDisposer(Disposable disposer);

  String getDescription();

  void setDescription(String description);

  void addPropertyChangeListener(PropertyChangeListener l);

  void removePropertyChangeListener(PropertyChangeListener l);

  ContentManager getManager();

  boolean isSelected();

  void release();

  boolean isValid();
  boolean isPinned();

  void setPinned(boolean locked);
  boolean isPinnable();

  boolean isCloseable();
  void setCloseable(boolean closeable);

  void setActions(ActionGroup actions, String place, @Nullable JComponent contextComponent);
  void setSearchComponent(@Nullable JComponent comp);

  ActionGroup getActions();
  @Nullable JComponent getSearchComponent();
  String getPlace();
  JComponent getActionsContextComponent();

  void setAlertIcon(@Nullable AlertIcon icon);
  @Nullable AlertIcon getAlertIcon();

  void fireAlert();
  
}
