// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.ui;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * A listener for {@code UISettings} changes.
 * <p/>
 * <em>NOTE: </em>The main {@code UISettings} instance pushes its events down
 * the UI components hierarchy so there's no need to add a {@code JComponent} as a listener.
 *
 * @see UISettings#fireUISettingsChanged()
 * @see com.intellij.util.ComponentTreeEventDispatcher
 */
@FunctionalInterface
public interface UISettingsListener extends EventListener {
  Topic<UISettingsListener> TOPIC = Topic.create("UI settings", UISettingsListener.class);

  void uiSettingsChanged(@NotNull UISettings uiSettings);
}
