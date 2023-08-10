// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.compiler.*;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.util.Map;

public class JpsCompilerConfigurationTest extends JpsSerializationTestCase {
  public void testLoadFromIpr() {
    doTest("jps/model-serialization/testData/compilerConfiguration/compilerConfiguration.ipr");
  }

  public void testLoadFromDirectory() {
    doTest("jps/model-serialization/testData/compilerConfigurationDir");
  }

  private void doTest(final String path) {
    loadProject(path);
    JpsJavaCompilerConfiguration configuration = JpsJavaExtensionService.getInstance().getCompilerConfiguration(myProject);
    assertNotNull(configuration);
    assertFalse(configuration.isClearOutputDirectoryOnRebuild());
    assertFalse(configuration.isAddNotNullAssertions());
    ProcessorConfigProfile defaultProfile = configuration.getDefaultAnnotationProcessingProfile();
    assertTrue(defaultProfile.isEnabled());
    assertFalse(defaultProfile.isObtainProcessorsFromClasspath());
    String srcDir = JpsPathUtil.urlToPath(getUrl("src"));
    assertEquals(FileUtil.toSystemDependentName(srcDir), defaultProfile.getProcessorPath());
    assertEquals("b", defaultProfile.getProcessorOptions().get("a"));
    assertEquals("d", defaultProfile.getProcessorOptions().get("c"));
    assertEquals("gen", defaultProfile.getGeneratedSourcesDirectoryName(false));

    JpsCompilerExcludes excludes = configuration.getCompilerExcludes();
    assertSameElements(excludes.getExcludedFiles(), new File(srcDir, "A.java"));
    assertSameElements(excludes.getExcludedDirectories(), new File(srcDir, "nonrec"));
    assertSameElements(excludes.getRecursivelyExcludedDirectories(), new File(srcDir, "rec"));

    assertFalse(isExcluded(excludes, "src/nonrec/x/Y.java"));
    assertTrue(isExcluded(excludes, "src/nonrec/Y.java"));
    assertTrue(isExcluded(excludes, "src/rec/x/Y.java"));
    assertTrue(isExcluded(excludes, "src/rec/Y.java"));
    assertTrue(isExcluded(excludes, "src/A.java"));
    assertFalse(isExcluded(excludes, "src/B.java"));

    JpsJavaCompilerOptions options = configuration.getCurrentCompilerOptions();
    assertNotNull(options);
    assertEquals(512, options.MAXIMUM_HEAP_SIZE);
    assertFalse(options.DEBUGGING_INFO);
    assertTrue(options.GENERATE_NO_WARNINGS);
    assertEquals("-Xlint", options.ADDITIONAL_OPTIONS_STRING);
    final Map<String, String> override = options.ADDITIONAL_OPTIONS_OVERRIDE;
    assertEquals(2, override.size());
    assertEquals("-param_1", override.get("mod_1"));
    assertEquals("-param_2", override.get("mod_2"));

    JpsValidationConfiguration validationConfiguration = configuration.getValidationConfiguration();
    assertTrue(validationConfiguration.isValidateOnBuild());
    assertTrue(validationConfiguration.isValidatorEnabled("Jasper Validator"));
    assertFalse(validationConfiguration.isValidatorEnabled("Hibernate Validator"));
    assertTrue(validationConfiguration.isValidatorEnabled("JPA Validator"));
  }

  private boolean isExcluded(JpsCompilerExcludes excludes, final String path) {
    return excludes.isExcluded(JpsPathUtil.urlToFile(getUrl(path)));
  }
}
