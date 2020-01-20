// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.fileEditor.impl;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class InconsistentLineSeparatorsTest extends LightDaemonAnalyzerTestCase {
  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new InconsistentLineSeparatorsInspection()};
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      UIUtil.dispatchAllInvocationEvents(); // invokeLater() in EncodingProjectManagerImpl.reloadAllFilesUnder()
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testMixedLineSeparators() throws IOException {
    String rawText = "abc\r\ndef\nghi";
    configureFromLiteralText("<warning descr=\"Line separators in the current file (\\n, \\r\\n) differ from the project defaults (\\n)\">" + rawText +"</warning>");

    CodeStyle.getSettings(getProject()).LINE_SEPARATOR = "\n";
    doTestConfiguredFile(true, false,null);
  }

  public void testLineSeparatorsDifferentFromProjectDefault() throws IOException {
    String rawText = "abc\r\ndef\r\nghi";
    configureFromLiteralText("<warning descr=\"Line separators in the current file (\\r\\n) differ from the project defaults (\\n)\">" + rawText +"</warning>");

    CodeStyle.getSettings(getProject()).LINE_SEPARATOR = "\n";
    doTestConfiguredFile(true, false,null);
  }

  public void testMustWarnAboutMixedSeparatorsEvenWhenTheDetectedLineSeparatorIsProject() throws IOException {
    String rawText = "abc\rdef\ndef\ndef\nghi";
    configureFromLiteralText("<warning descr=\"Line separators in the current file (\\n, \\r) differ from the project defaults (\\n)\">" + rawText +"</warning>");
    assertEquals("\n", getVFile().getDetectedLineSeparator());

    CodeStyle.getSettings(getProject()).LINE_SEPARATOR = "\n";
    doTestConfiguredFile(true, false,null);
  }

  public void testMustNotWarnAboutOneLiners() throws IOException {
    String rawText = "abc";
    configureFromLiteralText(rawText);
    CodeStyle.getSettings(getProject()).LINE_SEPARATOR = "\n";
    doTestConfiguredFile(true, false,null);
  }


  private void configureFromLiteralText(@NotNull String rawText) throws IOException {
    WriteAction.run(() -> {
      Document document = configureFromFileText("text.txt", StringUtil.convertLineSeparators(rawText));
      PsiFile file = PsiDocumentManager.getInstance(getProject()).getPsiFile(document);
      VirtualFile virtualFile = file.getVirtualFile();
      virtualFile.setBinaryContent(rawText.getBytes(StandardCharsets.UTF_8));
    });
  }
}
