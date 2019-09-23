// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.diagnostic.errordialog.PluginConflictDialog;
import com.intellij.ide.plugins.PluginConflictReporter;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

final class GuiPluginConflictReporter implements PluginConflictReporter {
  @Override
  public void reportConflictByClasses(@NotNull Collection<Class<?>> conflictingClasses) {
    final Set<PluginId> foundPlugins = new HashSet<>();
    boolean hasConflictWithPlatform = false;

    if (conflictingClasses.size() < 2) {
      Logger.getInstance(GuiPluginConflictReporter.class).warn("One should provide at least two conflicting classes to report");
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
      Logger.getInstance(GuiPluginConflictReporter.class).warn("The conflict has not come from PluginClassLoader");
      return;
    }

    boolean finalHasConflictWithPlatform = hasConflictWithPlatform;
    Runnable task = () -> {
      new PluginConflictDialog(new ArrayList<>(foundPlugins), finalHasConflictWithPlatform).show();
    };

    if (EventQueue.isDispatchThread()) {
      task.run();
    }
    else {
      try {
        EventQueue.invokeAndWait(task);
      }
      catch (InterruptedException | InvocationTargetException e) {
        PluginManagerCore.getLogger().error(e);
      }
    }
  }
}
