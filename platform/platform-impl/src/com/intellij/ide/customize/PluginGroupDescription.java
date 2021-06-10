// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.customize;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class PluginGroupDescription {

  private final @NotNull PluginId myPluginId;
  private final @NotNull @NlsSafe String myName;
  private final @NotNull @Nls String myCategory;
  private final @NotNull @Nls String myDescription;

  private PluginGroupDescription(@NotNull @NonNls String idString,
                                 @NotNull @NlsSafe String name,
                                 @NotNull @Nls String category,
                                 @NotNull @Nls String description) {
    myPluginId = PluginId.getId(idString);
    myName = name;
    myCategory = category;
    myDescription = description;
  }

  public @NotNull PluginId getPluginId() {
    return myPluginId;
  }

  public @NotNull @NlsSafe String getName() {
    return myName;
  }

  public @NotNull @Nls String getCategory() {
    return myCategory;
  }

  public @NotNull @Nls String getDescription() {
    return myDescription;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    PluginGroupDescription that = (PluginGroupDescription)o;
    return getPluginId().equals(that.getPluginId());
  }

  @Override
  public int hashCode() {
    return getPluginId().hashCode();
  }

  /**
   * TODO regenerate after {@link PluginGroups#parseString(String)} removal.
   */
  @Override
  public String toString() {
    return StringUtil.join(new String[]{getCategory(), getDescription(), getPluginId().getIdString()}, ":");
  }

  public static @NotNull PluginGroupDescription vim() {
    return new PluginGroupDescription("IdeaVIM",
                                      "IdeaVim",
                                      IdeBundle.message("plugin.group.editor"),
                                      IdeBundle.message("plugin.description.vim.editor"));
  }

  public static @NotNull PluginGroupDescription aws() {
    return new PluginGroupDescription("aws.toolkit",
                                      "AWS Toolkit",
                                      IdeBundle.message("plugin.group.cloud.support"),
                                      IdeBundle.message("plugin.descriptor.aws.toolkit"));
  }

  public static @NotNull PluginGroupDescription teamCity() {
    return new PluginGroupDescription("JetBrains TeamCity Plugin",
                                      "TeamCity Integration",
                                      IdeBundle.message("plugin.group.tools.integration"),
                                      IdeBundle.message("plugin.descriptor.teamcity"));
  }

  public static @NotNull PluginGroupDescription create(@NotNull @NonNls String idString,
                                                       @NotNull @NlsSafe String name,
                                                       @NotNull @Nls String category,
                                                       @NotNull @Nls String description) {
    return new PluginGroupDescription(idString, name, category, description);
  }
}