package org.jetbrains.jps.model.serialization;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * @author nik
 */
public class PathMacroUtil {
  @NonNls public static final String PROJECT_DIR_MACRO_NAME = "PROJECT_DIR";
  @NonNls public static final String MODULE_DIR_MACRO_NAME = "MODULE_DIR";
  @NonNls public static final String DIRECTORY_STORE_NAME = ".idea";

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
}
