// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model.serialization;

import com.intellij.application.options.PathMacrosImpl;
import org.jetbrains.jps.model.JpsEncodingConfigurationService;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.assertions.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class JpsGlobalSerializationTest {
  private static final String OPTIONS_DIR = "jps/model-serialization/testData/config/options";

  @Test
  public void testLoadSdksAndGlobalLibraries() {
    JpsModel model = JpsModelFromTestData.loadGlobalSettings(OPTIONS_DIR, getClass());
    final List<JpsLibrary> libraries = model.getGlobal().getLibraryCollection().getLibraries();
    assertEquals(3, libraries.size());
    assertEquals("Gant", libraries.get(0).getName());
    final JpsLibrary sdk1 = libraries.get(1);
    assertEquals("1.5", sdk1.getName());
    final JpsLibrary sdk2 = libraries.get(2);
    assertEquals("1.6", sdk2.getName());
  }

  @Test
  public void testLoadPathVariables() {
    JpsModel model = JpsModelFromTestData.loadGlobalSettings(OPTIONS_DIR, getClass());
    JpsPathVariablesConfiguration configuration = JpsModelSerializationDataService.getPathVariablesConfiguration(model.getGlobal());
    assertNotNull(configuration);
    assertEquals("/home/nik/.m2/repository", configuration.getUserVariableValue(PathMacrosImpl.MAVEN_REPOSITORY));
    assertThat(configuration.getAllUserVariables()).hasSize(1);
  }

  @Test
  public void testLoadEncoding() {
    JpsModel model = JpsModelFromTestData.loadGlobalSettings(OPTIONS_DIR, getClass());
    assertEquals("windows-1251", JpsEncodingConfigurationService.getInstance().getGlobalEncoding(model.getGlobal()));
  }

  @Test
  public void testLoadIgnoredFiles() {
    JpsModel model = JpsModelFromTestData.loadGlobalSettings(OPTIONS_DIR, getClass());
    assertEquals("CVS;.svn;", model.getGlobal().getFileTypesConfiguration().getIgnoredPatternString());
  }
}
