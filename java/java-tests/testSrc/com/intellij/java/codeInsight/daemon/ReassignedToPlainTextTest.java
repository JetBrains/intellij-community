// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
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
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class ReassignedToPlainTextTest extends DaemonAnalyzerTestCase {
  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new ReassignedToPlainTextInspection()};
  }

  public void testText() throws Exception {
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    String name = "xx.xxxx";
    assertEquals(FileTypes.UNKNOWN, fileTypeManager.getFileTypeByFileName(name));
    WriteAction.run(() -> fileTypeManager.associate(PlainTextFileType.INSTANCE, new ExactFileNameMatcher(name)));
    assertEquals(PlainTextFileType.INSTANCE, fileTypeManager.getFileTypeByFileName(name));

    VirtualFile file = getVirtualFile(createTempFile(name, "xxx"));
    configureByExistingFile(file);
    assertEquals(PlainTextFileType.INSTANCE, file.getFileType());

    Collection<HighlightInfo> infos = doHighlighting(HighlightSeverity.WARNING);
    HighlightInfo info = assertOneElement(infos);
    assertEquals("This file was explicitly re-assigned to plain text", info.getDescription());

    findAndInvokeIntentionAction(infos, InspectionsBundle.message("reassigned.to.plain.text.inspection.fix.remove.name"), getEditor(), getFile());
    assertEquals(FileTypes.UNKNOWN, fileTypeManager.getFileTypeByFileName(name));
    assertEquals(PlainTextFileType.INSTANCE, file.getFileType());
  }

  public void testMustNotReportTxt() throws Exception {
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    String name = "xx." + PlainTextFileType.INSTANCE.getDefaultExtension();
    assertEquals(PlainTextFileType.INSTANCE, fileTypeManager.getFileTypeByFileName(name));

    VirtualFile file = getVirtualFile(createTempFile(name, "xxx"));
    configureByExistingFile(file);
    assertEquals(PlainTextFileType.INSTANCE, file.getFileType());

    Collection<HighlightInfo> infos = doHighlighting(HighlightSeverity.WARNING);
    assertEmpty(infos);
  }
}
