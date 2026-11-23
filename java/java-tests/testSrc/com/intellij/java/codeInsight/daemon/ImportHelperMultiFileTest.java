// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.ProductionDaemonAnalyzerFixture;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NonNls;
import org.junit.jupiter.api.Test;

import java.io.File;

import static com.intellij.codeInsight.daemon.ProductionDaemonAnalyzerFixture.productionDaemonAnalyzerFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class ImportHelperMultiFileTest {
  @NonNls private static final String BASE_PATH = "/codeInsight/importHelper/";

  private final TestFixture<ProductionDaemonAnalyzerFixture> daemonFixture = productionDaemonAnalyzerFixture(JavaTestUtil.getJavaTestDataPath());

  @Test
  public void testReimportConflictingClasses() {
    ProductionDaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      String path = BASE_PATH + fixture.getTestName(true);
      fixture.configureByFile(path + "/x/Usage.java", path);
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();

      JavaCodeStyleSettings.getInstance(fixture.getProject()).CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = 2;
      WriteCommandAction.runWriteCommandAction(fixture.getProject(),
                                               () -> JavaCodeStyleManager.getInstance(fixture.getProject()).optimizeImports(fixture.getFile()));

      @NonNls String fullPath = fixture.getTestDataPath() + path + "/x/Usage_afterOptimize.txt";
      final VirtualFile vFile = StandardFileSystems.local().findFileByPath(fullPath.replace(File.separatorChar, '/'));
      String text = LoadTextUtil.loadText(vFile).toString();
      assertEquals(text, ReadAction.computeBlocking(() -> fixture.getFile().getText()));
    });
  }

  @Test
  public void testConflictBetweenRegularAndStaticClassesInImportList() {
    ProductionDaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.runTest(() -> {
      String path = BASE_PATH + fixture.getTestName(true);
      fixture.configureByFile(path + "/foo/A.java", path);
      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();

      JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(fixture.getProject());
      javaSettings.LAYOUT_STATIC_IMPORTS_SEPARATELY = true;
      javaSettings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = 3;
      javaSettings.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND = 3;

      WriteCommandAction.runWriteCommandAction(fixture.getProject(),
                                               () -> JavaCodeStyleManager.getInstance(fixture.getProject()).optimizeImports(fixture.getFile()));

      assertThat(fixture.waitHighlighting(HighlightSeverity.ERROR)).isEmpty();
    });
  }
}
