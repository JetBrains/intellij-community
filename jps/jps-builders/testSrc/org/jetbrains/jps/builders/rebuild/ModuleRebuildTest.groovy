package org.jetbrains.jps.builders.rebuild
/**
 * @author nik
 */
public class ModuleRebuildTest extends JpsRebuildTestCase {
  public void testModuleCycle() {
    doTest("moduleCycle/moduleCycle.ipr", null, {
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

  public void testOverlappingSourceRoots() {
    doTest("overlappingSourceRoots/overlappingSourceRoots.ipr", null, {
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

  public void testResourceCopying() {
    doTest("resourceCopying/resourceCopying.ipr", null, {
      dir("production") {
        dir("resourceCopying") {
          dir("copy") {
            file("file.txt")
          }
          dir("copyTree") {
            dir("subdir") {
              file("abc.txt")
            }
            file("xyz.txt")
          }
          file("a.txt")
          file("index.html")
        }
      }
    })
  }
}
