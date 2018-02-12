/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.ide.ui;

import com.intellij.util.messages.Topic;

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

  void uiSettingsChanged(UISettings uiSettings);
}
