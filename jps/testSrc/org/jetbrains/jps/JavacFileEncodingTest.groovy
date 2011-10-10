package org.jetbrains.jps

/**
 * @author nik
 */
class JavacFileEncodingTest extends JpsBuildTestCase {
  public void test() {
    doTest("testData/javacFileEncoding/javacFileEncoding.ipr", null, {
      dir("production") {
        dir("javacFileEncoding") {
          file("MyClass.class")
        }
      }
    })
  }
}
