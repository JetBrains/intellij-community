package org.jetbrains.jps

/**
 * @author nik
 */
class BuildFromIdeaProjectTest extends JpsBuildTestCase {

  public void testLoadFromIpr() {
    doTest("testData/iprProject/iprProject.ipr", getGlobalLib(), expectedOutput("iprProject"))
  }

  public void testLoadDirBased() throws Exception {
    doTest("testData/dirBasedProject", getGlobalLib(), expectedOutput("dirBased"))
  }

  private Closure getGlobalLib() {
    return {Project project, ProjectBuilder projectBuilder ->
      project.createGlobalLibrary("jdom") {
        classpath "testData/iprProject/lib/jdom.jar"
      }
    }
  }

  private Closure expectedOutput(String moduleName) {
    return {
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
        dir(moduleName) {
          dir("xxx") {
            file("MyClass.class")
          }
        }
      }
    }
  }
}
