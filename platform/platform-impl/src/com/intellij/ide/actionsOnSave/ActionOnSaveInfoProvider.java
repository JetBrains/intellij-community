// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actionsOnSave;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Please use `order` attribute when registering {@link ActionOnSaveInfoProvider}s and be as specific as possible. Some plugins may be
 * disabled, so it's recommended to use 'before' and 'after' anchors relative to all other known extensions. The order of the checkboxes
 * on the 'Actions on Save' page should reflect the real order of the performed actions.
 */
public abstract class ActionOnSaveInfoProvider {

  private static final ExtensionPointName<ActionOnSaveInfoProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.actionOnSaveInfoProvider");

  static List<ActionOnSaveInfo> getAllActionOnSaveInfos(@NotNull Project project, @NotNull Settings settings) {
    ArrayList<ActionOnSaveInfo> infos = new ArrayList<>();
    for (ActionOnSaveInfoProvider provider : EP_NAME.getExtensionList()) {
      infos.addAll(provider.getActionOnSaveInfos(project, settings));
    }
    return infos;
  }

  protected abstract @NotNull Collection<? extends ActionOnSaveInfo> getActionOnSaveInfos(@NotNull Project project,
                                                                                          @NotNull Settings settings);
}
