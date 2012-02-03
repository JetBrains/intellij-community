package org.jetbrains.jps.incremental.artifacts;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author nik
 */
public class JarPathUtil {
  public static final String JAR_SEPARATOR = "!/";

  @NotNull
  public static File getLocalFile(@NotNull String fullPath) {
    final int i = fullPath.indexOf(JAR_SEPARATOR);
    String filePath = i == -1 ? fullPath : fullPath.substring(0, i);
    return new File(FileUtil.toSystemDependentName(filePath));
  }
}
