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

package com.intellij.openapi.components;

import com.intellij.util.xmlb.XmlSerializer;
import org.jetbrains.annotations.Nullable;

/**
 * Every component which would like to persist its state across IDEA restarts
 * should implement this interface.
 *
 * See <a href="http://confluence.jetbrains.net/display/IDEADEV/Persisting+State+of+Components">JetBrains WIKI</a>
 * for detailed description.
 */
public interface PersistentStateComponent<T> {
  /**
   * @return a component state. All properties and public fields are serialized. Only values, which differ
   * from default (i.e. the value of newly instantiated class) are serialized. <code>null</code> value indicates
   * that no state should be stored
   * @see XmlSerializer
   */
  @Nullable
  T getState();

  /**
   * This method is called when new component state is loaded. A component should expect this method
   * to be called at any moment of its lifecycle. The method can and will be called several times, if
   * config files were externally changed while IDEA running.
   * @param state loaded component state
   * @see com.intellij.util.xmlb.XmlSerializerUtil#copyBean(Object, Object) 
   */
  void loadState(T state);
}
