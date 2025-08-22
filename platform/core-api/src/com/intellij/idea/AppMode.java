// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@ApiStatus.Internal
public final class AppMode {
  public static final String FORCE_PLUGIN_UPDATES = "idea.force.plugin.updates";

  public static final String HELP_OPTION = "--help";
  public static final String VERSION_OPTION = "--version";

  private static final String AWT_HEADLESS = "java.awt.headless";

  private static boolean isHeadless;
  private static boolean isCommandLine;
  private static boolean isLightEdit;
  private static boolean disableNonBundledPlugins;
  private static boolean dontReopenProjects;
  private static boolean isRemoteDevHost;

  public static boolean isDisableNonBundledPlugins() {
    return disableNonBundledPlugins;
  }

  public static boolean isDontReopenProjects() {
    return dontReopenProjects;
  }

  /**
   * Disable some speculative service initializations to speed up Light Edit startup
   * <p>
   * This DOES NOT guarantee that the IDE will be run in Light Edit mode.
   *
   * @see com.intellij.ide.lightEdit.LightEditService#isLightEditProject(Project)
   */
  @ApiStatus.Obsolete
  public static boolean isLightEdit() {
    return isLightEdit;
  }

  public static boolean isCommandLine() {
    return isCommandLine;
  }

  public static boolean isHeadless() {
    return isHeadless;
  }

  /**
   * Returns {@code true} if the IDE is running as a remote development host.
   * This is an internal method supposed to be used only from code running during early startup phases.
   * If the instance container is initialized (in particular, in any plugin code), its equivalent
   * {@link com.intellij.platform.ide.productMode.IdeProductMode#isBackend()} should be used instead.
   */
  public static boolean isRemoteDevHost() {
    return isRemoteDevHost;
  }

  /**
   * Returns {@code true} if the IDE is running from a development build, not a regular installation.
   * The IDE can be started with the development build by running '* (dev build)' configuration from source code, also some tests use this
   * mode.
   * In this mode modules and plugins are loaded by different classloaders, the same as in production mode. However, the layout of
   * class-files and resources may differ from the real production layout.
   * @see com.intellij.ide.plugins.PluginManagerCore#isRunningFromSources
   */
  public static boolean isRunningFromDevBuild() {
    return Boolean.getBoolean("idea.use.dev.build.server");
  }

  /** @deprecated use {@link #isRunningFromDevBuild()} instead; this name may be confusing */
  @Deprecated
  public static boolean isDevServer() {
    return isRunningFromDevBuild();
  }

  public static void setFlags(@NotNull List<String> args) {
    WellKnownCommand knownCommand = WellKnownCommands.getCommandFor(args);

    isHeadless = Boolean.getBoolean(AWT_HEADLESS) ||
                 knownCommand != null && knownCommand.isHeadless();
    isCommandLine = isHeadless ||
                    knownCommand != null && knownCommand.isCommandLine();

    if (isHeadless) {
      System.setProperty(AWT_HEADLESS, Boolean.TRUE.toString());
    }

    isRemoteDevHost = knownCommand != null && knownCommand.isRemoteDevHost();

    isLightEdit = Boolean.parseBoolean(System.getProperty("idea.force.light.edit.mode")) ||
                  (knownCommand == null && !isHeadless && mayHappenToBeAFile(args));

    for (String arg : args) {
      if (ApplicationStartArguments.DISABLE_NON_BUNDLED_PLUGINS.isSet(args)) {
        disableNonBundledPlugins = true;
      }
      else if (ApplicationStartArguments.DONT_REOPEN_PROJECTS.isSet(args)) {
        dontReopenProjects = true;
      }
    }
  }

  private static boolean mayHappenToBeAFile(@NotNull List<String> args) {
    for (String arg : args) {
      // If not an option
      if (!arg.startsWith("-")) {
        try {
          Path path = Paths.get(arg);
          return Files.isRegularFile(path) || !Files.exists(path);
        }
        catch (Throwable t) {
          return false;
        }
      }
      else if (arg.equals("-l") || arg.equals("--line") || arg.equals("-c") || arg.equals("--column")) {
        return true;
      }
    }
    return false;
  }

  @TestOnly
  public static void setHeadlessInTestMode(boolean headless) {
    isHeadless = headless;
    isCommandLine = true;
    isLightEdit = false;
  }

  public static @Nullable String getDevIdeaProjectDir() {
    return System.getProperty("idea.dev.project.root");
  }
}
