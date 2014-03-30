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

package com.intellij.execution.ui.layout;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface LayoutViewOptions {

  String STARTUP = "startup";

  @NotNull
  LayoutViewOptions setTopToolbar(@NotNull ActionGroup actions, @NotNull String place);

  @NotNull
  LayoutViewOptions setLeftToolbar(@NotNull ActionGroup leftToolbar, @NotNull String place);

  @NotNull
  LayoutViewOptions setMinimizeActionEnabled(boolean enabled);

  @NotNull
  LayoutViewOptions setMoveToGridActionEnabled(boolean enabled);

  @NotNull
  LayoutViewOptions setAttractionPolicy(@NotNull String contentId, LayoutAttractionPolicy policy);

  @NotNull
  LayoutViewOptions setConditionAttractionPolicy(@NotNull String condition, LayoutAttractionPolicy policy);

  boolean isToFocus(@NotNull Content content, @NotNull String condition);

  @NotNull
  LayoutViewOptions setToFocus(@Nullable Content content, @NotNull String condition);

  AnAction getLayoutActions();
  @NotNull
  AnAction[] getLayoutActionsList();

  @NotNull
  LayoutViewOptions setTabPopupActions(@NotNull ActionGroup group);
  @NotNull
  LayoutViewOptions setAdditionalFocusActions(@NotNull ActionGroup group);

  AnAction getSettingsActions();
  @NotNull
  AnAction[] getSettingsActionsList();
}