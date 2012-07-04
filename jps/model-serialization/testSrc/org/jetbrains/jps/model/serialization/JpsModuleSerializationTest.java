package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.application.PathManager;
import org.jetbrains.jps.model.JpsModelTestCase;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.module.*;

import java.io.IOException;
import java.util.List;

/**
 * @author nik
 */
public class JpsModuleSerializationTest extends JpsModelTestCase {
  public void test() {
    loadProject("iprProject/iprProject.ipr");
    final JpsModule module = assertOneElement(myModel.getProject().getModules());
    assertEquals("iprProject", module.getName());

    final JpsLibrary library = assertOneElement(myModel.getProject().getLibraryCollection().getLibraries());
    assertEquals("junit", library.getName());

    List<JpsDependencyElement> dependencies = module.getDependenciesList().getDependencies();
    JpsSdkDependency sdkDependency = assertInstanceOf(dependencies.get(0), JpsSdkDependency.class);
    assertSame(JpsJavaSdkType.INSTANCE, sdkDependency.getSdkType());
    assertEquals("1.6", sdkDependency.getSdkReference().getLibraryName());
    assertInstanceOf(dependencies.get(1), JpsModuleSourceDependency.class);
    assertInstanceOf(dependencies.get(2), JpsLibraryDependency.class);
    assertInstanceOf(dependencies.get(3), JpsLibraryDependency.class);
  }

  private void loadProject(final String path) {
    try {
      final String projectPath = PathManager.getHomePath() + "/community/jps/model-serialization/testData/" + path;
      JpsProjectLoader.loadProject(myModel.getGlobal(), myModel.getProject(), projectPath);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
