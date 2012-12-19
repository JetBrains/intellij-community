/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.builders
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.ProjectPaths
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType
import org.jetbrains.jps.builders.rebuild.JpsRebuildTestCase
import org.jetbrains.jps.incremental.ModuleBuildTarget
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
    assertClasspath('util', false, ["util/lib/exported.jar", "/jdk15.jar"])
  }

  public void testScopes() {
    assertClasspath("test-util", false,
                    ["/jdk.jar", "test-util/lib/provided.jar"])
    assertClasspath("test-util", true,
                    ["/jdk.jar", "test-util/lib/provided.jar", "test-util/lib/test.jar", "out/production/test-util"])
  }

  public void testDepModules() {
    assertClasspath("main", false,
            ["util/lib/exported.jar", "out/production/util", "/jdk.jar", "main/lib/service.jar"])
    assertClasspath("main", true,
            ["out/production/main", "util/lib/exported.jar", "out/test/util", "out/production/util", "/jdk.jar",
             "out/test/test-util", "out/production/test-util", "main/lib/service.jar"])
  }

  public void testCompilationClasspath() {
    ModuleChunk chunk = createChunk('main')
    assertClasspath(["util/lib/exported.jar", "out/production/util", "/jdk.jar"],
            getPathsList(ProjectPaths.getPlatformCompilationClasspath(chunk, true)))
    assertClasspath(["main/lib/service.jar"],
            getPathsList(ProjectPaths.getCompilationClasspath(chunk, true)))
  }

  private def assertClasspath(String moduleName, boolean includeTests, List<String> expected) {
    ModuleChunk chunk = createChunk(moduleName)
    final List<String> classpath = getPathsList(new ProjectPaths().getCompilationClasspathFiles(chunk, includeTests, true, true))
    assertClasspath(expected, toSystemIndependentPaths(classpath))
  }

  private ModuleChunk createChunk(String moduleName) {
    def module = myProject.modules.find {it.name == moduleName}
    return new ModuleChunk([new ModuleBuildTarget(module, JavaModuleBuildTargetType.PRODUCTION)] as Set)
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

  public static List<String> getPathsList(Collection<File> files) {
    final List<String> result = new ArrayList<String>();
    for (File file : files) {
      result.add(getCanonicalPath(file));
    }
    return result;
  }
  private static String getCanonicalPath(File file) {
    final String path = file.getPath();
    return path.contains(".")? FileUtil.toCanonicalPath(path) : FileUtil.toSystemIndependentName(path);
  }
}
