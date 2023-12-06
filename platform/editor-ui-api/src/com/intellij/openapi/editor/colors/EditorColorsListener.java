/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.editor.colors;

import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.Nullable;

import java.util.EventListener;

/**
 * A listener for global color scheme change event.
 * <p/>
 * <em>NOTE: </em>The {@code EditorColorsManager} pushes the events down
 * the UI components hierarchy so there's no need to add a {@code JComponent} as a listener.
 * UI components also get this event triggered when global scheme itself is modified
 * so they can adjust their appearance accordingly.
 *
 * @see com.intellij.util.ComponentTreeEventDispatcher
 */
public interface EditorColorsListener extends EventListener {
  /**
   * Notifies the subscribers that the global editor colors scheme has changed.
   * <p>
   *   <em>A note to the callers: </em> if you're thinking of invoking it
   *   directly through {@link MessageBus#syncPublisher(Topic)} after you've changed the global scheme,
   *   consider calling {@code EditorColorsManagerImpl.fireGlobalSchemeChange} instead, which does a bit
   *   more than that and also catches and logs exceptions.
   * </p>
   * @param scheme the new scheme if it was switched or {@code null} if the scheme is the same, but was modified
   */
  void globalSchemeChange(@Nullable EditorColorsScheme scheme);
}
