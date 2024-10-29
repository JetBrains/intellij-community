// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.UsefulTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.*;
import org.jetbrains.jps.model.artifact.JpsArtifactService;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.jps.model.java.*;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.library.sdk.JpsSdkReference;
import org.jetbrains.jps.model.module.*;
import org.jetbrains.jps.util.JpsPathUtil;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.intellij.testFramework.UsefulTestCase.assertInstanceOf;
import static com.intellij.testFramework.UsefulTestCase.assertOneElement;
import static org.junit.jupiter.api.Assertions.*;

public class JpsProjectSerializationTest {
  public static final String SAMPLE_PROJECT_PATH = "jps/model-serialization/testData/sampleProject";
  public static final String SAMPLE_PROJECT_IPR_PATH = "jps/model-serialization/testData/sampleProject-ipr/sampleProject.ipr";

  @Test
  public void testLoadProject() {
    JpsProjectData projectData = loadProject(SAMPLE_PROJECT_PATH);
    assertEquals("sampleProject", projectData.getProject().getName());
    checkSampleProjectConfiguration(projectData);
  }

  @Test
  public void testLoadIprProject() {
    JpsProjectData projectData = loadProject(SAMPLE_PROJECT_IPR_PATH);
    assertEquals("sampleProject", projectData.getProject().getName());
    checkSampleProjectConfiguration(projectData);
  }

  private static void checkSampleProjectConfiguration(JpsProjectData projectData) {
    JpsProject project = projectData.getProject();
    File baseDirPath = projectData.getBaseProjectDir().toFile();
    assertTrue(FileUtil.filesEqual(baseDirPath, JpsModelSerializationDataService.getBaseDirectory(project)));
    List<JpsModule> modules = project.getModules();
    assertEquals(3, modules.size());
    JpsModule main = modules.get(0);
    assertEquals("main", main.getName());
    JpsModule util = modules.get(1);
    assertEquals("util", util.getName());
    JpsModule xxx = modules.get(2);
    assertEquals("xxx", xxx.getName());

    assertTrue(FileUtil.filesEqual(new File(baseDirPath, "util"), JpsModelSerializationDataService.getBaseDirectory(util)));

    List<JpsLibrary> libraries = project.getLibraryCollection().getLibraries();
    assertEquals(3, libraries.size());
    assertEquals(project, ((JpsElementBase<?>)libraries.get(0)).getParent().getParent());

    List<JpsDependencyElement> dependencies = util.getDependenciesList().getDependencies();
    assertEquals(4, dependencies.size());
    JpsSdkDependency sdkDependency = assertInstanceOf(dependencies.get(0), JpsSdkDependency.class);
    assertSame(JpsJavaSdkType.INSTANCE, sdkDependency.getSdkType());
    JpsSdkReference<?> reference = sdkDependency.getSdkReference();
    assertNotNull(reference);
    assertEquals("1.5", reference.getSdkName());
    assertInstanceOf(dependencies.get(1), JpsModuleSourceDependency.class);
    assertInstanceOf(dependencies.get(2), JpsLibraryDependency.class);
    JpsLibraryDependency moduleLibraryDependency = assertInstanceOf(dependencies.get(3), JpsLibraryDependency.class);
    JpsLibrary moduleLibrary = moduleLibraryDependency.getLibrary();
    assertEquals("log4j", moduleLibrary.getName());
    assertEquals(util, ((JpsElementBase<?>)moduleLibrary).getParent().getParent());

    assertEquals(projectData.getUrl(""), assertOneElement(main.getContentRootsList().getUrls()));
    assertEquals(projectData.getUrl("src"), assertOneElement(main.getSourceRoots()).getUrl());
    
    JpsSdkDependency inheritedSdkDependency = assertInstanceOf(main.getDependenciesList().getDependencies().get(0), JpsSdkDependency.class);
    JpsSdkReference<?> projectSdkReference = inheritedSdkDependency.getSdkReference();
    assertNotNull(projectSdkReference);
    assertEquals("1.6", projectSdkReference.getSdkName());

    assertEquals(projectData.getUrl("xxx"), assertOneElement(xxx.getContentRootsList().getUrls()));
    assertEquals(projectData.getUrl("xxx/output"), JpsJavaExtensionService.getInstance().getOutputUrl(xxx, true));
    assertEquals(projectData.getUrl("xxx/output"), JpsJavaExtensionService.getInstance().getOutputUrl(xxx, false));
  }

  @Test
  public void testFileBasedProjectNameAndBaseDir() {
    JpsProjectData projectData = loadProject("jps/model-serialization/testData/run-configurations/run-configurations.ipr");
    JpsProject project = projectData.getProject();
    assertEquals("run-configurations", project.getName());
    assertTrue(FileUtil.filesEqual(projectData.getBaseProjectDir().toFile(), JpsModelSerializationDataService.getBaseDirectory(project)));
  }

