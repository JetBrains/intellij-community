// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actionsOnSave;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.search.SearchableOptionContributor;
import com.intellij.ide.ui.search.SearchableOptionProcessor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class ActionsOnSaveSearchableOptionsContributor extends SearchableOptionContributor {
  @Override
  public void processOptions(@NotNull SearchableOptionProcessor processor) {
    addOptions(processor, IdeBundle.message("actions.on.save.page.title"));

    for (ActionOnSaveInfoProvider provider : ActionOnSaveInfoProvider.EP_NAME.getExtensionList()) {
      for (String text : provider.getSearchableOptions()) {
        addOptions(processor, text);
      }
    }
  }

  private static void addOptions(@NotNull SearchableOptionProcessor processor, @NotNull String text) {
    processor.addOptions(text,
                         null,
                         IdeBundle.message("actions.on.save.page.title"),
                         ActionsOnSaveConfigurable.CONFIGURABLE_ID,
                         IdeBundle.message("actions.on.save.page.title"),
                         false);
  }
}
