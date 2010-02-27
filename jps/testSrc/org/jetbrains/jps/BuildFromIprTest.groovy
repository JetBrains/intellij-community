package org.jetbrains.jps

/**
 * @author nik
 */
class BuildFromIprTest extends JpsBuildTestCase {

  public void testBuild() {
    def globalLib = {Project project ->
      project.createGlobalLibrary("jdom") {
        classpath "testData/iprProject/lib/jdom.jar"
      }
    }
    doTest("testData/iprProject/iprProject.ipr", globalLib) {
      dir("artifacts") {
        dir("archive") {
          archive("archive.jar") {
            dir("files") {
              dir("dir") {
                file("f.txt", "f")
              }
              file("f.txt", "f")
              file("g.txt", "f")
            }
            archive("sources.zip") {
              dir("xxx") {
                file("MyClass.java")
              }
            }
            dir("META-INF") { file("MANIFEST.MF") }
          }
        }
        dir("files") {
          dir("dir") {
            file("f.txt", "f")
          }
          file("f.txt", "f")
          file("g.txt", "f")
        }
        dir("explodedWar") {
          dir("WEB-INF") {
            dir("classes") {
              dir("xxx") {
                file("MyClass.class")
              }
            }
            dir("lib") {
              file("jdom.jar")
              file("junit.jar")
            }
            file("web.xml")
          }
          file("index.jsp")
        }
      }
      dir("production") {
        dir("iprProject") {
          dir("xxx") {
            file("MyClass.class")
          }
        }
      }
    }
  }

}
