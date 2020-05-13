// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model.serialization;

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.openapi.util.io.FileUtil;
import org.jdom.Element;
import org.jetbrains.jps.model.JpsEncodingConfigurationService;
import org.jetbrains.jps.model.library.JpsLibrary;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static com.intellij.testFramework.assertions.Assertions.assertThat;

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

  public void testSaveSdksAndGlobalLibraries() {
    loadGlobalSettings(OPTIONS_DIR);
    Path targetOptionsDir = saveGlobalSettings();
    Path originalOptionsDir = getTestDataAbsoluteFile(OPTIONS_DIR);
    assertOptionsFilesEqual(originalOptionsDir, targetOptionsDir, "jdk.table.xml");
    assertOptionsFilesEqual(originalOptionsDir, targetOptionsDir, "applicationLibraries.xml");
  }

  private Path saveGlobalSettings() {
    try {
      File targetOptionsDir = FileUtil.createTempDirectory("options", null);
      JpsSerializationManager.getInstance().saveGlobalSettings(myModel.getGlobal(), targetOptionsDir.getAbsolutePath());
      return targetOptionsDir.toPath();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void testLoadPathVariables() {
    loadGlobalSettings(OPTIONS_DIR);
    JpsPathVariablesConfiguration configuration = JpsModelSerializationDataService.getPathVariablesConfiguration(myModel.getGlobal());
    assertNotNull(configuration);
    assertEquals("/home/nik/.m2/repository", configuration.getUserVariableValue(PathMacrosImpl.MAVEN_REPOSITORY));
    assertThat(configuration.getAllUserVariables()).hasSize(1);
  }

  public void testSavePathVariables() {
    loadGlobalSettings(OPTIONS_DIR);
    JpsPathVariablesConfiguration configuration = JpsModelSerializationDataService.getOrCreatePathVariablesConfiguration(myModel.getGlobal());
    configuration.addPathVariable("TOMCAT_HOME", "/home/nik/applications/tomcat");

    Path targetOptionsDir = saveGlobalSettings();
    Path originalOptionsDir = getTestDataAbsoluteFile(OPTIONS_DIR + "AfterChange");
    assertOptionsFilesEqual(originalOptionsDir, targetOptionsDir, "path.macros.xml");
  }

  private void assertOptionsFilesEqual(Path originalOptionsDir, Path targetOptionsDir, final String fileName) {
    JpsMacroExpander expander = new JpsMacroExpander(getPathVariables());
    Element actual = JpsLoaderBase.loadRootElement(targetOptionsDir.resolve(fileName), expander);
    assertThat(actual).isEqualTo(JpsLoaderBase.loadRootElement(originalOptionsDir.resolve(fileName), expander));
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
