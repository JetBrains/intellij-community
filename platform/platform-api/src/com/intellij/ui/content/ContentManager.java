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
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.util.ActionCallback;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public interface ContentManager extends Disposable {


  boolean canCloseContents();

  JComponent getComponent();

  void addContent(@NotNull Content content);
  void addContent(@NotNull Content content, final int order);
  void addContent(@NotNull Content content, Object constraints);

  boolean removeContent(@NotNull Content content, final boolean dispose);
  ActionCallback removeContent(@NotNull Content content, final boolean dispose, boolean trackFocus, boolean forcedFocus);

  void setSelectedContent(@NotNull Content content);
  ActionCallback setSelectedContentCB(@NotNull Content content);
  void setSelectedContent(@NotNull Content content, boolean requestFocus);
  ActionCallback setSelectedContentCB(@NotNull Content content, boolean requestFocus);
  void setSelectedContent(@NotNull Content content, boolean requestFocus, boolean forcedFocus);
  ActionCallback setSelectedContentCB(@NotNull Content content, boolean requestFocus, boolean forcedFocus);

  ActionCallback setSelectedContent(@NotNull Content content, boolean requestFocus, boolean forcedFocus, boolean implicit);

  void addSelectedContent(@NotNull Content content);

  @Nullable
  Content getSelectedContent();
  @NotNull Content[] getSelectedContents();


  void removeAllContents(final boolean dispose);

  int getContentCount();

  @NotNull Content[] getContents();

  //TODO[anton,vova] is this method needed?
  Content findContent(String displayName);

  @Nullable
  Content getContent(int index);

  Content getContent(JComponent component);

  int getIndexOfContent(Content content);

  String getCloseActionName();

  boolean canCloseAllContents();

  ActionCallback selectPreviousContent();

  ActionCallback selectNextContent();

  void addContentManagerListener(@NotNull ContentManagerListener l);

  void removeContentManagerListener(@NotNull ContentManagerListener l);

  /**
   * Returns the localized name of the "Close All but This" action.
   *
   * @return the action name.
   * @since 5.1
   */
  String getCloseAllButThisActionName();

  List<AnAction> getAdditionalPopupActions(@NotNull  Content content);

  void removeFromSelection(@NotNull Content content);

  boolean isSelected(@NotNull Content content);

  ActionCallback requestFocus(@Nullable Content content, boolean forced);

  void addDataProvider(@NotNull DataProvider provider);
  
  @NotNull ContentFactory getFactory();

  boolean isDisposed();

  boolean isSingleSelection();
}
