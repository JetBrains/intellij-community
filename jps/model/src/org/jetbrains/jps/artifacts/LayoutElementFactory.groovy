package org.jetbrains.jps.artifacts

/**
 * @author nik
 */
class LayoutElementFactory {

  static LayoutElement createParentDirectories(String path, LayoutElement element) {
    def result = element
    path.split("/").reverseEach {
      if (it != "") {
        result = new DirectoryElement(it, [result])
      }
    }
    return result
  }

}
