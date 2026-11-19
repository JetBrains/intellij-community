// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.internationalization;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class UnnecessaryUnicodeEscapeInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new UnnecessaryUnicodeEscapeInspection());

  @Test
  public void testUnnecessaryUnicodeEscape() {
    LightJavaCodeInsightTestFixture fixture = inspectionFixture.get().getFixture();
    fixture.configureByFile(inspectionFixture.get().getTestName(false) + ".java");
    final VirtualFile vFile = fixture.getFile().getVirtualFile();
    Charset ascii = StandardCharsets.US_ASCII;
    EncodingProjectManager encodingManager = EncodingProjectManager.getInstance(fixture.getProject());
    runInEdtAndWait(() -> {
      encodingManager.setEncoding(vFile, ascii);
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue(); // reload in invokeLater
    });
    try {
      assertEquals(ascii, vFile.getCharset());
      fixture.testHighlighting(true, false, false);
    }
    finally {
      runInEdtAndWait(() -> {
        encodingManager.setEncoding(vFile, null);
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      });
    }
  }

  @Test
  public void testUnnecessaryUnicodeEscapeUTF8() {
    inspectionFixture.get().doTest();
  }
}
