// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.Strings;
import com.intellij.util.PathUtilRt;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class PathMacroUtil {
  @ApiStatus.Internal public static final @NonNls String PROJECT_DIR_MACRO_NAME = "PROJECT_DIR";
  @ApiStatus.Internal public static final @NonNls String PROJECT_NAME_MACRO_NAME = "PROJECT_NAME";

  @ApiStatus.Internal public static final @NonNls String MODULE_DIR_MACRO_NAME = "MODULE_DIR";
  @ApiStatus.Internal
  public static final String DEPRECATED_MODULE_DIR = "$" + MODULE_DIR_MACRO_NAME + "$";
  @ApiStatus.Internal
  public static final String MODULE_WORKING_DIR_NAME = "MODULE_WORKING_DIR";
  @ApiStatus.Internal
  public static final String MODULE_WORKING_DIR = "$" + MODULE_WORKING_DIR_NAME + "$";

  @ApiStatus.Internal public static final @NonNls String DIRECTORY_STORE_NAME = ".idea";
  @ApiStatus.Internal public static final @NonNls String APPLICATION_HOME_DIR = "APPLICATION_HOME_DIR";
  @ApiStatus.Internal public static final @NonNls String APPLICATION_CONFIG_DIR = "APPLICATION_CONFIG_DIR";
  @ApiStatus.Internal public static final @NonNls String APPLICATION_PLUGINS_DIR = "APPLICATION_PLUGINS_DIR";
  @ApiStatus.Internal public static final @NonNls String USER_HOME_NAME = "USER_HOME";

  private static volatile Map<String, String> ourGlobalMacrosForIde;
  private static volatile Map<String, String> globalMacrosForStandalone;

  private PathMacroUtil() {
  }

  public static @Nullable String getModuleDir(@NotNull String moduleFilePath) {
    String moduleDir = PathUtilRt.getParentPath(moduleFilePath);
    if (Strings.isEmpty(moduleDir)) {
      return null;
    }

    // hack so that, if a module is stored inside the .idea directory, the base directory
    // rather than the .idea directory itself is considered the module root
    // (so that a Ruby IDE project doesn't break if its directory is moved together with the .idea directory)
    String moduleDirParent = PathUtilRt.getParentPath(moduleDir);
    if (!Strings.isEmpty(moduleDirParent) && PathUtilRt.getFileName(moduleDir).equals(DIRECTORY_STORE_NAME)) {
      moduleDir = moduleDirParent;
    }
    moduleDir = FileUtilRt.toSystemIndependentName(moduleDir);
    if (moduleDir.endsWith(":/")) {
      moduleDir = moduleDir.substring(0, moduleDir.length() - 1);
    }
    return moduleDir;
  }

  public static @NotNull String getUserHomePath() {
    return Objects.requireNonNull(getGlobalSystemMacroValue(USER_HOME_NAME));
  }

  public static @NotNull Map<String, String> getGlobalSystemMacros() {
    return getGlobalSystemMacros(true);
  }

  public static @NotNull Map<String, String> getGlobalSystemMacros(boolean insideIde) {
    if (insideIde) {
      if (ourGlobalMacrosForIde == null) {
        ourGlobalMacrosForIde = computeGlobalPathMacrosInsideIde();
      }
      return ourGlobalMacrosForIde;
    }
    else {
      if (globalMacrosForStandalone == null) {
        globalMacrosForStandalone = computeGlobalPathMacrosForStandaloneCode();
      }
      return globalMacrosForStandalone;
    }
  }

  private static @NotNull Map<String, String> computeGlobalPathMacrosForStandaloneCode() {
    Map<String, String> result = new HashMap<>();
    String homePath = PathManager.getHomePath(false);
    if (homePath != null) {
      result.put(APPLICATION_HOME_DIR, FileUtilRt.toSystemIndependentName(homePath));
      result.put(APPLICATION_CONFIG_DIR, FileUtilRt.toSystemIndependentName(PathManager.getConfigPath()));
      result.put(APPLICATION_PLUGINS_DIR, FileUtilRt.toSystemIndependentName(PathManager.getPluginsPath()));
    }
    result.put(USER_HOME_NAME, computeUserHomePath());
    return Map.copyOf(result);
  }

  private static Map<String, String> computeGlobalPathMacrosInsideIde() {
    return Map.of(
      APPLICATION_HOME_DIR, FileUtilRt.toSystemIndependentName(PathManager.getHomePath()), 
      APPLICATION_CONFIG_DIR, FileUtilRt.toSystemIndependentName(PathManager.getConfigPath()), 
      APPLICATION_PLUGINS_DIR, FileUtilRt.toSystemIndependentName(PathManager.getPluginsPath()), 
      USER_HOME_NAME, computeUserHomePath()
    );
  }

  private static @NotNull String computeUserHomePath() {
    return Strings.trimEnd(FileUtilRt.toSystemIndependentName(SystemProperties.getUserHome()), "/");
  }

  public static @Nullable String getGlobalSystemMacroValue(String name) {
    return getGlobalSystemMacroValue(name, true);
  }

  public static @Nullable String getGlobalSystemMacroValue(String name, boolean insideIde) {
    return getGlobalSystemMacros(insideIde).get(name);
  }
}
