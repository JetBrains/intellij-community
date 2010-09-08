package org.jetbrains.jps

/**
 * @author nik
 */
class JpaTest extends JpsBuildTestCase {
  public void testOverwriteArtifacts() throws Exception {
    doTest("testData/jpaTest/jpaTest.ipr", {}, {
      dir("artifacts") {
        dir("jpaTest") {
          dir("WEB-INF") {
            dir("classes") {
              dir("META-INF") {
                file("persistence.xml")
              }
            }
          }
        }
      }
      dir("production") {
        dir("jpaTest") {
          dir("META-INF") {
            file("persistence.xml")
          }
        }
      }
    })
  }
}
