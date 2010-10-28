package org.jetbrains.jps.builders

/**
 * @author nik
 */
class BuildUtil {
  static String suggestFileName(String text) {
    String name = text.replaceAll(/(;|:|\s)/, "_")
    if (name.length() > 100) {
      name = name.substring(0, 100) + "_etc"
    }
    return name;
  }
}
