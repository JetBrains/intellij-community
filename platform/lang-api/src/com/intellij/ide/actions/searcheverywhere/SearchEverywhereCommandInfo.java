// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.ide.SearchTopHitProvider;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.Nls;

public class SearchEverywhereCommandInfo {
  private final String command;
  private final @Nls String definition;
  private final SearchEverywhereContributor<?> contributor;

  public SearchEverywhereCommandInfo(String command, @Nls String definition, SearchEverywhereContributor<?> contributor) {
    this.command = command;
    this.definition = definition;
    this.contributor = contributor;
  }

  public String getCommand() {
    return command;
  }

  public @Nls String getDefinition() {
    return definition;
  }

  public SearchEverywhereContributor<?> getContributor() {
    return contributor;
  }

  public @NlsSafe String getCommandWithPrefix() {
    return SearchTopHitProvider.getTopHitAccelerator() + command;
  }
}
