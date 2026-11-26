// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.incomplete;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.codeInspection.unneededThrows.RedundantThrowsDeclarationLocalInspection;
import com.intellij.codeInspection.unusedImport.UnusedImportInspection;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.keymap.MacKeymapUtil;
import com.intellij.openapi.project.IncompleteDependenciesService;
import com.intellij.openapi.project.WriteActionAutoCloseable;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.openapi.project.IncompleteDependenciesServiceKt.asAutoCloseable;
import static com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_21;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public final class IncompleteModelHighlightingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(JAVA_21);
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(projectFixture);

  static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/incompleteHighlighting";

  /**
   * Configures the file as {@link LightDaemonAnalyzerFixture#configureByFile} does, with the shortcut text of the macOS keymap on a Mac.
   */
  private void configureByFile(@NotNull String relativePath) throws IOException {
    String fullPath = daemonFixture.get().getFixture().getTestDataPath() + relativePath;
    Path path = Path.of(fullPath);
    PlatformTestUtil.checkCaseSensitiveFS(fullPath, path.toFile());
    String fileText = StringUtilRt.convertLineSeparators(Files.readString(path));
    if (SystemInfoRt.isMac) {
      fileText = fileText.replace("Ctrl+Click, Ctrl+B", MacKeymapUtil.COMMAND + "Click, " + MacKeymapUtil.COMMAND + "B");
    }
    daemonFixture.get().configureFromFileText(path.getFileName().toString(), fileText);
  }

  private void doTest() throws IOException {
    doTest(daemonFixture.get().getFixture().getTestName(false) + ".java");
  }

  private void doTest(String fileName) throws IOException {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    myFixture.enableInspections(new UnusedImportInspection(), new RedundantThrowsDeclarationLocalInspection());
    IncompleteDependenciesService service = myFixture.getProject().getService(IncompleteDependenciesService.class);
    WriteActionAutoCloseable token = EdtTestUtil.runInEdtAndGet(() -> asAutoCloseable(WriteAction.compute(() -> service.enterIncompleteState(this))));
    try {
      String filePath = BASE_PATH + "/" + fileName;
      configureByFile(filePath);
      daemonFixture.get().doTestConfiguredFile(true, true, filePath);
    }
    finally {
      EdtTestUtil.runInEdtAndWait(() -> token.close());
    }
  }

  @Test public void testSimple() throws IOException { doTest(); }
  
  @Test public void testModuleInfo() throws IOException { doTest("module-info.java"); }

  @Test public void testDefaultLoaderFactory() throws IOException { doTest(); }
  
  @Test public void testServer() throws IOException { doTest(); }
  
  @Test public void testStarImports() throws IOException { doTest(); }
  
  @Test public void testInferenceFailure() throws IOException { doTest(); }

  @Test public void testIncompleteRecursiveParameters() throws IOException { doTest(); }
  
  @Test public void testSuperCall() throws IOException { doTest(); }
  
  @Test public void testFunctionInterface() throws IOException { doTest(); }
}
