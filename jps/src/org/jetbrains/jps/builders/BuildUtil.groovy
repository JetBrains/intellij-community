package org.jetbrains.jps.builders

/**
 * @author nik
 */
class BuildUtil {
  static String suggestFileName(String text) {
    return text.replaceAll(/(;|:|\s)/, "_");
  }
}
