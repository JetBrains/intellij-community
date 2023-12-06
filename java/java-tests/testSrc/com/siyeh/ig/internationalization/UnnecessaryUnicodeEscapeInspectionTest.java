// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.internationalization;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.util.ui.UIUtil;
import com.siyeh.ig.LightJavaInspectionTestCase;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * @author Bas Leijdekkers
 */
public class UnnecessaryUnicodeEscapeInspectionTest extends LightJavaInspectionTestCase {

  public void testUnnecessaryUnicodeEscape() {
    myFixture.configureByFile(getTestName(false) + ".java");
    final VirtualFile vFile = myFixture.getFile().getVirtualFile();
    Charset ascii = StandardCharsets.US_ASCII;
    EncodingProjectManager.getInstance(getProject()).setEncoding(vFile, ascii);
    UIUtil.dispatchAllInvocationEvents(); // reload in invokeLater
    assertEquals(ascii, vFile.getCharset());
    myFixture.testHighlighting(true, false, false);
  }

  public void testUnnecessaryUnicodeEscapeUTF8() {
    doTest();
  }

  @Override
  protected LocalInspectionTool getInspection() {
    return new UnnecessaryUnicodeEscapeInspection();
  }
}
