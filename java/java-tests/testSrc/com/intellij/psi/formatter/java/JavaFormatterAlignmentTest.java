/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.psi.formatter.java;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.codeStyle.CodeStyleSettings;

/**
 * Is intended to hold specific java formatting tests for alignment settings (
 * <code>Project Settings - Code Style - Alignment and Braces</code>).
 *
 * @author Denis Zhdanov
 * @since Apr 27, 2010 6:42:00 PM
 */
public class JavaFormatterAlignmentTest extends AbstractJavaFormatterTest {

  public void testChainedMethodsAlignment() throws Exception {
    // Inspired by IDEA-30369
    getSettings().ALIGN_MULTILINE_CHAINED_METHODS = true;
    getSettings().METHOD_CALL_CHAIN_WRAP = CodeStyleSettings.WRAP_AS_NEEDED;
    getSettings().getIndentOptions(StdFileTypes.JAVA).CONTINUATION_INDENT_SIZE = 8;
    doTest();
  }

  public void testMultipleMethodAnnotationsCommentedInTheMiddle() throws Exception {
    getSettings().BLANK_LINES_AFTER_CLASS_HEADER = 1;
    getSettings().getIndentOptions(StdFileTypes.JAVA).INDENT_SIZE = 4;

    // Inspired by IDEA-53942
    doTextTest(
      "public class Test {\n" +
      "          @Override\n" +
      "//       @XmlElement(name = \"Document\", required = true, type = DocumentType.class)\n" +
      "       @XmlTransient\n" +
      "  void foo() {\n" +
      "}\n" +
      "}",

      "public class Test {\n" +
      "\n" +
      "    @Override\n" +
      "//       @XmlElement(name = \"Document\", required = true, type = DocumentType.class)\n" +
      "    @XmlTransient\n" +
      "    void foo() {\n" +
      "    }\n" +
      "}"
    );
  }
}