  @Test
  public void testDirectoryBasedProjectName() {
    JpsProjectData projectData = loadProject("jps/model-serialization/testData/run-configurations-dir");
    assertEquals("run-configurations-dir", projectData.getProject().getName());
  }

  @Test
  public void testImlUnderDotIdea() {
    JpsProjectData projectData = loadProject("jps/model-serialization/testData/imlUnderDotIdea");
    JpsProject project = projectData.getProject();
    JpsModule module = assertOneElement(project.getModules());
    JpsModuleSourceRoot root = assertOneElement(module.getSourceRoots());
    assertEquals(projectData.getUrl("src"), root.getUrl());
  }

  @Test
  public void testTestModuleProperties() {
    JpsProjectData projectData = loadProject("jps/model-serialization/testData/testModuleProperties/testModuleProperties.ipr");
    List<JpsModule> modules = projectData.getProject().getModules();
    assertEquals(2, modules.size());
    JpsModule productionModule = modules.get(0);
    assertEquals("productionModule", productionModule.getName());
    JpsModule testModule = modules.get(1);
    assertEquals("testModule", testModule.getName());

    assertNull(JpsJavaExtensionService.getInstance().getTestModuleProperties(productionModule));
    JpsTestModuleProperties testModuleProperties = JpsJavaExtensionService.getInstance().getTestModuleProperties(testModule);
    assertNotNull(testModuleProperties);
    assertEquals("productionModule", testModuleProperties.getProductionModuleReference().getModuleName());
    assertSame(productionModule, testModuleProperties.getProductionModule());
  }

  @Test
  public void testInvalidLanguageLevel() {
    JpsProjectData projectData = loadProject("jps/model-serialization/testData/testInvalidLanguageLevel/testInvalidLanguageLevel.ipr");
    JpsProject project = projectData.getProject();
    List<JpsModule> modules = project.getModules();
    assertEquals(1, modules.size());
    JpsModule testModule = modules.get(0);
    assertEquals("testModule", testModule.getName());

    JpsJavaModuleExtension moduleExtension = JpsJavaExtensionService.getInstance().getModuleExtension(testModule);
    assertNull(moduleExtension.getLanguageLevel());
    JpsJavaProjectExtension projectExtension = JpsJavaExtensionService.getInstance().getProjectExtension(project);
    assertEquals(LanguageLevel.JDK_1_6, projectExtension.getLanguageLevel());
  }

  @Test
  public void testExcludePatterns() {
    JpsProjectData projectData = loadProject("jps/model-serialization/testData/excludePatterns/excludePatterns.ipr");
    JpsModule module = assertOneElement(projectData.getProject().getModules());
    JpsExcludePattern pattern = assertOneElement(module.getExcludePatterns());
    assertEquals("*.class", pattern.getPattern());
    assertEquals(assertOneElement(module.getContentRootsList().getUrls()), pattern.getBaseDirUrl());
  }

  @Test
  public void testProjectSdkWithoutType() {
    JpsProjectData projectData = loadProject("jps/model-serialization/testData/projectSdkWithoutType/projectSdkWithoutType.ipr");
    JpsSdkReference<JpsDummyElement> reference = projectData.getProject().getSdkReferencesTable().getSdkReference(JpsJavaSdkType.INSTANCE);
    assertNotNull(reference);
    assertEquals("1.6", reference.getSdkName());
  }

  @Test
  public void testInvalidDependencyScope() {
    JpsProjectData projectData = loadProject("jps/model-serialization/testData/invalidDependencyScope/invalidDependencyScope.ipr");
    JpsModule module = assertOneElement(projectData.getProject().getModules());
    List<JpsDependencyElement> dependencies = module.getDependenciesList().getDependencies();
    assertEquals(3, dependencies.size());
    JpsJavaDependencyExtension extension = JpsJavaExtensionService.getInstance().getDependencyExtension(dependencies.get(2));
    assertNotNull(extension);
    assertEquals(JpsJavaDependencyScope.COMPILE, extension.getScope());
  }

  @Test
  public void testDuplicatedModuleLibrary() {
    JpsProjectData projectData = loadProject("jps/model-serialization/testData/duplicatedModuleLibrary/duplicatedModuleLibrary.ipr");
    JpsModule module = assertOneElement(projectData.getProject().getModules());
    List<JpsDependencyElement> dependencies = module.getDependenciesList().getDependencies();
    assertEquals(4, dependencies.size());
    JpsLibrary lib1 = assertInstanceOf(dependencies.get(2), JpsLibraryDependency.class).getLibrary();
    assertNotNull(lib1);
    UsefulTestCase.assertSameElements(lib1.getRootUrls(JpsOrderRootType.COMPILED), projectData.getUrl("data/lib1"));
    JpsLibrary lib2 = assertInstanceOf(dependencies.get(3), JpsLibraryDependency.class).getLibrary();
    assertNotSame(lib1, lib2);
    assertNotNull(lib2);
    UsefulTestCase.assertSameElements(lib2.getRootUrls(JpsOrderRootType.COMPILED), projectData.getUrl("data/lib2"));
  }

