/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.model.impl;

import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.JpsEventDispatcher;

import java.util.EventListener;
import java.util.HashMap;
import java.util.Map;

public abstract class JpsEventDispatcherBase implements JpsEventDispatcher {
  private final Map<Class<?>, EventDispatcher<?>> myDispatchers = new HashMap<>();

  @NotNull
  @Override
  public <T extends EventListener> T getPublisher(Class<T> listenerClass) {
    EventDispatcher<?> dispatcher = myDispatchers.get(listenerClass);
    if (dispatcher == null) {
      dispatcher = EventDispatcher.create(listenerClass);
      myDispatchers.put(listenerClass, dispatcher);
    }
    //noinspection unchecked
    return (T)dispatcher.getMulticaster();
  }

  @Override
  public <T extends JpsElement> void fireElementAdded(@NotNull T element, @NotNull JpsElementChildRole<T> role) {
    role.fireElementAdded(this, element);
  }

  @Override
  public <T extends JpsElement> void fireElementRemoved(@NotNull T element, @NotNull JpsElementChildRole<T> role) {
    role.fireElementRemoved(this, element);
  }
}
