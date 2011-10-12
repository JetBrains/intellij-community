package org.jetbrains.jps.builders

import org.jetbrains.jps.ProjectBuilder

/**
 * @author nik
 */
class BuildUtil {
  static def deleteDir(ProjectBuilder projectBuilder, String path) {
    if (path == null) return
    int attempts = 10;
    while (attempts-- > 0) {
      try {
        projectBuilder.binding.ant.delete(dir: path)
        return
      } catch (Exception e) {
        if (attempts == 0) {
          throw e;
        }
        projectBuilder.info("Failed to delete: $e, trying again")
        Thread.sleep(100)
      }
    }
  }

  static String suggestFileName(String text) {
    String name = text.replaceAll(/(;|:|\s)/, "_")
    if (name.length() > 100) {
      name = name.substring(0, 100) + "_etc"
    }
    return name;
  }
}
