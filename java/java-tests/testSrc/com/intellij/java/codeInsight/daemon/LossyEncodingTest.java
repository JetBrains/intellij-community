// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LossyEncodingInspection;
import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class LossyEncodingTest extends DaemonAnalyzerTestCase {
  @NonNls private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/lossyEncoding";

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new LossyEncodingInspection()};
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

  public void testText() throws Exception {
    doTest("Text.txt");
    Charset ascii = StandardCharsets.US_ASCII;
    VirtualFile myVFile = myFile.getVirtualFile();
    FileDocumentManager.getInstance().saveAllDocuments();
    EncodingProjectManager.getInstance(getProject()).setEncoding(myVFile, ascii);
    UIUtil.dispatchAllInvocationEvents(); // wait for reload requests to bubble up
    assertEquals(ascii, myVFile.getCharset());
    int start = myEditor.getCaretModel().getOffset();
    type((char)0x445);
    type((char)0x438);
    int end = myEditor.getCaretModel().getOffset();

    Collection<HighlightInfo> infos = doHighlighting();
    HighlightInfo info = assertOneElement(infos);
    assertEquals("Unsupported characters for the charset 'US-ASCII'", info.getDescription());
    assertEquals(start, info.startOffset);
    assertEquals(end, info.endOffset);

    backspace();
    backspace();

    doDoTest(true, false);
  }

  public void testNativeConversion() {
    configureByText(PropertiesFileType.INSTANCE, "a=<caret>v");
    EncodingProjectManager.getInstance(getProject()).setNative2AsciiForPropertiesFiles(null, true);
    UIUtil.dispatchAllInvocationEvents();  //reload files

    type('\\');
    type('\\');

    Collection<HighlightInfo> infos = doHighlighting();
    assertEquals(0, infos.size());
  }

  public void testTyping() throws Exception {
    doTest("Simple.xml");
    type("US-ASCII");

    Collection<HighlightInfo> infos = doHighlighting();
    assertEquals(1, infos.size());
    boolean found = false;

    for(HighlightInfo info:infos) {
      if (info.getDescription().equals("Unsupported characters for the charset 'US-ASCII'")) {
        found = true;
        break;
      }
    }
    assertTrue(found);
  }

  public void testMultipleRanges() throws Exception {
    configureByFile(BASE_PATH + "/" + "MultipleRanges.xml");
    type("US-ASCII");

    doDoTest(true, false);
  }

  private void doTest(@NonNls String filePath) throws Exception {
    doTest(BASE_PATH + "/" + filePath, true, false);
    UIUtil.dispatchAllInvocationEvents();
  }

  public void testNativeEncoding() throws Exception {
    EncodingProjectManager.getInstance(getProject()).setNative2AsciiForPropertiesFiles(null, true);
    UIUtil.dispatchAllInvocationEvents();
    configureByFile(BASE_PATH + "/" + "NativeEncoding.properties");

    doDoTest(true, false);
  }

  public void testDetectWrongEncoding() {
    VirtualFile virtualFile = getVirtualFile(BASE_PATH + "/Win1251.txt");
    virtualFile.setCharset(StandardCharsets.UTF_8);
    configureByExistingFile(virtualFile);
    Document document = Objects.requireNonNull(FileDocumentManager.getInstance().getDocument(virtualFile));

    assertFalse(FileDocumentManager.getInstance().isDocumentUnsaved(document));
    assertEquals(StandardCharsets.UTF_8, virtualFile.getCharset());

    doHighlighting();
    List<HighlightInfo> infos = DaemonCodeAnalyzerEx.getInstanceEx(getProject()).getFileLevelHighlights(getProject(), getFile());
    HighlightInfo info = assertOneElement(infos);
    assertEquals("File was loaded in the wrong encoding: 'UTF-8'", info.getDescription());
  }

  public void testSurrogateUTF8() {
    VirtualFile virtualFile = getVirtualFile(BASE_PATH + "/" + "surrogate.txt");
    virtualFile.setCharset(StandardCharsets.UTF_8);
    configureByExistingFile(virtualFile);
    final Document document = Objects.requireNonNull(FileDocumentManager.getInstance().getDocument(virtualFile));

    assertFalse(FileDocumentManager.getInstance().isDocumentUnsaved(document));
    assertEquals(StandardCharsets.UTF_8, virtualFile.getCharset());

    assertEmpty(doHighlighting());
  }

  public void testInconsistentLineSeparators() {
    VirtualFile virtualFile = getVirtualFile(BASE_PATH + "/" + getTestName(false) + ".txt");
    configureByExistingFile(virtualFile);
    FileDocumentManager.getInstance().saveAllDocuments();
    final Document document = Objects.requireNonNull(FileDocumentManager.getInstance().getDocument(virtualFile));
    assertFalse(FileDocumentManager.getInstance().isDocumentUnsaved(document));
    doHighlighting();
    List<HighlightInfo> infos = DaemonCodeAnalyzerEx.getInstanceEx(getProject()).getFileLevelHighlights(getProject(), getFile());
    assertEmpty(infos);
  }
}
