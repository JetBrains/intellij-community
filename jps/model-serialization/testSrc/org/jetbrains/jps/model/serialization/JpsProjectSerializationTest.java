package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.PlatformTestUtil;
import org.jdom.Element;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.sdk.JpsSdkReference;
import org.jetbrains.jps.model.module.*;

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
    List<JpsModule> modules = myModel.getProject().getModules();
    assertEquals(2, modules.size());
    JpsModule main = modules.get(0);
    assertEquals("main", main.getName());
    JpsModule util = modules.get(1);
    assertEquals("util", util.getName());

    List<JpsLibrary> libraries = myModel.getProject().getLibraryCollection().getLibraries();
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
  }

  public void testSaveProject() {
    loadProject(SAMPLE_PROJECT_PATH);
    JpsModule main = myModel.getProject().getModules().get(0);
    doTestSaveModule(main, "main.iml");

    JpsModule util = myModel.getProject().getModules().get(1);
    doTestSaveModule(util, "util/util.iml");

    File[] libs = getFileInSampleProject(".idea/libraries").listFiles();
    assertNotNull(libs);
    for (File libFile : libs) {
      String libName = FileUtil.getNameWithoutExtension(libFile);
      JpsLibrary library = myModel.getProject().getLibraryCollection().findLibrary(libName);
      assertNotNull(libName, library);
      doTestSaveLibrary(libFile, libName, library);
    }
  }

  private static void doTestSaveLibrary(File libFile, String libName, JpsLibrary library) {
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

  private static void doTestSaveModule(JpsModule module, final String moduleFilePath) {
    try {
      Element actual = JDomSerializationUtil.createComponentElement("NewModuleRootManager");
      JpsModuleSerializer.saveRootModel(module, actual);
      File imlFile = getFileInSampleProject(moduleFilePath);
      Element rootElement = loadModuleRootTag(imlFile);
      Element expected = JDomSerializationUtil.findComponent(rootElement, "NewModuleRootManager");
      PlatformTestUtil.assertElementsEqual(expected, actual);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static File getFileInSampleProject(String relativePath) {
    return new File(getTestDataFileAbsolutePath(SAMPLE_PROJECT_PATH + "/" + relativePath));
  }

  public void _testLoadIdeaProject() {
    long start = System.currentTimeMillis();
    final JpsProject project = myModel.getProject();
    loadProject("");
    assertTrue(project.getModules().size() > 0);
    System.out.println("Time: " + (System.currentTimeMillis() - start));
  }
}
