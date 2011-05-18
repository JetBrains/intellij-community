/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LossyEncodingInspection;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;

import java.nio.charset.Charset;
import java.util.Collection;
import java.util.List;

public class LossyEncodingTest extends LightDaemonAnalyzerTestCase {
  @NonNls private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/lossyEncoding";

  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new LossyEncodingInspection()};
  }

  public void testText() throws Exception {
    doTest("Text.txt");
    EncodingManager.getInstance().setEncoding(myVFile, Charset.forName("US-ASCII"));
    int start = myEditor.getCaretModel().getOffset();
    type((char)0x445);
    type((char)0x438);
    int end = myEditor.getCaretModel().getOffset();

    Collection<HighlightInfo> infos = doHighlighting();
    HighlightInfo info = assertOneElement(infos);
    assertEquals("Unsupported characters for the charset 'US-ASCII'", info.description);
    assertEquals(start, info.startOffset);
    assertEquals(end, info.endOffset);

    backspace();
    backspace();
    doTestConfiguredFile(true, false);
  }
  public void testNativeConversion() throws Exception {
    configureFromFileText("x.properties","a=<caret>v");
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
      if (info.description.equals("Unsupported characters for the charset 'US-ASCII'")) {
        found = true;
        break;
      }                                         
    }
    assertTrue(found);
  }

  public void testMultipleRanges() throws Exception {
    configureByFile(BASE_PATH + "/" + "MultipleRanges.xml");
    type("US-ASCII");

    doTestConfiguredFile(true, false);
  }

  private void doTest(@NonNls String filePath) throws Exception {
    doTest(BASE_PATH + "/" + filePath, true, false);
  }

  public void testNativeEncoding() throws Exception {
    EncodingManager.getInstance().setNative2AsciiForPropertiesFiles(null, true);
    configureByFile(BASE_PATH + "/" + "NativeEncoding.properties");

    doTestConfiguredFile(true, false);
  }

  public static final String THREE_NOTORIOUS_RUSSIAN_LETTERS = "\u0416\u041e\u041f";
  public void testDetectWrongEncoding() throws Exception {
    configureFromFileText("Win1251.txt", THREE_NOTORIOUS_RUSSIAN_LETTERS);
    VirtualFile virtualFile = getFile().getVirtualFile();
    assertEquals(CharsetToolkit.UTF8_CHARSET, virtualFile.getCharset());
    Charset WINDOWS_1251 = Charset.forName("windows-1251");
    virtualFile.setCharset(WINDOWS_1251);
    FileDocumentManager.getInstance().saveAllDocuments();
    assertEquals(WINDOWS_1251, virtualFile.getCharset());
    assertEquals(THREE_NOTORIOUS_RUSSIAN_LETTERS, new String(virtualFile.contentsToByteArray(), WINDOWS_1251));
    virtualFile.setCharset(CharsetToolkit.UTF8_CHARSET);

    doHighlighting();
    List<HighlightInfo> infos = DaemonCodeAnalyzerImpl.getFileLevelHighlights(getProject(), getFile());
    HighlightInfo info = assertOneElement(infos);
    assertEquals("File was loaded in a wrong encoding: 'UTF-8'", info.description);
  }
}
