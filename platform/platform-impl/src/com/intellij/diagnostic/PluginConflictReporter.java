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
package com.intellij.diagnostic;

import com.intellij.diagnostic.errordialog.PluginConflictDialog;
import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public enum PluginConflictReporter {
  INSTANCE;

  public void reportConflictByClasses(@NotNull Collection<Class<?>> conflictingClasses) {
    final Set<PluginId> foundPlugins = new HashSet<>();
    boolean hasConflictWithPlatform = false;

    if (conflictingClasses.size() < 2) {
      Logger.getInstance(PluginConflictReporter.class).warn("One should provide at least two conflicting classes to report");
      return;
    }

    for (Class<?> aClass : conflictingClasses) {
      final ClassLoader classLoader = aClass.getClassLoader();
      if (classLoader instanceof PluginClassLoader) {
        foundPlugins.add(((PluginClassLoader)classLoader).getPluginId());
      }
      else {
        hasConflictWithPlatform = true;
      }
    }

    if (foundPlugins.isEmpty()) {
      Logger.getInstance(PluginConflictReporter.class).warn("The conflict has not come from PluginClassLoader");
      return;
    }

    new PluginConflictDialog(new ArrayList<>(foundPlugins), hasConflictWithPlatform).show();
  }
}
