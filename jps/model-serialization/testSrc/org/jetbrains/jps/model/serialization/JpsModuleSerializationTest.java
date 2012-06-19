package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.application.PathManager;
import org.jetbrains.jps.model.JpsModelTestCase;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.IOException;

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
