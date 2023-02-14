// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.JpsEncodingConfigurationService;
import org.jetbrains.jps.model.JpsEncodingProjectConfiguration;
import org.jetbrains.jps.model.JpsExcludePattern;
import org.jetbrains.jps.model.artifact.JpsArtifactService;
import org.jetbrains.jps.model.java.*;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.library.sdk.JpsSdkReference;
import org.jetbrains.jps.model.module.*;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class JpsProjectSerializationTest extends JpsSerializationTestCase {
  public static final String SAMPLE_PROJECT_PATH = "/jps/model-serialization/testData/sampleProject";
  public static final String SAMPLE_PROJECT_IPR_PATH = "/jps/model-serialization/testData/sampleProject-ipr/sampleProject.ipr";

  public void testLoadProject() {
    loadProject(SAMPLE_PROJECT_PATH);
    assertEquals("sampleProject", myProject.getName());
    checkSampleProjectConfiguration(getTestDataAbsoluteFile(SAMPLE_PROJECT_PATH).toFile());
  }

  public void testLoadIprProject() {
    loadProject(SAMPLE_PROJECT_IPR_PATH);
    assertEquals("sampleProject", myProject.getName());
    checkSampleProjectConfiguration(getTestDataAbsoluteFile(SAMPLE_PROJECT_IPR_PATH).toFile().getParentFile());
  }

  private void checkSampleProjectConfiguration(File baseDirPath) {
    assertTrue(FileUtil.filesEqual(baseDirPath, JpsModelSerializationDataService.getBaseDirectory(myProject)));
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
    Path absolutePath = getTestDataAbsoluteFile(relativePath);
    loadProject(relativePath);
    assertEquals("run-configurations", myProject.getName());
    assertTrue(FileUtil.filesEqual(absolutePath.getParent().toFile(), JpsModelSerializationDataService.getBaseDirectory(myProject)));
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

  public void testInvalidLanguageLevel() {
    loadProject("/jps/model-serialization/testData/testInvalidLanguageLevel/testInvalidLanguageLevel.ipr");
    List<JpsModule> modules = myProject.getModules();
    assertEquals(1, modules.size());
    JpsModule testModule = modules.get(0);
    assertEquals("testModule", testModule.getName());

    JpsJavaModuleExtension moduleExtension = JpsJavaExtensionService.getInstance().getModuleExtension(testModule);
    assertNull(moduleExtension.getLanguageLevel());
    JpsJavaProjectExtension projectExtension = JpsJavaExtensionService.getInstance().getProjectExtension(myProject);
    assertEquals(LanguageLevel.JDK_1_6, projectExtension.getLanguageLevel());
  }

  public void testExcludePatterns() {
    String projectPath = "/jps/model-serialization/testData/excludePatterns";
    loadProject(projectPath + "/excludePatterns.ipr");
    JpsModule module = assertOneElement(myProject.getModules());
    JpsExcludePattern pattern = assertOneElement(module.getExcludePatterns());
    assertEquals("*.class", pattern.getPattern());
    assertEquals(assertOneElement(module.getContentRootsList().getUrls()), pattern.getBaseDirUrl());
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
    checkEncodingConfigurationInSampleProject();
  }

  public void testLoadEncodingIpr() {
    loadProject(SAMPLE_PROJECT_IPR_PATH);
    checkEncodingConfigurationInSampleProject();
  }

  private void checkEncodingConfigurationInSampleProject() {
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
  }

  public void testMissingModuleSourcesOrderEntry() {
    loadProject("/jps/model-serialization/testData/missingModuleSourcesOrderEntry/missingModuleSourcesOrderEntry.ipr");
    JpsModule module = assertOneElement(myProject.getModules());
    List<JpsDependencyElement> dependencies = module.getDependenciesList().getDependencies();
    assertEquals(2, dependencies.size());
    assertInstanceOf(dependencies.get(0), JpsSdkDependency.class);
    assertInstanceOf(dependencies.get(1), JpsModuleSourceDependency.class);
  }

  private static void checkResourceRoot(JpsModuleSourceRoot root, boolean forGenerated, String relativeOutput) {
    assertSame(JavaResourceRootType.RESOURCE, root.getRootType());
    JavaResourceRootProperties properties = root.getProperties(JavaResourceRootType.RESOURCE);
    assertNotNull(properties);
    assertEquals(forGenerated, properties.isForGeneratedSources());
    assertEquals(relativeOutput, properties.getRelativeOutputPath());
  }

  public void testUnloadedModule() {
    String projectPath = "/jps/model-serialization/testData/unloadedModule";
    loadProject(projectPath);
    assertEquals("main", assertOneElement(myProject.getModules()).getName());
  }

  public void testMissingImlFile() {
    loadProject("/jps/model-serialization/testData/missingImlFile/missingImlFile.ipr");
    assertEmpty(myProject.getModules());
  }

  public void testMissingContentUrlAttribute() {
    loadProject("/jps/model-serialization/testData/missingContentUrlAttribute/missingContentUrlAttribute.ipr");
    JpsModule module = assertOneElement(myProject.getModules());
    assertEquals("missingContentUrlAttribute", module.getName());
  }

  public void testLoadIdeaProject() {
    long start = System.nanoTime();
    loadProjectByAbsolutePath(PathManager.getHomePath());
    assertTrue(myProject.getModules().size() > 0);
    System.out.println("JpsProjectSerializationTest: " + myProject.getModules().size() + " modules, " + myProject.getLibraryCollection().getLibraries().size() + " libraries and " +
                       JpsArtifactService.getInstance().getArtifacts(myProject).size() + " artifacts loaded in " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) + "ms");
  }

  public void testExcludesInLibraries() {
    loadProject("/jps/model-serialization/testData/excludesInLibraries");
    JpsLibrary library = assertOneElement(myProject.getLibraryCollection().getLibraries());
    assertEquals("junit", library.getName());
    assertEquals(JpsPathUtil.getLibraryRootUrl(new File(getAbsolutePath("lib/junit.jar"))), assertOneElement(library.getRoots(JpsOrderRootType.COMPILED)).getUrl());
  }
}
