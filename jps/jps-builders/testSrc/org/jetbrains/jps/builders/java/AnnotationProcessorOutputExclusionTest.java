// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.java;

import com.intellij.util.PathUtil;
import org.jetbrains.jps.builders.JpsBuildTestCase;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.compiler.ProcessorConfigProfile;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.util.JpsPathUtil;

import static com.intellij.util.io.TestFileSystemItem.fs;

public class AnnotationProcessorOutputExclusionTest extends JpsBuildTestCase {

  public void testApGeneratedSourcesUnderSourceRoot_areExcluded() {
    String file = createFile("src/A.java", "public class A {}");
    createFile("src/generated/Gen.java", "public class Gen {}");

    JpsModule m = addModule("m");
    m.getContentRootsList().addUrl(getUrl(""));
    m.addSourceRoot(JpsPathUtil.pathToUrl(PathUtil.getParentPath(file)), JavaSourceRootType.SOURCE);

    ProcessorConfigProfile profile = JpsJavaExtensionService.getInstance()
      .getCompilerConfiguration(myProject)
      .getAnnotationProcessingProfile(m);
    profile.setEnabled(true);
    profile.setOutputRelativeToContentRoot(true);
    profile.setGeneratedSourcesDirectoryName("src/generated", false);

    rebuildAllModules();

    // Only A.java should be compiled; Gen.java (under AP output dir inside source root) must be excluded
    assertOutput(m, fs().file("A.class"));
  }

  public void testApGeneratedTestSourcesUnderTestRoot_areExcluded() {
    String testFile = createFile("testSrc/ATest.java", "public class ATest {}");
    createFile("testSrc/generated_tests/GenTest.java", "public class GenTest {}");

    JpsModule m = addModule("m");
    m.getContentRootsList().addUrl(getUrl(""));
    m.addSourceRoot(JpsPathUtil.pathToUrl(PathUtil.getParentPath(testFile)), JavaSourceRootType.TEST_SOURCE);

    ProcessorConfigProfile profile = JpsJavaExtensionService.getInstance()
      .getCompilerConfiguration(myProject)
      .getAnnotationProcessingProfile(m);
    profile.setEnabled(true);
    profile.setOutputRelativeToContentRoot(true);
    profile.setGeneratedSourcesDirectoryName("testSrc/generated_tests", true);

    rebuildAllModules();

    // Only ATest.java should be compiled to test output; generated test sources must be excluded
    String testOutputUrl = JpsJavaExtensionService.getInstance().getOutputUrl(m, true);
    assertNotNull(testOutputUrl);
    assertOutput(JpsPathUtil.urlToPath(testOutputUrl), fs().file("ATest.class"));
  }
}
