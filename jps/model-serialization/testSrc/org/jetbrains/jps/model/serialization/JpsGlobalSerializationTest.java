package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.testFramework.PlatformTestUtil;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.jps.model.library.JpsLibrary;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author nik
 */
public class JpsGlobalSerializationTest extends JpsSerializationTestCase {
  private static final String OPTIONS_DIR = "jps/model-serialization/testData/config/options";

  public void testLoadSdks() {
    loadGlobalSettings();
    final List<JpsLibrary> libraries = myModel.getGlobal().getLibraryCollection().getLibraries();
    assertEquals(3, libraries.size());
    assertEquals("Gant", libraries.get(0).getName());
    final JpsLibrary sdk1 = libraries.get(1);
    assertEquals("1.5", sdk1.getName());
    final JpsLibrary sdk2 = libraries.get(2);
    assertEquals("1.6", sdk2.getName());
  }

  public void testSaveSdks() throws JDOMException, IOException {
    loadGlobalSettings();
    Element actual = new Element("component").setAttribute("name", "ProjectJdkTable");
    JpsSdkTableSerializer.saveSdks(myModel.getGlobal().getLibraryCollection(), actual);
    File jdkTableFile = new File(getTestDataFileAbsolutePath(OPTIONS_DIR), "jdk.table.xml");
    Element expected = JDomSerializationUtil.findComponent(JDOMUtil.loadDocument(jdkTableFile).getRootElement(), "ProjectJdkTable");
    PlatformTestUtil.assertElementsEqual(expected, actual);
  }

  private void loadGlobalSettings() {
    try {
      JpsGlobalLoader.loadGlobalSettings(myModel.getGlobal(), getTestDataFileAbsolutePath(OPTIONS_DIR));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
