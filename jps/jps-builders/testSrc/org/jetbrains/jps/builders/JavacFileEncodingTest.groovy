package org.jetbrains.jps.builders

import org.jetbrains.jps.builders.rebuild.JpsRebuildTestCase

/**
 * @author nik
 */
class JavacFileEncodingTest extends JpsRebuildTestCase {
  public void test() {
    doTest("javacFileEncoding/javacFileEncoding.ipr", {
      dir("production") {
        dir("javacFileEncoding") {
          file("MyClass.class")
        }
      }
    })
  }
}
