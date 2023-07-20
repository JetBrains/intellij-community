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
package org.jetbrains.jps.model;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.jps.model.java.*;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.module.*;

import java.util.List;

public class JpsModuleTest extends JpsModelTestCase {
  public void testAddSourceRoot() {
    final JpsModule module = myProject.addModule("m", JpsJavaModuleType.INSTANCE);
    JavaSourceRootProperties properties = JpsJavaExtensionService.getInstance().createSourceRootProperties("com.xxx");
    module.addSourceRoot("file://url", JavaSourceRootType.SOURCE, properties);

    final JpsModuleSourceRoot root = assertOneElement(module.getSourceRoots());
    assertEquals("file://url", root.getUrl());
    assertSameElements(ContainerUtil.newArrayList(module.getSourceRoots(JavaSourceRootType.SOURCE)), root);
    assertEmpty(ContainerUtil.newArrayList(module.getSourceRoots(JavaSourceRootType.TEST_SOURCE)));
    JpsTypedModuleSourceRoot<JavaSourceRootProperties> typedRoot = root.asTyped(JavaSourceRootType.SOURCE);
    assertNotNull(typedRoot);
    assertEquals("com.xxx", typedRoot.getProperties().getPackagePrefix());
  }

  public void testGetModulesOfType() {
    JpsProject project = myProject;
    JpsModule module = project.addModule("m", JpsJavaModuleType.INSTANCE);
    Iterable<JpsTypedModule<JpsDummyElement>> modules = project.getModules(JpsJavaModuleType.INSTANCE);
    assertSameElements(ContainerUtil.newArrayList(modules), module);
  }

  public void testExcludedPatterns() {
    JpsModule module = myProject.addModule("m", JpsJavaModuleType.INSTANCE);
    module.addExcludePattern("file://url", "*.class");
    JpsExcludePattern pattern = assertOneElement(module.getExcludePatterns());
    assertEquals("file://url", pattern.getBaseDirUrl());
    assertEquals("*.class", pattern.getPattern());
  }

  public void testAddDependency() {
    final JpsModule module = myProject.addModule("m", JpsJavaModuleType.INSTANCE);
    final JpsLibrary library = myProject.addLibrary("l", JpsJavaLibraryType.INSTANCE);
    final JpsModule dep = myProject.addModule("dep", JpsJavaModuleType.INSTANCE);
    module.getDependenciesList().addLibraryDependency(library);
    module.getDependenciesList().addModuleDependency(dep);

    final List<? extends JpsDependencyElement> dependencies = module.getDependenciesList().getDependencies();
    assertEquals(3, dependencies.size());
    assertInstanceOf(dependencies.get(0), JpsModuleSourceDependency.class);
    assertSame(library, assertInstanceOf(dependencies.get(1), JpsLibraryDependency.class).getLibrary());
    assertSame(dep, assertInstanceOf(dependencies.get(2), JpsModuleDependency.class).getModule());
  }

  public void testCreateReferenceByModule() {
    final JpsModule module = myProject.addModule("m", JpsJavaModuleType.INSTANCE);
    final JpsModuleReference reference = module.createReference().asExternal(myModel);
    assertEquals("m", reference.getModuleName());
    assertSame(module, reference.resolve());
  }

  public void testCreateReferenceByName() {
    final JpsModuleReference reference = JpsElementFactory.getInstance().createModuleReference("m").asExternal(myModel);
    assertEquals("m", reference.getModuleName());
    assertNull(reference.resolve());

    final JpsModule module = myProject.addModule("m", JpsJavaModuleType.INSTANCE);
    assertSame(module, reference.resolve());
  }

  public void testSdkDependency() {
    JpsSdk<JpsDummyElement> sdk = myModel.getGlobal().addSdk("sdk", null, null, JpsJavaSdkType.INSTANCE).getProperties();
    final JpsModule module = myProject.addModule("m", JpsJavaModuleType.INSTANCE);
    module.getSdkReferencesTable().setSdkReference(JpsJavaSdkType.INSTANCE, sdk.createReference());
    module.getDependenciesList().addSdkDependency(JpsJavaSdkType.INSTANCE);

    List<JpsDependencyElement> dependencies = module.getDependenciesList().getDependencies();
    assertEquals(2, dependencies.size());
    final JpsSdkDependency dependency = assertInstanceOf(dependencies.get(1), JpsSdkDependency.class);
    assertSame(sdk.getParent(), dependency.resolveSdk());
  }
}
