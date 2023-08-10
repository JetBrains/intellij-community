// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.content;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.BusyObject;
import com.intellij.openapi.util.NlsActions.ActionText;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * Obtain via {@link ContentFactory#createContentManager(ContentUI, boolean, com.intellij.openapi.project.Project)}.
 *
 * @see ContentsUtil
 */
public interface ContentManager extends Disposable, BusyObject {
  boolean canCloseContents();

  @NotNull
  JComponent getComponent();

  /**
   * @see #getFactory()
   */
  void addContent(@NotNull Content content);

  void addContent(@NotNull Content content, int order);

  boolean removeContent(@NotNull Content content, boolean dispose);

  @NotNull ActionCallback removeContent(@NotNull Content content, boolean dispose, boolean requestFocus, boolean forcedFocus);

  void setSelectedContent(@NotNull Content content);

  @NotNull
  @ApiStatus.Internal
  ActionCallback setSelectedContentCB(@NotNull Content content);

  void setSelectedContent(@NotNull Content content, boolean requestFocus);

  @NotNull
  ActionCallback setSelectedContentCB(@NotNull Content content, boolean requestFocus);

  /**
   * @param forcedFocus unused
   */
  void setSelectedContent(@NotNull Content content, boolean requestFocus, boolean forcedFocus);

  /**
   * @param forcedFocus unused
   */
  @NotNull
  ActionCallback setSelectedContentCB(@NotNull Content content, boolean requestFocus, boolean forcedFocus);

  /**
   * @param requestFocus whether content will request focus after selection
   * @param forcedFocus  unused
   * @param implicit     if {@code true} and content cannot be focused (e.g. it's minimized at the moment) {@link ActionCallback#REJECTED} will be returned
   * @return resulting ActionCallback for both selection and focus transfer (if needed)
   */
  @NotNull
  ActionCallback setSelectedContent(@NotNull Content content, boolean requestFocus, boolean forcedFocus, boolean implicit);

  void addSelectedContent(@NotNull Content content);

  @Nullable
  Content getSelectedContent();

  Content @NotNull [] getSelectedContents();

  void removeAllContents(boolean dispose);

  int getContentCount();

  Content @NotNull [] getContents();

  Content findContent(String displayName);

  @Nullable
  Content getContent(int index);

  Content getContent(@NotNull JComponent component);

  int getIndexOfContent(@NotNull Content content);

  @ActionText
  @NotNull
  String getCloseActionName();

  boolean canCloseAllContents();

  ActionCallback selectPreviousContent();

  ActionCallback selectNextContent();

  void addContentManagerListener(@NotNull ContentManagerListener listener);

  void removeContentManagerListener(@NotNull ContentManagerListener listener);

  /**
   * Returns the localized name of the "Close All but This" action.
   */
  @ActionText
  @NotNull
  String getCloseAllButThisActionName();

  @ActionText
  @NotNull
  String getPreviousContentActionName();

  @ActionText
  @NotNull
  String getNextContentActionName();

  @NotNull
  List<AnAction> getAdditionalPopupActions(@NotNull Content content);

  void removeFromSelection(@NotNull Content content);

  boolean isSelected(@NotNull Content content);

  /**
   * @param forced unused
   */
  @NotNull
  ActionCallback requestFocus(@Nullable Content content, boolean forced);

  void addDataProvider(@NotNull DataProvider provider);

  @NotNull
  ContentFactory getFactory();

  boolean isDisposed();

  boolean isSingleSelection();

  default boolean isEmpty() {
    return getContentCount() == 0;
  }
}
