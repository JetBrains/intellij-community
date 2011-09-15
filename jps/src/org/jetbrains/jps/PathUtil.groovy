package org.jetbrains.jps

/**
 * @author nik
 */
class PathUtil {
  static String toSystemIndependentPath(String path) {
    return path.replace('\\', '/')
  }
}
