/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Map;

/**
 * @author nik
 */
public class PathMacroUtil {
  @NonNls public static final String PROJECT_DIR_MACRO_NAME = "PROJECT_DIR";
  @NonNls public static final String MODULE_DIR_MACRO_NAME = "MODULE_DIR";
  @NonNls public static final String DIRECTORY_STORE_NAME = ".idea";
  @NonNls public static final String APPLICATION_HOME_DIR = "APPLICATION_HOME_DIR";
  @NonNls public static final String APPLICATION_PLUGINS_DIR = "APPLICATION_PLUGINS_DIR";
  @NonNls public static final String USER_HOME_NAME = "USER_HOME";

  @Nullable
  public static String getModuleDir(String moduleFilePath) {
    File moduleDirFile = new File(moduleFilePath).getParentFile();
    if (moduleDirFile == null) return null;

    // hack so that, if a module is stored inside the .idea directory, the base directory
    // rather than the .idea directory itself is considered the module root
    // (so that a Ruby IDE project doesn't break if its directory is moved together with the .idea directory)
    File moduleDirParent = moduleDirFile.getParentFile();
    if (moduleDirParent != null && moduleDirFile.getName().equals(DIRECTORY_STORE_NAME)) {
      moduleDirFile = moduleDirParent;
    }
    String moduleDir = moduleDirFile.getPath();
    moduleDir = moduleDir.replace(File.separatorChar, '/');
    if (moduleDir.endsWith(":/")) {
      moduleDir = moduleDir.substring(0, moduleDir.length() - 1);
    }
    return moduleDir;
  }

  public static String getUserHomePath() {
    return StringUtil.trimEnd(FileUtil.toSystemIndependentName(SystemProperties.getUserHome()), "/");
  }

  public static Map<String, String> getGlobalSystemMacros() {
    final Map<String, String> map = new HashMap<String, String>();
    map.put(APPLICATION_HOME_DIR, getApplicationHomeDirPath());
    map.put(APPLICATION_PLUGINS_DIR, getApplicationPluginsDirPath());
    map.put(USER_HOME_NAME, getUserHomePath());
    return map;
  }

  private static String getApplicationHomeDirPath() {
    return FileUtil.toSystemIndependentName(PathManager.getHomePath());
  }

  private static String getApplicationPluginsDirPath() {
    return FileUtil.toSystemIndependentName(PathManager.getPluginsPath());
  }

  @Nullable
  public static String getGlobalSystemMacroValue(String name) {
    if (APPLICATION_HOME_DIR.equals(name)) return getApplicationHomeDirPath();
    if (APPLICATION_PLUGINS_DIR.equals(name)) return getApplicationPluginsDirPath();
    if (USER_HOME_NAME.equals(name)) return getUserHomePath();
    return null;
  }
}
