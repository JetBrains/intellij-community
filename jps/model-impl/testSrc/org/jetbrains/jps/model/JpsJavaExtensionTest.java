package org.jetbrains.jps.model;

import org.jetbrains.jps.model.java.*;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsLibraryDependency;
import org.jetbrains.jps.model.module.JpsModule;

/**
 * @author nik
 */
public class JpsJavaExtensionTest extends JpsModelTestCase {
  public void testModule() {
    final JpsModule module = myModel.getProject().addModule(JpsJavaModuleType.INSTANCE, "m");
    final JpsJavaModuleExtension extension = JpsJavaExtensionService.getInstance().getOrCreateModuleExtension(module);
    extension.setOutputUrl("file://path");
    assertEquals("file://path",
                 module.getContainer().getChild(JpsJavaExtensionService.getInstance().getModuleExtensionKind()).getOutputUrl());
  }

  public void testDependency() {
    final JpsModel model = myModel.createModifiableModel(new TestJpsEventDispatcher());
    final JpsModule module = model.getProject().addModule(JpsJavaModuleType.INSTANCE, "m");
    final JpsLibrary library = model.getProject().addLibrary(JpsJavaLibraryType.INSTANCE, "l");
    final JpsLibraryDependency dependency = module.getDependenciesList().addLibraryDependency(library);
    JpsJavaExtensionService.getInstance().getOrCreateDependencyExtension(dependency).setScope(JpsJavaDependencyScope.TEST);
    JpsJavaExtensionService.getInstance().getOrCreateDependencyExtension(dependency).setExported(true);
    model.commit();

    final JpsDependencyElement dep =
      assertOneElement(assertOneElement(myModel.getProject().getModules()).getDependenciesList().getDependencies());
    final JpsJavaDependencyExtension extension =
      dep.getContainer().getChild(JpsJavaExtensionService.getInstance().getDependencyExtensionKind());
    assertTrue(extension.isExported());
    assertSame(JpsJavaDependencyScope.TEST, extension.getScope());
  }
}
