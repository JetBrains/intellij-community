package org.jetbrains.jps

/**
 * @author nik
 */
public class OverlappingSourceRootsTest extends JpsBuildTestCase {
  public void test() throws Exception {
    doTest("testData/overlappingSourceRoots/overlappingSourceRoots.ipr", null, {
        dir("production") {
          dir("inner") {
            dir("y") {
              file("a.properties")
              file("Class2.class")
            }
          }
          dir("overlappingSourceRoots") {
            dir("x") {
              file("MyClass.class")
            }
          }
        }
    })
  }
}
