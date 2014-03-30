/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Apr 11, 2002
 * Time: 7:51:16 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.daemon;

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LossyEncodingInspection;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
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

  public void testText() throws Exception {
    doTest("Text.txt");
    Charset ascii = CharsetToolkit.forName("US-ASCII");
    VirtualFile myVFile = myFile.getVirtualFile();
    EncodingManager.getInstance().setEncoding(myVFile, ascii);
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

  public void testNativeConversion() throws Exception {
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
  }

  public void testNativeEncoding() throws Exception {
    EncodingManager.getInstance().setNative2AsciiForPropertiesFiles(null, true);
    configureByFile(BASE_PATH + "/" + "NativeEncoding.properties");

    doDoTest(true, false);
  }

  public void testDetectWrongEncoding0() throws Exception {
    String threeNotoriousRussianLetters = "\u0416\u041e\u041f";
    configureByText(FileTypes.PLAIN_TEXT, threeNotoriousRussianLetters);
    VirtualFile virtualFile = getFile().getVirtualFile();
    final Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
    WriteCommandAction.runWriteCommandAction(getProject(), new Runnable() {
      @Override
      public void run() {
        document.insertString(0, " ");
        document.deleteString(0, 1);
      }
    });


    assertTrue(FileDocumentManager.getInstance().isDocumentUnsaved(document));
    assertEquals(CharsetToolkit.UTF8_CHARSET, virtualFile.getCharset());
    Charset WINDOWS_1251 = Charset.forName("windows-1251");
    virtualFile.setCharset(WINDOWS_1251);
    FileDocumentManager.getInstance().saveAllDocuments();  // save in wrong encoding
    assertEquals(WINDOWS_1251, virtualFile.getCharset());
    assertEquals(threeNotoriousRussianLetters, new String(virtualFile.contentsToByteArray(), WINDOWS_1251));
    virtualFile.setCharset(CharsetToolkit.UTF8_CHARSET);

    doHighlighting();
    List<HighlightInfo> infos = DaemonCodeAnalyzerEx.getInstanceEx(getProject()).getFileLevelHighlights(getProject(), getFile());
    HighlightInfo info = assertOneElement(infos);
    assertEquals("File was loaded in the wrong encoding: 'UTF-8'", info.getDescription());
  }

  public void testDetectWrongEncoding() throws Exception {
    VirtualFile virtualFile = getVirtualFile(BASE_PATH + "/" + "Win1251.txt");
    virtualFile.setCharset(CharsetToolkit.UTF8_CHARSET);
    configureByExistingFile(virtualFile);
    final Document document = FileDocumentManager.getInstance().getDocument(virtualFile);

    assertFalse(FileDocumentManager.getInstance().isDocumentUnsaved(document));
    assertEquals(CharsetToolkit.UTF8_CHARSET, virtualFile.getCharset());

    doHighlighting();
    List<HighlightInfo> infos = DaemonCodeAnalyzerEx.getInstanceEx(getProject()).getFileLevelHighlights(getProject(), getFile());
    HighlightInfo info = assertOneElement(infos);
    assertEquals("File was loaded in the wrong encoding: 'UTF-8'", info.getDescription());
  }

  public void testInconsistentLineSeparators() throws Exception {
    VirtualFile virtualFile = getVirtualFile(BASE_PATH + "/" + getTestName(false) + ".txt");
    configureByExistingFile(virtualFile);
    FileDocumentManager.getInstance().saveAllDocuments();
    final Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
    assertFalse(FileDocumentManager.getInstance().isDocumentUnsaved(document));
    doHighlighting();
    List<HighlightInfo> infos = DaemonCodeAnalyzerEx.getInstanceEx(getProject()).getFileLevelHighlights(getProject(), getFile());
    assertEmpty(infos);
  }
}
