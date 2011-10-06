package org.jetbrains.jps

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.incremental.ProjectPaths

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
      project.createJavaSdk("1.5", "jdk15") {
        classpath "/jdk15.jar"
      }
    })
  }

  private String getProjectPath() {
    return "testData/moduleClasspath/moduleClasspath.ipr"
  }

  public void testSimpleClasspath() {
    assertClasspath('util', ClasspathKind.PRODUCTION_COMPILE, ["util/lib/exported.jar", "/jdk15.jar"])
    assertClasspath('util', ClasspathKind.PRODUCTION_RUNTIME, ["util/lib/exported.jar", "/jdk15.jar", "out/production/util"])
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
            ["out/production/main", "util/lib/exported.jar", "out/production/util", "/jdk.jar", "main/lib/service.jar"])
    assertClasspath("main", ClasspathKind.TEST_RUNTIME,
            ["out/test/main", "out/production/main", "util/lib/exported.jar", "out/test/util", "out/production/util", "/jdk.jar",
             "test-util/lib/provided.jar", "test-util/lib/runtime.jar", "test-util/lib/test.jar", "out/test/test-util",
             "out/production/test-util","main/lib/service.jar"])
  }

  public void testCompilationClasspath() {
    ModuleChunk chunk = new ModuleChunk(project.modules['main'])
    assertClasspath(["util/lib/exported.jar", "out/production/util", "/jdk.jar"],
            ProjectPaths.getPathsList(project.builder.getProjectPaths().getPlatformCompilationClasspath(chunk, false, true)))
    assertClasspath(["main/lib/service.jar"],
            ProjectPaths.getPathsList(project.builder.getProjectPaths().getCompilationClasspath(chunk, false, true)))
  }

  public void testProjectClasspath() {
    assertClasspath(["out/production/main", "/jdk.jar", "main/lib/service.jar",
                     "test-util/lib/runtime.jar", "out/production/test-util",
                     "util/lib/exported.jar", "/jdk15.jar", "out/production/util"],
                    project.builder.getProjectPaths().getProjectRuntimeClasspath(false))
  }

  private def assertClasspath(String module, ClasspathKind classpathKind, List<String> expected) {
    final List<String> classpath = project.builder.moduleClasspath(project.modules[module], classpathKind)
    assertClasspath(expected, toSystemIndependentPaths(classpath))
  }

  private def assertClasspath(List<String> expected, List<String> classpath) {
    String basePath = FileUtil.toSystemIndependentName(new File(getProjectPath()).parentFile.absolutePath) + "/"
    List<String> actual = toSystemIndependentPaths(classpath).collect { String path ->
      path.startsWith(basePath) ? path.substring(basePath.length()) : path
    }
    assertEquals(expected.join("\n"), actual.join("\n"))
  }

  private static List<String> toSystemIndependentPaths(List<String> classpath) {
    final List<String> result = new ArrayList<String>()
    for (String path: classpath) {
      result.add(FileUtil.toSystemIndependentName(path));
    }
    return result
  }
}
