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

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.PlatformTestUtil;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.jps.model.JpsEncodingConfigurationService;
import org.jetbrains.jps.model.library.JpsLibrary;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author nik
 */
public class JpsGlobalSerializationTest extends JpsSerializationTestCase {
  private static final String OPTIONS_DIR = "jps/model-serialization/testData/config/options";

  public void testLoadSdksAndGlobalLibraries() {
    loadGlobalSettings(OPTIONS_DIR);
    final List<JpsLibrary> libraries = myModel.getGlobal().getLibraryCollection().getLibraries();
    assertEquals(3, libraries.size());
    assertEquals("Gant", libraries.get(0).getName());
    final JpsLibrary sdk1 = libraries.get(1);
    assertEquals("1.5", sdk1.getName());
    final JpsLibrary sdk2 = libraries.get(2);
    assertEquals("1.6", sdk2.getName());
  }

  public void testSaveSdksAndGlobalLibraries() throws JDOMException, IOException {
    loadGlobalSettings(OPTIONS_DIR);
    File targetOptionsDir = FileUtil.createTempDirectory("options" ,null);
    JpsSerializationManager.getInstance().saveGlobalSettings(myModel.getGlobal(), targetOptionsDir.getAbsolutePath());

    File originalOptionsDir = new File(getTestDataFileAbsolutePath(OPTIONS_DIR));
    assertOptionsFilesEqual(originalOptionsDir, targetOptionsDir, "jdk.table.xml");
    assertOptionsFilesEqual(originalOptionsDir, targetOptionsDir, "applicationLibraries.xml");
  }

  private void assertOptionsFilesEqual(File originalOptionsDir, File targetOptionsDir, final String fileName) throws IOException {
    JpsMacroExpander expander = new JpsMacroExpander(getPathVariables());
    Element expected = JpsLoaderBase.loadRootElement(new File(originalOptionsDir, fileName), expander);
    Element actual = JpsLoaderBase.loadRootElement(new File(targetOptionsDir, fileName), expander);
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
