package com.intellij.externalSystem;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase;
import org.junit.Assume;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;

@RunWith(Parameterized.class)
public abstract class GradleDependencyUpdaterTestBase extends GradleImportingTestCase {

  @NotNull private static final String GROOVY_LANGUAGE = "Groovy";
  @NotNull private static final String KOTLIN_LANGUAGE = "Kotlin";

  @Parameterized.Parameter(1)
  public String myTestDataExtension;
  @Parameterized.Parameter(2)
  public String myLanguageName;
  protected File myTestDataDir;
  protected DependencyModifierService myModifierService;

  @NotNull
  @Parameterized.Parameters(name = "{2}")
  public static Collection languageExtensions() {
    return Arrays.asList(new Object[][]{
      {"6.5.1", ".gradle", GROOVY_LANGUAGE},
      {"6.5.1", ".gradle.kts", KOTLIN_LANGUAGE}
    });
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myTestDataDir = PathManagerEx.findFileUnderCommunityHome("platform/external-system-api/dependency-updater/testData/gradle");
    assertTrue(myTestDataDir.isDirectory());
    myModifierService = DependencyModifierService.getInstance(getMyProject());
    Assume.assumeTrue(myLanguageName.equals(GROOVY_LANGUAGE));
  }

  protected void assertScriptChanged() throws IOException {
    File expected = new File(myTestDataDir, "expected/" + getTestName(true) + myTestDataExtension);
    String expectedData = FileUtil.loadFile(expected, "UTF-8");
    String actualData = new String(myProjectRoot.findChild("build" + myTestDataExtension).contentsToByteArray(), StandardCharsets.UTF_8);
    assertSameLines(expectedData, actualData);
  }

  protected void assertScriptNotChanged() throws IOException {
    File expected = new File(myTestDataDir, "projects/" + getTestName(true) + myTestDataExtension);
    String expectedData = FileUtil.loadFile(expected, "UTF-8");
    String actualData = new String(myProjectRoot.findChild("build" + myTestDataExtension).contentsToByteArray(), StandardCharsets.UTF_8);
    assertSameLines(expectedData, actualData);
  }

  protected void importProjectFromTemplate() throws IOException {
    File projectFile = new File(myTestDataDir, "projects/" + getTestName(true) + myTestDataExtension);
    createProjectConfig(FileUtil.loadFile(projectFile, "UTF-8"));
    importProject();
  }
}
