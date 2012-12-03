package org.jetbrains.jps.model.serialization;

import com.intellij.testFramework.PlatformTestUtil;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.jps.model.JpsEncodingConfigurationService;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.serialization.library.JpsSdkTableSerializer;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author nik
 */
public class JpsGlobalSerializationTest extends JpsSerializationTestCase {
  private static final String OPTIONS_DIR = "jps/model-serialization/testData/config/options";

  public void testLoadSdks() {
    loadGlobalSettings(OPTIONS_DIR);
    final List<JpsLibrary> libraries = myModel.getGlobal().getLibraryCollection().getLibraries();
    assertEquals(3, libraries.size());
    assertEquals("Gant", libraries.get(0).getName());
    final JpsLibrary sdk1 = libraries.get(1);
    assertEquals("1.5", sdk1.getName());
    final JpsLibrary sdk2 = libraries.get(2);
    assertEquals("1.6", sdk2.getName());
  }

  public void testSaveSdks() throws JDOMException, IOException {
    loadGlobalSettings(OPTIONS_DIR);
    Element actual = new Element("component").setAttribute("name", "ProjectJdkTable");
    JpsSdkTableSerializer.saveSdks(myModel.getGlobal().getLibraryCollection(), actual);
    File jdkTableFile = new File(getTestDataFileAbsolutePath(OPTIONS_DIR), "jdk.table.xml");
    JpsMacroExpander expander = new JpsMacroExpander(getPathVariables());
    Element expected = JDomSerializationUtil.findComponent(JpsLoaderBase.loadRootElement(jdkTableFile, expander), "ProjectJdkTable");
    PlatformTestUtil.assertElementsEqual(expected, actual);
  }

  public void testLoadEncoding() {
    loadGlobalSettings(OPTIONS_DIR);
    assertEquals("windows-1251", JpsEncodingConfigurationService.getInstance().getGlobalEncoding(myModel.getGlobal()));
  }

  public void testLoadIgnoredFiles() {
    loadGlobalSettings(OPTIONS_DIR);
    assertEquals("CVS;.svn;", myModel.getGlobal().getFileTypesConfiguration().getIgnoredPatternString());
  }
}
