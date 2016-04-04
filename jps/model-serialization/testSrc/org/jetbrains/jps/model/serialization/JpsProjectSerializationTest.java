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
package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.PlatformTestUtil;
import org.jdom.Element;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.JpsEncodingConfigurationService;
import org.jetbrains.jps.model.JpsEncodingProjectConfiguration;
import org.jetbrains.jps.model.artifact.JpsArtifactService;
import org.jetbrains.jps.model.java.*;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.library.sdk.JpsSdkReference;
import org.jetbrains.jps.model.module.*;
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer;
import org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class JpsProjectSerializationTest extends JpsSerializationTestCase {
  public static final String SAMPLE_PROJECT_PATH = "/jps/model-serialization/testData/sampleProject";

  public void testLoadProject() {
    loadProject(SAMPLE_PROJECT_PATH);
    String baseDirPath = getTestDataFileAbsolutePath(SAMPLE_PROJECT_PATH);
    assertTrue(FileUtil.filesEqual(new File(baseDirPath), JpsModelSerializationDataService.getBaseDirectory(myProject)));
    assertEquals("sampleProjectName", myProject.getName());
    List<JpsModule> modules = myProject.getModules();
    assertEquals(3, modules.size());
    JpsModule main = modules.get(0);
    assertEquals("main", main.getName());
    JpsModule util = modules.get(1);
    assertEquals("util", util.getName());
    JpsModule xxx = modules.get(2);
    assertEquals("xxx", xxx.getName());

    assertTrue(FileUtil.filesEqual(new File(baseDirPath, "util"), JpsModelSerializationDataService.getBaseDirectory(util)));

    List<JpsLibrary> libraries = myProject.getLibraryCollection().getLibraries();
    assertEquals(3, libraries.size());

    List<JpsDependencyElement> dependencies = util.getDependenciesList().getDependencies();
    assertEquals(4, dependencies.size());
    JpsSdkDependency sdkDependency = assertInstanceOf(dependencies.get(0), JpsSdkDependency.class);
    assertSame(JpsJavaSdkType.INSTANCE, sdkDependency.getSdkType());
    JpsSdkReference<?> reference = sdkDependency.getSdkReference();
    assertNotNull(reference);
    assertEquals("1.5", reference.getSdkName());
    assertInstanceOf(dependencies.get(1), JpsModuleSourceDependency.class);
    assertInstanceOf(dependencies.get(2), JpsLibraryDependency.class);
    assertInstanceOf(dependencies.get(3), JpsLibraryDependency.class);

    JpsSdkDependency inheritedSdkDependency = assertInstanceOf(main.getDependenciesList().getDependencies().get(0), JpsSdkDependency.class);
    JpsSdkReference<?> projectSdkReference = inheritedSdkDependency.getSdkReference();
    assertNotNull(projectSdkReference);
    assertEquals("1.6", projectSdkReference.getSdkName());

    assertEquals(getUrl("xxx/output"), JpsJavaExtensionService.getInstance().getOutputUrl(xxx, true));
    assertEquals(getUrl("xxx/output"), JpsJavaExtensionService.getInstance().getOutputUrl(xxx, false));
  }

  public void testFileBasedProjectNameAndBaseDir() {
    String relativePath = "/jps/model-serialization/testData/run-configurations/run-configurations.ipr";
    String absolutePath = getTestDataFileAbsolutePath(relativePath);
    loadProject(relativePath);
    assertEquals("run-configurations", myProject.getName());
    assertTrue(FileUtil.filesEqual(new File(absolutePath).getParentFile(), JpsModelSerializationDataService.getBaseDirectory(myProject)));
  }

  public void testDirectoryBasedProjectName() {
    loadProject("/jps/model-serialization/testData/run-configurations-dir");
    assertEquals("run-configurations-dir", myProject.getName());
  }

  public void testImlUnderDotIdea() {
    loadProject("/jps/model-serialization/testData/imlUnderDotIdea");
    JpsModule module = assertOneElement(myProject.getModules());
    JpsModuleSourceRoot root = assertOneElement(module.getSourceRoots());
    assertEquals(getUrl("src"), root.getUrl());
  }

  public void testTestModuleProperties() {
    loadProject("/jps/model-serialization/testData/testModuleProperties/testModuleProperties.ipr");
    List<JpsModule> modules = myProject.getModules();
    assertEquals(2, modules.size());
    JpsModule testModule = modules.get(0);
    assertEquals("testModule", testModule.getName());
    JpsModule productionModule = modules.get(1);
    assertEquals("productionModule", productionModule.getName());

    assertNull(JpsJavaExtensionService.getInstance().getTestModuleProperties(productionModule));
    JpsTestModuleProperties testModuleProperties = JpsJavaExtensionService.getInstance().getTestModuleProperties(testModule);
    assertNotNull(testModuleProperties);
    assertEquals("productionModule", testModuleProperties.getProductionModuleReference().getModuleName());
    assertSame(productionModule, testModuleProperties.getProductionModule());
  }

  public void testProjectSdkWithoutType() {
    loadProject("/jps/model-serialization/testData/projectSdkWithoutType/projectSdkWithoutType.ipr");
    JpsSdkReference<JpsDummyElement> reference = myProject.getSdkReferencesTable().getSdkReference(JpsJavaSdkType.INSTANCE);
    assertNotNull(reference);
    assertEquals("1.6", reference.getSdkName());
  }

  public void testInvalidDependencyScope() {
    loadProject("/jps/model-serialization/testData/invalidDependencyScope/invalidDependencyScope.ipr");
    JpsModule module = assertOneElement(myProject.getModules());
    List<JpsDependencyElement> dependencies = module.getDependenciesList().getDependencies();
    assertEquals(3, dependencies.size());
    JpsJavaDependencyExtension extension = JpsJavaExtensionService.getInstance().getDependencyExtension(dependencies.get(2));
    assertNotNull(extension);
    assertEquals(JpsJavaDependencyScope.COMPILE, extension.getScope());
  }

  public void testDuplicatedModuleLibrary() {
    loadProject("/jps/model-serialization/testData/duplicatedModuleLibrary/duplicatedModuleLibrary.ipr");
    JpsModule module = assertOneElement(myProject.getModules());
    List<JpsDependencyElement> dependencies = module.getDependenciesList().getDependencies();
    assertEquals(4, dependencies.size());
    JpsLibrary lib1 = assertInstanceOf(dependencies.get(2), JpsLibraryDependency.class).getLibrary();
    assertNotNull(lib1);
    assertSameElements(lib1.getRootUrls(JpsOrderRootType.COMPILED), getUrl("data/lib1"));
    JpsLibrary lib2 = assertInstanceOf(dependencies.get(3), JpsLibraryDependency.class).getLibrary();
    assertNotSame(lib1, lib2);
    assertNotNull(lib2);
    assertSameElements(lib2.getRootUrls(JpsOrderRootType.COMPILED), getUrl("data/lib2"));
  }

  public void testDotIdeaUnderDotIdea() {
    loadProject("/jps/model-serialization/testData/matryoshka/.idea");
    JpsJavaProjectExtension extension = JpsJavaExtensionService.getInstance().getProjectExtension(myProject);
    assertNotNull(extension);
    assertEquals(getUrl("out"), extension.getOutputUrl());
  }

  public void testLoadEncoding() {
    loadProject(SAMPLE_PROJECT_PATH);
    JpsEncodingConfigurationService service = JpsEncodingConfigurationService.getInstance();
    assertEquals("UTF-8", service.getProjectEncoding(myModel));
    JpsEncodingProjectConfiguration configuration = service.getEncodingConfiguration(myProject);
    assertNotNull(configuration);
    assertEquals("UTF-8", configuration.getProjectEncoding());
    assertEquals("windows-1251", configuration.getEncoding(new File(getAbsolutePath("util"))));
    assertEquals("windows-1251", configuration.getEncoding(new File(getAbsolutePath("util/foo/bar/file.txt"))));
    assertEquals("UTF-8", configuration.getEncoding(new File(getAbsolutePath("other"))));
  }

  public void testResourceRoots() {
    String projectPath = "/jps/model-serialization/testData/resourceRoots/";
    loadProject(projectPath + "resourceRoots.ipr");
    JpsModule module = assertOneElement(myProject.getModules());
    List<JpsModuleSourceRoot> roots = module.getSourceRoots();
    assertSame(JavaSourceRootType.SOURCE, roots.get(0).getRootType());
    checkResourceRoot(roots.get(1), false, "");
    checkResourceRoot(roots.get(2), true, "");
    checkResourceRoot(roots.get(3), true, "foo");
    doTestSaveModule(module, projectPath + "resourceRoots.iml");
  }

  private static void checkResourceRoot(JpsModuleSourceRoot root, boolean forGenerated, String relativeOutput) {
    assertSame(JavaResourceRootType.RESOURCE, root.getRootType());
    JavaResourceRootProperties properties = root.getProperties(JavaResourceRootType.RESOURCE);
    assertNotNull(properties);
    assertEquals(forGenerated, properties.isForGeneratedSources());
    assertEquals(relativeOutput, properties.getRelativeOutputPath());
  }

  public void testSaveProject() {
    loadProject(SAMPLE_PROJECT_PATH);
    List<JpsModule> modules = myProject.getModules();
    doTestSaveModule(modules.get(0), SAMPLE_PROJECT_PATH + "/main.iml");
    doTestSaveModule(modules.get(1), SAMPLE_PROJECT_PATH + "/util/util.iml");
    //tod[nik] remember that test output root wasn't specified and doesn't save it to avoid unnecessary modifications of iml files
    //doTestSaveModule(modules.get(2), "xxx/xxx.iml");

    File[] libs = getFileInSampleProject(".idea/libraries").listFiles();
    assertNotNull(libs);
    for (File libFile : libs) {
      String libName = FileUtil.getNameWithoutExtension(libFile);
      JpsLibrary library = myProject.getLibraryCollection().findLibrary(libName);
      assertNotNull(libName, library);
      doTestSaveLibrary(libFile, libName, library);
    }
  }

  private void doTestSaveLibrary(File libFile, String libName, JpsLibrary library) {
    try {
      Element actual = new Element("library");
      JpsLibraryTableSerializer.saveLibrary(library, actual, libName);
      JpsMacroExpander
        macroExpander = JpsProjectLoader.createProjectMacroExpander(Collections.<String, String>emptyMap(), getFileInSampleProject(""));
      Element rootElement = JpsLoaderBase.loadRootElement(libFile, macroExpander);
      Element expected = rootElement.getChild("library");
      PlatformTestUtil.assertElementsEqual(expected, actual);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void doTestSaveModule(JpsModule module, final String moduleFilePath) {
    try {
      Element actual = JDomSerializationUtil.createComponentElement("NewModuleRootManager");
      JpsModuleRootModelSerializer.saveRootModel(module, actual);
      File imlFile = new File(getTestDataFileAbsolutePath(moduleFilePath));
      Element rootElement = loadModuleRootTag(imlFile);
      Element expected = JDomSerializationUtil.findComponent(rootElement, "NewModuleRootManager");
      PlatformTestUtil.assertElementsEqual(expected, actual);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public File getFileInSampleProject(String relativePath) {
    return new File(getTestDataFileAbsolutePath(SAMPLE_PROJECT_PATH + "/" + relativePath));
  }

  public void testLoadIdeaProject() {
    long start = System.currentTimeMillis();
    loadProjectByAbsolutePath(PathManager.getHomePath());
    assertTrue(myProject.getModules().size() > 0);
    System.out.println("JpsProjectSerializationTest: " + myProject.getModules().size() + " modules, " + myProject.getLibraryCollection().getLibraries().size() + " libraries and " +
                       JpsArtifactService.getInstance().getArtifacts(myProject).size() + " artifacts loaded in " + (System.currentTimeMillis() - start) + "ms");
  }
}
