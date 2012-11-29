package org.jetbrains.jps.model;

import org.jetbrains.jps.model.java.*;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsLibraryDependency;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.List;

/**
 * @author nik
 */
public class JpsJavaExtensionTest extends JpsJavaModelTestCase {
  public void testModule() {
    final JpsModule module = addModule();
    final JpsJavaModuleExtension extension = getJavaService().getOrCreateModuleExtension(module);
    extension.setOutputUrl("file://path");
    JpsJavaModuleExtension moduleExtension = getJavaService().getModuleExtension(module);
    assertNotNull(moduleExtension);
    assertEquals("file://path", moduleExtension.getOutputUrl());
  }

  public void testDependency() {
    final JpsModel model = myModel.createModifiableModel(new TestJpsEventDispatcher());
    final JpsModule module = model.getProject().addModule("m", JpsJavaModuleType.INSTANCE);
    final JpsLibrary library = model.getProject().addLibrary("l", JpsJavaLibraryType.INSTANCE);
    final JpsLibraryDependency dependency = module.getDependenciesList().addLibraryDependency(library);
    getJavaService().getOrCreateDependencyExtension(dependency).setScope(JpsJavaDependencyScope.TEST);
    getJavaService().getOrCreateDependencyExtension(dependency).setExported(true);
    model.commit();

    List<JpsDependencyElement> dependencies = assertOneElement(myProject.getModules()).getDependenciesList().getDependencies();
    assertEquals(2, dependencies.size());
    final JpsDependencyElement dep = dependencies.get(1);
    final JpsJavaDependencyExtension extension = getJavaService().getDependencyExtension(dep);
    assertNotNull(extension);
    assertTrue(extension.isExported());
    assertSame(JpsJavaDependencyScope.TEST, extension.getScope());
  }
}
