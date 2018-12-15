// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.impl.livePreview;

import junit.framework.TestCase;

public class ReplacementViewTest extends TestCase {

  public void testMultiline() {
    assertEquals("<html>", ReplacementView.multiline(""));

    assertEquals("<html>&lt;script>&amp;#32;", ReplacementView.multiline("<script>&#32;"));

    // Convert tabs to spaces in order to make them visible.
    assertEquals("<html>&nbsp;&nbsp;&nbsp;&nbsp;", ReplacementView.multiline("\t"));

    // Convert line breaks to <br> in order to make them visible.
    assertEquals("<html>a<br>\nb<br>\nc<br>\nd<br>\n<br>\ne", ReplacementView.multiline("a\rb\nc\r\nd\r\r\ne"));
  }
}
