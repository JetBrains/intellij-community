// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * A listener for {@code UISettings} changes. An event says that some UI setting changed. It does not say which one.
 * <p>
 * Use this listener only when the reaction depends on several values. When the reaction depends on one value, and
 * {@link UISettings} has a value listener for it, use that value listener. An example is
 * {@link UISettings#addEditorAntialiasingListener}.
 * <p>
 * <em>NOTE:</em> The main {@code UISettings} instance pushes its events down the UI components hierarchy,
 * so there's no need to add a {@code JComponent} as a listener.
 *
 * @see UISettings#fireUISettingsChanged()
 * @see com.intellij.util.ComponentTreeEventDispatcher
 */
@FunctionalInterface
public interface UISettingsListener extends EventListener {

  void uiSettingsChanged(@NotNull UISettings uiSettings);

  @Topic.AppLevel
  Topic<UISettingsListener> TOPIC = new Topic<>(UISettingsListener.class, Topic.BroadcastDirection.TO_DIRECT_CHILDREN);
}
