package org.jetbrains.jps.model.serialization;

import org.jetbrains.jps.model.library.JpsLibrary;

import java.io.IOException;
import java.util.List;

/**
 * @author nik
 */
public class JpsGlobalSerializationTest extends JpsSerializationTestCase {
  public void testLoadSdks() throws IOException {
    JpsGlobalLoader.loadGlobalSettings(myModel.getGlobal(), getTestDataFileAbsolutePath("jps/model-serialization/testData/config/options"));
    final List<JpsLibrary> libraries = myModel.getGlobal().getLibraryCollection().getLibraries();
    assertEquals(3, libraries.size());
    assertEquals("Gant", libraries.get(0).getName());
    final JpsLibrary sdk1 = libraries.get(1);
    assertEquals("1.5", sdk1.getName());
    final JpsLibrary sdk2 = libraries.get(2);
    assertEquals("1.6", sdk2.getName());
  }
}
