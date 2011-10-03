package org.jetbrains.jps;

/**
 * @author nik
 */
public class ModuleClasspathTest extends JpsBuildTestCase {
  private Project project

  @Override
  protected void setUp() {
    super.setUp()
    project = loadProject(getProjectPath(), [:], {Project project ->
      project.createJavaSdk("1.6", "jdk16") {
        classpath "/jdk.jar"
      }
    })
  }

  private String getProjectPath() {
    return "testData/moduleClasspath/moduleClasspath.ipr"
  }

  public void testSimpleClasspath() {
    assertClasspath('util', ClasspathKind.PRODUCTION_COMPILE, ["util/lib/exported.jar", "/jdk.jar"])
    assertClasspath('util', ClasspathKind.PRODUCTION_RUNTIME, ["util/lib/exported.jar", "/jdk.jar", "out/production/util"])
  }

  public void testScopes() {
    assertClasspath("test-util", ClasspathKind.PRODUCTION_COMPILE,
                    ["/jdk.jar", "test-util/lib/provided.jar"])
    assertClasspath("test-util", ClasspathKind.TEST_COMPILE,
                    ["/jdk.jar", "test-util/lib/provided.jar", "test-util/lib/test.jar", "out/production/test-util"])
    assertClasspath("test-util", ClasspathKind.PRODUCTION_RUNTIME,
                    ["/jdk.jar", "test-util/lib/runtime.jar", "out/production/test-util"])
    assertClasspath("test-util", ClasspathKind.TEST_RUNTIME,
                    ["/jdk.jar", "test-util/lib/provided.jar", "test-util/lib/runtime.jar",
                     "test-util/lib/test.jar", "out/test/test-util", "out/production/test-util"])
  }

  public void testDepModules() {
    assertClasspath("main", ClasspathKind.PRODUCTION_COMPILE,
            ["util/lib/exported.jar", "out/production/util", "/jdk.jar", "main/lib/service.jar"])
    assertClasspath("main", ClasspathKind.TEST_COMPILE,
            ["out/production/main", "util/lib/exported.jar", "out/test/util", "out/production/util", "/jdk.jar",
             "out/test/test-util", "out/production/test-util", "main/lib/service.jar"])

    assertClasspath("main", ClasspathKind.PRODUCTION_RUNTIME,
            ["out/production/main", "util/lib/exported.jar", "/jdk.jar", "out/production/util",
             "main/lib/service.jar"])
    assertClasspath("main", ClasspathKind.TEST_RUNTIME,
            ["out/test/main", "out/production/main", "util/lib/exported.jar", "/jdk.jar", "out/test/util", "out/production/util",
             "test-util/lib/provided.jar", "test-util/lib/runtime.jar", "test-util/lib/test.jar", "out/test/test-util",
             "out/production/test-util","main/lib/service.jar"])
  }

  private def assertClasspath(String module, ClasspathKind classpathKind, List<String> expected) {
    String basePath = PathUtil.toSystemIndependentPath(new File(getProjectPath()).parentFile.absolutePath) + "/"
    List<String> classpath = project.builder.moduleClasspath(project.modules[module], classpathKind)
    List<String> actual = classpath.collect { String path ->
      path.startsWith(basePath) ? path.substring(basePath.length()) : path
    }
    assertEquals(expected.join("\n"), actual.join("\n"))
  }
}
