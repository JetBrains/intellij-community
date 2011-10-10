package org.jetbrains.jps.scala

import org.jetbrains.jps.JpsBuildTestCase
import org.jetbrains.jps.Project
import org.jetbrains.jps.idea.Facet
import org.jetbrains.jps.ProjectBuilder

class ScalaModuleTest extends JpsBuildTestCase {
  public void test_load_scala_facet() {
    Project project = loadProject("plugins/scala/testData/scala-test", [:]);
    Map<String, Facet> facets = project.modules["mod1"].facets;
    assertEquals(1, facets.size());
    ScalaFacet facet = facets.values().iterator().next();
    assertNotNull(facet);
    assertEquals("scala-compiler-2.9.1", facet.compilerLibraryName);
  }

  public void test_compile_scala_module() {
    def projectPath = "plugins/scala/testData/scala-test"
    Project project = loadProject(projectPath, [:]);
    ProjectBuilder builder = createBuilder(project)
    builder.clean();
    builder.makeModule(project.modules["mod1"]);

    def path = projectPath + "/out/production/mod1/HelloWorld.class"
    assertTrue(path, new File(path).isFile());
  }

  public void test_joint_compilation() {
    def projectPath = "plugins/scala/testData/scala-test"
    Project project = loadProject(projectPath, [:]);
    ProjectBuilder builder = createBuilder(project)
    builder.clean();
    builder.makeModule(project.modules["mod2"]);

    def path = projectPath + "/out/production/mod2/"
    assertTrue(path, new File(path, "A.class").isFile());
    assertTrue(path, new File(path, "B.class").isFile());
  }
}
