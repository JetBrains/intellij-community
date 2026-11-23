// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.java.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.DaemonAnalyzerFixture;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ReassignedToPlainTextInspection;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileTypes.ExactFileNameMatcher;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collection;

import static com.intellij.codeInsight.daemon.DaemonAnalyzerFixture.daemonAnalyzerFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class ReassignedToPlainTextTest {
  private final TestFixture<DaemonAnalyzerFixture> daemonFixture = daemonAnalyzerFixture(
    JavaTestUtil.getJavaTestDataPath(), () -> new LocalInspectionTool[]{new ReassignedToPlainTextInspection()});

  @Test
  public void testText() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    String name = "xx.xxxx";
    assertEquals(FileTypes.UNKNOWN, fileTypeManager.getFileTypeByFileName(name));
    WriteAction.runAndWait(() -> fileTypeManager.associate(PlainTextFileType.INSTANCE, new ExactFileNameMatcher(name)));
    assertEquals(PlainTextFileType.INSTANCE, fileTypeManager.getFileTypeByFileName(name));

    VirtualFile file = createTempFile(fixture, name, "xxx");
    fixture.configureByExistingFile(file);
    assertEquals(PlainTextFileType.INSTANCE, file.getFileType());

    Collection<HighlightInfo> infos = fixture.doHighlighting(HighlightSeverity.WARNING);
    assertThat(infos).hasSize(1);
    HighlightInfo info = infos.iterator().next();
    assertEquals("This file was explicitly re-assigned to plain text", info.getDescription());

    fixture.findAndInvokeIntentionAction(infos, InspectionsBundle.message("reassigned.to.plain.text.inspection.fix.remove.name"));
    assertEquals(FileTypes.UNKNOWN, fileTypeManager.getFileTypeByFileName(name));
    assertEquals(PlainTextFileType.INSTANCE, file.getFileType());
  }

  @Test
  public void testMustNotReportTxt() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    String name = "xx." + PlainTextFileType.INSTANCE.getDefaultExtension();
    assertEquals(PlainTextFileType.INSTANCE, fileTypeManager.getFileTypeByFileName(name));

    VirtualFile file = createTempFile(fixture, name, "xxx");
    fixture.configureByExistingFile(file);
    assertEquals(PlainTextFileType.INSTANCE, file.getFileType());

    Collection<HighlightInfo> infos = fixture.doHighlighting(HighlightSeverity.WARNING);
    assertThat(infos).isEmpty();
  }

  /**
   * Writes {@code text} to the file {@code name} in a new temporary directory outside the project content.
   */
  private static VirtualFile createTempFile(DaemonAnalyzerFixture fixture, String name, String text) throws IOException {
    VirtualFile dir = fixture.createTempVirtualDir();
    return WriteAction.computeAndWait(() -> {
      VirtualFile file = dir.createChildData(ReassignedToPlainTextTest.class, name);
      VfsUtil.saveText(file, text);
      return file;
    });
  }
}
