/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.extensions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ExtensionPointListener<T> {
  ExtensionPointListener[] EMPTY_ARRAY = new ExtensionPointListener[0];
  void extensionAdded(@NotNull T extension, @Nullable PluginDescriptor pluginDescriptor);

  void extensionRemoved(@NotNull T extension, @Nullable PluginDescriptor pluginDescriptor);

  class Adapter<T> implements ExtensionPointListener<T> {
    @Override
    public void extensionAdded(@NotNull T extension, @Nullable PluginDescriptor pluginDescriptor) { }

    @Override
    public void extensionRemoved(@NotNull T extension, @Nullable PluginDescriptor pluginDescriptor) { }
  }
}
