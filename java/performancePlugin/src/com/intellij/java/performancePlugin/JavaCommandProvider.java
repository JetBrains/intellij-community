package com.intellij.java.performancePlugin;

import com.jetbrains.performancePlugin.CommandProvider;
import com.jetbrains.performancePlugin.CreateCommand;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

final class JavaCommandProvider implements CommandProvider {
  @Override
  public @NotNull
  Map<String, CreateCommand> getCommands() {
    return Map.of(
      FindUsagesJavaCommand.PREFIX, FindUsagesJavaCommand::new,
      BuildCommand.PREFIX, BuildCommand::new,
      SyncJpsLibrariesCommand.PREFIX, SyncJpsLibrariesCommand::new
    );
  }
}
