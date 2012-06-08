package org.jetbrains.jps.model;

import org.jetbrains.jps.model.java.JpsJavaDependencyExtension;
import org.jetbrains.jps.model.java.JpsJavaDependencyScope;
import org.jetbrains.jps.model.java.JpsJavaLibraryType;
import org.jetbrains.jps.model.java.JpsJavaModuleType;
import org.jetbrains.jps.model.java.impl.JavaModuleExtensionKind;
import org.jetbrains.jps.model.java.impl.JpsJavaDependencyExtensionKind;
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
    JavaModuleExtensionKind.getExtension(module).setOutputUrl("file://path");
    assertEquals("file://path", JavaModuleExtensionKind.getExtension(module).getOutputUrl());
  }

  public void testDependency() {
    final JpsModel model = myModel.createModifiableModel(new TestJpsEventDispatcher());
    final JpsModule module = model.getProject().addModule(JpsJavaModuleType.INSTANCE, "m");
    final JpsLibrary library = model.getProject().addLibrary(JpsJavaLibraryType.INSTANCE, "l");
    final JpsLibraryDependency dependency = module.getDependenciesList().addLibraryDependency(library);
    JpsJavaDependencyExtensionKind.getExtension(dependency).setScope(JpsJavaDependencyScope.TEST);
    JpsJavaDependencyExtensionKind.getExtension(dependency).setExported(true);
    model.commit();

    final JpsDependencyElement dep = assertOneElement(assertOneElement(myModel.getProject().getModules()).getDependenciesList().getDependencies());
    final JpsJavaDependencyExtension extension = dep.getContainer().getChild(JpsJavaDependencyExtensionKind.INSTANCE);
    assertTrue(extension.isExported());
    assertSame(JpsJavaDependencyScope.TEST, extension.getScope());
  }
}
