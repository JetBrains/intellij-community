/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Apr 11, 2002
 * Time: 7:51:16 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.daemon;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LossyEncodingInspection;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;

import java.nio.charset.Charset;
import java.util.Collection;

public class LossyEncodingTest extends LightDaemonAnalyzerTestCase {
  @NonNls private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/lossyEncoding";

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
    assertEquals(1, infos.size());
    HighlightInfo info = infos.iterator().next();
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

  public void testNativeEncoding() throws Exception {
    EncodingManager.getInstance().setNative2AsciiForPropertiesFiles(null, true);
    configureByFile(BASE_PATH + "/" + "NativeEncoding.properties");

    doTestConfiguredFile(true, false);
  }

  private void doTest(@NonNls String filePath) throws Exception {
    doTest(BASE_PATH + "/" + filePath, true, false);
  }
}