package org.jetbrains.jps

/**
 * @author nik
 */
class JpaTest extends JpsBuildTestCase {
  public void testOverwriteArtifacts() throws Exception {
    doTest("plugins/jpa/testData/jpaTest/jpaTest.ipr", null, {
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
