package org.jetbrains.jps

/**
 * @author nik
 */
public class ModuleCycleTest extends JpsBuildTestCase {
  public void test() throws Exception {
    doTest("testData/moduleCycle/moduleCycle.ipr", {Project project, ProjectBuilder builder ->
      builder.arrangeModuleCyclesOutputs = true
    }, {
        dir("production") {
          dir("module1") {
            file("Bar1.class")
          }
          dir("module2") {
            file("Bar2.class")
          }
          dir("moduleCycle") {
            file("Foo.class")
          }
        }
    })
  }
}
