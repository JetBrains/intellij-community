// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PathUtilRt;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

import static com.intellij.openapi.util.io.FileUtilRt.toSystemIndependentName;

/**
 * @author nik
 */
public class PathMacroUtil {
  @NonNls public static final String PROJECT_DIR_MACRO_NAME = "PROJECT_DIR";

  @NonNls public static final String MODULE_DIR_MACRO_NAME = "MODULE_DIR";
  public static final String DEPRECATED_MODULE_DIR = "$" + MODULE_DIR_MACRO_NAME + "$";
  public static final String MODULE_WORKING_DIR_NAME = "MODULE_WORKING_DIR";
  public static final String MODULE_WORKING_DIR = "$" + MODULE_WORKING_DIR_NAME + "$";

  @NonNls public static final String DIRECTORY_STORE_NAME = ".idea";
  @NonNls public static final String APPLICATION_HOME_DIR = "APPLICATION_HOME_DIR";
  @NonNls public static final String APPLICATION_CONFIG_DIR = "APPLICATION_CONFIG_DIR";
  @NonNls public static final String APPLICATION_PLUGINS_DIR = "APPLICATION_PLUGINS_DIR";
  @NonNls public static final String USER_HOME_NAME = "USER_HOME";

  private static final Map<String, String> ourGlobalMacros = ContainerUtil.<String, String>immutableMapBuilder()
    .put(APPLICATION_HOME_DIR, toSystemIndependentName(PathManager.getHomePath()))
    .put(APPLICATION_CONFIG_DIR, toSystemIndependentName(PathManager.getConfigPath()))
    .put(APPLICATION_PLUGINS_DIR, toSystemIndependentName(PathManager.getPluginsPath()))
    .put(USER_HOME_NAME, StringUtil.trimEnd(toSystemIndependentName(SystemProperties.getUserHome()), "/")).build();

  @Nullable
  public static String getModuleDir(@NotNull String moduleFilePath) {
    String moduleDir = PathUtilRt.getParentPath(moduleFilePath);
    if (StringUtil.isEmpty(moduleDir)) {
      return null;
    }

    // hack so that, if a module is stored inside the .idea directory, the base directory
    // rather than the .idea directory itself is considered the module root
    // (so that a Ruby IDE project doesn't break if its directory is moved together with the .idea directory)
    String moduleDirParent = PathUtilRt.getParentPath(moduleDir);
    if (!StringUtil.isEmpty(moduleDirParent) && PathUtilRt.getFileName(moduleDir).equals(DIRECTORY_STORE_NAME)) {
      moduleDir = moduleDirParent;
    }
    moduleDir = toSystemIndependentName(moduleDir);
    if (moduleDir.endsWith(":/")) {
      moduleDir = moduleDir.substring(0, moduleDir.length() - 1);
    }
    return moduleDir;
  }

  @NotNull
  public static String getUserHomePath() {
    return ObjectUtils.assertNotNull(getGlobalSystemMacroValue(USER_HOME_NAME));
  }

  @NotNull
  public static Map<String, String> getGlobalSystemMacros() {
    return ourGlobalMacros;
  }

  @Nullable
  public static String getGlobalSystemMacroValue(String name) {
    return ourGlobalMacros.get(name);
  }
}
