/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LossyEncodingInspection;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;
import java.util.Collection;
import java.util.List;

public class LossyEncodingTest extends DaemonAnalyzerTestCase {
  @NonNls private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/lossyEncoding";

  @NotNull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new LossyEncodingInspection()};
  }

  @Override
  protected void tearDown() throws Exception {
    UIUtil.dispatchAllInvocationEvents(); // invokeLater() in EncodingProjectManagerImpl.reloadAllFilesUnder()
    super.tearDown();
  }

  public void testText() throws Exception {
    doTest("Text.txt");
    Charset ascii = CharsetToolkit.forName("US-ASCII");
    VirtualFile myVFile = myFile.getVirtualFile();
    FileDocumentManager.getInstance().saveAllDocuments();
    EncodingManager.getInstance().setEncoding(myVFile, ascii);
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
    configureByText(StdFileTypes.PROPERTIES, "a=<caret>v");
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
    EncodingManager.getInstance().setNative2AsciiForPropertiesFiles(null, true);
    UIUtil.dispatchAllInvocationEvents();
    configureByFile(BASE_PATH + "/" + "NativeEncoding.properties");

    doDoTest(true, false);
  }

  public void testDetectWrongEncoding() {
    VirtualFile virtualFile = getVirtualFile(BASE_PATH + "/Win1251.txt");
    virtualFile.setCharset(CharsetToolkit.UTF8_CHARSET);
    configureByExistingFile(virtualFile);
    Document document = ObjectUtils.notNull(FileDocumentManager.getInstance().getDocument(virtualFile));

    assertFalse(FileDocumentManager.getInstance().isDocumentUnsaved(document));
    assertEquals(CharsetToolkit.UTF8_CHARSET, virtualFile.getCharset());

    doHighlighting();
    List<HighlightInfo> infos = DaemonCodeAnalyzerEx.getInstanceEx(getProject()).getFileLevelHighlights(getProject(), getFile());
    HighlightInfo info = assertOneElement(infos);
    assertEquals("File was loaded in the wrong encoding: 'UTF-8'", info.getDescription());
  }

  public void testSurrogateUTF8() {
    VirtualFile virtualFile = getVirtualFile(BASE_PATH + "/" + "surrogate.txt");
    virtualFile.setCharset(CharsetToolkit.UTF8_CHARSET);
    configureByExistingFile(virtualFile);
    final Document document = ObjectUtils.notNull(FileDocumentManager.getInstance().getDocument(virtualFile));

    assertFalse(FileDocumentManager.getInstance().isDocumentUnsaved(document));
    assertEquals(CharsetToolkit.UTF8_CHARSET, virtualFile.getCharset());

    assertEmpty(doHighlighting());
  }

  public void testInconsistentLineSeparators() {
    VirtualFile virtualFile = getVirtualFile(BASE_PATH + "/" + getTestName(false) + ".txt");
    configureByExistingFile(virtualFile);
    FileDocumentManager.getInstance().saveAllDocuments();
    final Document document = ObjectUtils.notNull(FileDocumentManager.getInstance().getDocument(virtualFile));
    assertFalse(FileDocumentManager.getInstance().isDocumentUnsaved(document));
    doHighlighting();
    List<HighlightInfo> infos = DaemonCodeAnalyzerEx.getInstanceEx(getProject()).getFileLevelHighlights(getProject(), getFile());
    assertEmpty(infos);
  }
}
