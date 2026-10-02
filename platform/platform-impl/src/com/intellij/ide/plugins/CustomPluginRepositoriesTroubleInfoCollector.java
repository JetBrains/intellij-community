// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.RepositoryHelper.CustomPluginRepositorySource;
import com.intellij.openapi.project.Project;
import com.intellij.troubleshooting.TroubleInfoCollector;
import org.jetbrains.annotations.NotNull;

final class CustomPluginRepositoriesTroubleInfoCollector implements TroubleInfoCollector {
  @Override
  public @NotNull String collectInfo(@NotNull Project project) {
    var output = new StringBuilder(2000);
    output.append("=====").append(IdeBundle.message("trouble.info.custom.plugin.repositories")).append("=====\n");

    var hasRepositories = false;
    for (var source : CustomPluginRepositorySource.values()) {
      var repositoryUrls = source.getRepositoryUrls();
      if (repositoryUrls.isEmpty()) {
        continue;
      }

      hasRepositories = true;
      output.append(IdeBundle.message("trouble.info.custom.plugin.repositories.source"))
        .append(": ").append(getSourceName(source)).append("\n");
      for (var repositoryUrl : repositoryUrls) {
        output.append(repositoryUrl).append('\n');
      }
      output.append('\n');
    }

    if (!hasRepositories) {
      output.append(IdeBundle.message("trouble.info.custom.plugin.repositories.empty")).append('\n');
    }

    return output.toString();
  }

  @Override
  public String toString() {
    return IdeBundle.message("trouble.info.custom.plugin.repositories");
  }

  private static @NotNull String getSourceName(@NotNull CustomPluginRepositorySource source) {
    return switch (source) {
      case STORED_SETTINGS -> IdeBundle.message("trouble.info.custom.plugin.repositories.source.stored.settings");
      case IDEA_PLUGIN_HOSTS_PROPERTY -> IdeBundle.message("trouble.info.custom.plugin.repositories.source.idea.plugin.hosts.property");
      case UPDATE_SETTINGS_PROVIDERS -> IdeBundle.message("trouble.info.custom.plugin.repositories.source.update.settings.providers");
      case CUSTOM_BUILT_IN_REPOSITORY_PROPERTY ->
        IdeBundle.message("trouble.info.custom.plugin.repositories.source.custom.built.in.repository.property");
    };
  }
}