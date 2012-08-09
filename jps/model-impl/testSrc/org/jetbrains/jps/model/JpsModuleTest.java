package org.jetbrains.jps.model;

import org.jetbrains.jps.model.java.*;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.module.*;

import java.util.List;

/**
 * @author nik
 */
public class JpsModuleTest extends JpsModelTestCase {
  public void testAddSourceRoot() {
    final JpsModule module = myModel.getProject().addModule("m", JpsJavaModuleType.INSTANCE);
    JpsSimpleElement<JavaSourceRootProperties> properties = JpsElementFactory.getInstance().createSimpleElement(new JavaSourceRootProperties("com.xxx"));
    final JpsModuleSourceRoot sourceRoot = module.addSourceRoot("file://url", JavaSourceRootType.SOURCE, properties);

    assertSameElements(myDispatcher.retrieveAdded(JpsModule.class), module);
    assertSameElements(myDispatcher.retrieveAdded(JpsModuleSourceRoot.class), sourceRoot);

    final JpsModuleSourceRoot root = assertOneElement(module.getSourceRoots());
    assertEquals("file://url", root.getUrl());
    final JpsSimpleElement<JavaSourceRootProperties> properties2 = root.getProperties(JavaSourceRootType.SOURCE);
    assertNotNull(properties2);
    assertEquals("com.xxx", properties2.getData().getPackagePrefix());
  }

  public void testModifiableModel() {
    final JpsModule module = myModel.getProject().addModule("m", JpsJavaModuleType.INSTANCE);
    final JpsModuleSourceRoot root0 = module.addSourceRoot("url1", JavaSourceRootType.SOURCE);
    myDispatcher.clear();

    final JpsModel modifiableModel = myModel.createModifiableModel(new TestJpsEventDispatcher());
    final JpsModule modifiableModule = assertOneElement(modifiableModel.getProject().getModules());
    modifiableModule.addSourceRoot("url2", JavaSourceRootType.TEST_SOURCE);
    modifiableModel.commit();

    assertEmpty(myDispatcher.retrieveAdded(JpsModule.class));
    assertEmpty(myDispatcher.retrieveRemoved(JpsModule.class));

    final List<? extends JpsModuleSourceRoot> roots = module.getSourceRoots();
    assertEquals(2, roots.size());
    assertSame(root0, roots.get(0));
    final JpsModuleSourceRoot root1 = roots.get(1);
    assertEquals("url2", root1.getUrl());
    assertOrderedEquals(myDispatcher.retrieveAdded(JpsModuleSourceRoot.class), root1);
    assertEmpty(myDispatcher.retrieveChanged(JpsModuleSourceRoot.class));
  }

  public void testAddDependency() {
    final JpsModule module = myModel.getProject().addModule("m", JpsJavaModuleType.INSTANCE);
    final JpsLibrary library = myModel.getProject().addLibrary("l", JpsJavaLibraryType.INSTANCE);
    final JpsModule dep = myModel.getProject().addModule("dep", JpsJavaModuleType.INSTANCE);
    module.getDependenciesList().addLibraryDependency(library);
    module.getDependenciesList().addModuleDependency(dep);

    final List<? extends JpsDependencyElement> dependencies = module.getDependenciesList().getDependencies();
    assertEquals(2, dependencies.size());
    assertSame(library, assertInstanceOf(dependencies.get(0), JpsLibraryDependency.class).getLibrary());
    assertSame(dep, assertInstanceOf(dependencies.get(1), JpsModuleDependency.class).getModule());
  }

  public void testChangeElementInModifiableModel() {
    final JpsModule module = myModel.getProject().addModule("m", JpsJavaModuleType.INSTANCE);
    final JpsModule dep = myModel.getProject().addModule("dep", JpsJavaModuleType.INSTANCE);
    final JpsLibrary library = myModel.getProject().addLibrary("l", JpsJavaLibraryType.INSTANCE);
    module.getDependenciesList().addLibraryDependency(library);
    myDispatcher.clear();

    final JpsModel modifiableModel = myModel.createModifiableModel(new TestJpsEventDispatcher());
    final JpsModule m = modifiableModel.getProject().getModules().get(0);
    assertEquals("m", m.getName());
    m.getDependenciesList().getDependencies().get(0).remove();
    m.getDependenciesList().addModuleDependency(dep);
    modifiableModel.commit();
    assertOneElement(myDispatcher.retrieveRemoved(JpsLibraryDependency.class));
    assertSame(dep, assertOneElement(myDispatcher.retrieveAdded(JpsModuleDependency.class)).getModuleReference().resolve());
    assertSame(dep, assertInstanceOf(assertOneElement(module.getDependenciesList().getDependencies()), JpsModuleDependency.class).getModuleReference().resolve());
  }

  public void testCreateReferenceByModule() {
    final JpsModule module = myModel.getProject().addModule("m", JpsJavaModuleType.INSTANCE);
    final JpsModuleReference reference = module.createReference().asExternal(myModel);
    assertEquals("m", reference.getModuleName());
    assertSame(module, reference.resolve());
  }

  public void testCreateReferenceByName() {
    final JpsModuleReference reference = JpsElementFactory.getInstance().createModuleReference("m").asExternal(myModel);
    assertEquals("m", reference.getModuleName());
    assertNull(reference.resolve());

    final JpsModule module = myModel.getProject().addModule("m", JpsJavaModuleType.INSTANCE);
    assertSame(module, reference.resolve());
  }

  public void testSdkDependency() {
    JpsLibrary sdk = myModel.getGlobal().addSdk("sdk", null, null, JpsJavaSdkType.INSTANCE, JpsElementFactory.getInstance().createDummyElement());
    final JpsModule module = myModel.getProject().addModule("m", JpsJavaModuleType.INSTANCE);
    module.getSdkReferencesTable().setSdkReference(JpsJavaSdkType.INSTANCE, sdk.createReference());
    module.getDependenciesList().addSdkDependency(JpsJavaSdkType.INSTANCE);

    final JpsSdkDependency dependency = assertInstanceOf(assertOneElement(module.getDependenciesList().getDependencies()), JpsSdkDependency.class);
    assertSame(sdk, dependency.resolveSdk());
  }
}
