// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything.activity;

import com.intellij.ide.actions.runAnything.items.RunAnythingHelpItem;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

public interface RunAnythingHelpProviderBase<V> extends RunAnythingHelpProvider<V> {
  Logger LOG = Logger.getInstance(RunAnythingHelpProviderBase.class);

  @NotNull
  @Override
  default RunAnythingHelpItem getHelpItem(@NotNull DataContext dataContext) {
    return new RunAnythingHelpItem(getHelpCommandPlaceholder(), getCommand(), getHelpDescription(), getIcon());
  }

  @NotNull
  @Override
  default String getHelpCommandPlaceholder() {
    return getCommand();
  }

  @NotNull
  default String getCommand() {
    String commandPrefix = getCommandPrefix();
    LOG.assertTrue(commandPrefix != null, "Command prefix cannot be null for a help item");
    return commandPrefix;
  }
}