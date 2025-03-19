/*
 * Copyright 2004-2005 Alexey Efimov
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
package org.intellij.images.options;

import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeListener;

/**
 * Options for plugin.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
public interface Options extends Cloneable {
  @NotNull
  EditorOptions getEditorOptions();

  @NotNull
  ExternalEditorOptions getExternalEditorOptions();

  /**
   * Option injection from other options.
   *
   * @param options Other options
   */
  void inject(@NotNull Options options);

  void addPropertyChangeListener(@NotNull PropertyChangeListener listener, @NotNull Disposable parent);

  /**
   * Set option by string representation.
   *
   * @param name  Name of option
   * @param value Value
   * @return {@code true} if option is matched and setted.
   */
  boolean setOption(@NotNull String name, Object value);
}
