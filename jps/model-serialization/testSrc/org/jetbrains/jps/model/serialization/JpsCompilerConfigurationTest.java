package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.compiler.JpsCompilerExcludes;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerConfiguration;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerOptions;
import org.jetbrains.jps.model.java.compiler.ProcessorConfigProfile;
import org.jetbrains.jps.util.JpsPathUtil;

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
    assertEquals(FileUtil.toSystemDependentName(JpsPathUtil.urlToPath(getUrl("src"))), defaultProfile.getProcessorPath());
    assertEquals("b", defaultProfile.getProcessorOptions().get("a"));
    assertEquals("d", defaultProfile.getProcessorOptions().get("c"));
    assertEquals("gen", defaultProfile.getGeneratedSourcesDirectoryName(false));
    JpsCompilerExcludes excludes = configuration.getCompilerExcludes();
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
  }

  private boolean isExcluded(JpsCompilerExcludes excludes, final String path) {
    return excludes.isExcluded(JpsPathUtil.urlToFile(getUrl(path)));
  }
}