  @Test
  public void testDotIdeaUnderDotIdea() {
    JpsProjectData projectData = loadProject("jps/model-serialization/testData/matryoshka/.idea");
    JpsJavaProjectExtension extension = JpsJavaExtensionService.getInstance().getProjectExtension(projectData.getProject());
    assertNotNull(extension);
    assertEquals(projectData.getUrl("out"), extension.getOutputUrl());
  }

  @Test
  public void testLoadEncoding() {
    JpsProjectData projectData = loadProject(SAMPLE_PROJECT_PATH);
    checkEncodingConfigurationInSampleProject(projectData);
  }

  @Test
  public void testLoadEncodingIpr() {
    JpsProjectData projectData = loadProject(SAMPLE_PROJECT_IPR_PATH);
    checkEncodingConfigurationInSampleProject(projectData);
  }

  private void checkEncodingConfigurationInSampleProject(JpsProjectData projectData) {
    JpsEncodingConfigurationService service = JpsEncodingConfigurationService.getInstance();
    assertEquals("UTF-8", service.getProjectEncoding(projectData.getProject().getModel()));
    JpsEncodingProjectConfiguration configuration = service.getEncodingConfiguration(projectData.getProject());
    assertNotNull(configuration);
    assertEquals("UTF-8", configuration.getProjectEncoding());
    assertEquals("windows-1251", configuration.getEncoding(projectData.resolvePath("util").toFile()));
    assertEquals("windows-1251", configuration.getEncoding(projectData.resolvePath("util/foo/bar/file.txt").toFile()));
    assertEquals("UTF-8", configuration.getEncoding(projectData.resolvePath("other").toFile()));
  }

  @Test
  public void testResourceRoots() {
    JpsProjectData projectData = loadProject("jps/model-serialization/testData/resourceRoots/resourceRoots.ipr");
    JpsModule module = assertOneElement(projectData.getProject().getModules());
    List<JpsModuleSourceRoot> roots = module.getSourceRoots();
    assertSame(JavaSourceRootType.SOURCE, roots.get(0).getRootType());
    checkResourceRoot(roots.get(1), false, "");
    checkResourceRoot(roots.get(2), true, "");
    checkResourceRoot(roots.get(3), true, "foo");
  }

  @Test
  public void testMissingModuleSourcesOrderEntry() {
    JpsProjectData projectData =
      loadProject("jps/model-serialization/testData/missingModuleSourcesOrderEntry/missingModuleSourcesOrderEntry.ipr");
    JpsModule module = assertOneElement(projectData.getProject().getModules());
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

  @Test
  public void testUnloadedModule() {
    JpsProjectData projectData = loadProject("jps/model-serialization/testData/unloadedModule");
    assertEquals("main", assertOneElement(projectData.getProject().getModules()).getName());
  }

  @Test
  public void testMissingImlFile() {
    loadProject("jps/model-serialization/testData/missingImlFile/missingImlFile.ipr");
  }

  @Test
  public void testMissingContentUrlAttribute() {
    try {
      JpsProjectData projectData = loadProject("jps/model-serialization/testData/missingContentUrlAttribute/missingContentUrlAttribute.ipr");
      //the current implementation silently skips missing modules 
      JpsModule module = assertOneElement(projectData.getProject().getModules());
      assertEquals("missingContentUrlAttribute", module.getName());
    }
    catch (CannotLoadJpsModelException e) {
      //the new implementation throws an exception
      assertEquals("missingContentUrlAttribute.iml", e.getFile().getName());
    }
  }

  @Test
  public void testLoadIdeaProject() throws IOException {
    long start = System.nanoTime();
    JpsProject project = JpsSerializationManager.getInstance().loadModel(PathManager.getHomePath(), null).getProject();
    assertFalse(project.getModules().isEmpty());
    System.out.println("JpsProjectSerializationTest: " + project.getModules().size() + " modules, " + project.getLibraryCollection().getLibraries().size() + " libraries and " +
                       JpsArtifactService.getInstance().getArtifacts(project).size() + " artifacts loaded in " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) + "ms");
  }

  @Test
  public void testExcludesInLibraries() {
    JpsProjectData projectData = loadProject("jps/model-serialization/testData/excludesInLibraries");
    JpsLibrary library = assertOneElement(projectData.getProject().getLibraryCollection().getLibraries());
    assertEquals("junit", library.getName());
    assertEquals(JpsPathUtil.getLibraryRootUrl(projectData.resolvePath("lib/junit.jar").toFile()), 
                 assertOneElement(library.getRoots(JpsOrderRootType.COMPILED)).getUrl());
  }

  private @NotNull JpsProjectData loadProject(String projectPath) {
    return JpsProjectData.loadFromTestData(projectPath, getClass());
  }
}
