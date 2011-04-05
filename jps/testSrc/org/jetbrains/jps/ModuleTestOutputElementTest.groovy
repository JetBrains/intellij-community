package org.jetbrains.jps

/**
 * @author nik
 */
class ModuleTestOutputElementTest extends JpsBuildTestCase {
  public void test() {
    doTest("testData/moduleTestOutput/moduleTestOutput.ipr", {}, {
      dir("artifacts") {
        dir("tests") {
          file("MyTest.class")
        }
      }
      dir("production") {
        dir("moduleTestOutput") {
          file("MyClass.class")
        }
      }
      dir("test") {
        dir("moduleTestOutput") {
          file("MyTest.class")
        }
      }
    })
  }
}
