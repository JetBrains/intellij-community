package org.jetbrains.jps.builders
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.ProjectPaths
import org.jetbrains.jps.builders.rebuild.JpsRebuildTestCase
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
/**
 * @author nik
 */
public class ModuleClasspathTest extends JpsRebuildTestCase {
  @Override
  protected void setUp() {
    super.setUp()
    myModel.getGlobal().getLibraryCollection().findLibrary("1.6").delete()
    addJdk("1.6", "/jdk.jar")
    addJdk("1.5", "/jdk15.jar")
    loadProject("moduleClasspath/moduleClasspath.ipr")
  }

  private String getProjectPath() {
    return FileUtil.toSystemIndependentName(getTestDataRootPath()) + "/moduleClasspath/moduleClasspath.ipr"
  }

  public void testSimpleClasspath() {
    assertClasspath('util', JpsJavaClasspathKind.PRODUCTION_COMPILE, ["util/lib/exported.jar", "/jdk15.jar"])
    assertClasspath('util', JpsJavaClasspathKind.PRODUCTION_RUNTIME, ["util/lib/exported.jar", "/jdk15.jar", "out/production/util"])
  }

  public void testScopes() {
    assertClasspath("test-util", JpsJavaClasspathKind.PRODUCTION_COMPILE,
                    ["/jdk.jar", "test-util/lib/provided.jar"])
    assertClasspath("test-util", JpsJavaClasspathKind.TEST_COMPILE,
                    ["/jdk.jar", "test-util/lib/provided.jar", "test-util/lib/test.jar", "out/production/test-util"])
    assertClasspath("test-util", JpsJavaClasspathKind.PRODUCTION_RUNTIME,
                    ["/jdk.jar", "test-util/lib/runtime.jar", "out/production/test-util"])
    assertClasspath("test-util", JpsJavaClasspathKind.TEST_RUNTIME,
                    ["/jdk.jar", "test-util/lib/provided.jar", "test-util/lib/runtime.jar",
                     "test-util/lib/test.jar", "out/test/test-util", "out/production/test-util"])
  }

  public void testDepModules() {
    assertClasspath("main", JpsJavaClasspathKind.PRODUCTION_COMPILE,
            ["util/lib/exported.jar", "out/production/util", "/jdk.jar", "main/lib/service.jar"])
    assertClasspath("main", JpsJavaClasspathKind.TEST_COMPILE,
            ["out/production/main", "util/lib/exported.jar", "out/test/util", "out/production/util", "/jdk.jar",
             "out/test/test-util", "out/production/test-util", "main/lib/service.jar"])

    assertClasspath("main", JpsJavaClasspathKind.PRODUCTION_RUNTIME,
            ["out/production/main", "util/lib/exported.jar", "out/production/util", "/jdk.jar", "main/lib/service.jar"])
    assertClasspath("main", JpsJavaClasspathKind.TEST_RUNTIME,
            ["out/test/main", "out/production/main", "util/lib/exported.jar", "out/test/util", "out/production/util", "/jdk.jar",
             "test-util/lib/provided.jar", "test-util/lib/runtime.jar", "test-util/lib/test.jar", "out/test/test-util",
             "out/production/test-util","main/lib/service.jar"])
  }

  public void testCompilationClasspath() {
    ModuleChunk chunk = createChunk('main')
    assertClasspath(["util/lib/exported.jar", "out/production/util", "/jdk.jar"],
            ProjectPaths.getPathsList(getProjectPaths().getPlatformCompilationClasspath(chunk, false, true)))
    assertClasspath(["main/lib/service.jar"],
            ProjectPaths.getPathsList(getProjectPaths().getCompilationClasspath(chunk, false, true)))
  }

  public void testProjectClasspath() {
    assertClasspath(["out/production/main", "/jdk.jar", "main/lib/service.jar",
                     "test-util/lib/runtime.jar", "out/production/test-util",
                     "util/lib/exported.jar", "/jdk15.jar", "out/production/util"],
                    getProjectPaths().getProjectRuntimeClasspath(false))
  }

  private ProjectPaths getProjectPaths() {
    return new ProjectPaths(myJpsProject)
  }

  private def assertClasspath(String moduleName, JpsJavaClasspathKind classpathKind, List<String> expected) {
    ModuleChunk chunk = createChunk(moduleName)
    final List<String> classpath = new ProjectPaths(myJpsProject).getClasspath(chunk, classpathKind)
    assertClasspath(expected, toSystemIndependentPaths(classpath))
  }

  private ModuleChunk createChunk(String moduleName) {
    def module = myJpsProject.modules.find {it.name == moduleName}
    return new ModuleChunk([module] as Set)
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
