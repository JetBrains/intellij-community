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

/**
 * Is intended to hold specific java formatting tests for 'braces placement' settings (
 * <code>Project Settings - Code Style - Alignment and Braces</code>).
 *
 * @author Denis Zhdanov
 * @since Apr 27, 2010 6:39:24 PM
 */
public class JavaFormattingBracesTest extends AbstractJavaFormattingTest {

  public void testFlyingGeeseBraces() {
    // Inspired by IDEA-52305

    getSettings().USE_FLYING_GEESE_BRACES = true;
    getSettings().FLYING_GEESE_BRACES_GAP = 1;
    getSettings().BLANK_LINES_AROUND_METHOD = 0; // controls number of blank lines between instance initialization block and field. Very strange
    getSettings().KEEP_SIMPLE_METHODS_IN_ONE_LINE = true; // allow methods like 'void foo() {}'
    getSettings().getIndentOptions(StdFileTypes.JAVA).INDENT_SIZE = 2;

    // Flying gees style for class initialization and instance initialization block when there are no fields/methods.
    doTextTest(
      "class FormattingTest {\n" +
      "  {\n" +
      "  } \n" +
      "}",
      "class FormattingTest { {\n" +
      "} }"
    );

    // Flying gees style for class initialization and instance initialization block when there are fields after the block.
    doTextTest(
      "class FormattingTest {\n" +
      "  {\n" +
      "  } \n" +
      "  int i;\n" +
      "}",
      "class FormattingTest {\n" +
      "  {\n" +
      "  } \n" +
      "  int i;\n" +
      "}"
    );

    // Flying gees style for class initialization and instance initialization block when there are fields before the block.
    doTextTest(
      "class FormattingTest {\n" +
      "  int i;\n" +
      "  {\n" +
      "  } \n" +
      "}",
      "class FormattingTest {\n" +
      "  int i;\n" +
      "  {\n" +
      "  } \n" +
      "}"
    );

    // Flying gees style for class initialization and instance initialization block when there are methods after the block.
    doTextTest(
      "class FormattingTest {\n" +
      "  {\n" +
      "  } \n" +
      "  void foo() {}\n" +
      "}",
      "class FormattingTest {\n" +
      "  {\n" +
      "  } \n" +
      "  void foo() {}\n" +
      "}"
    );

    // Flying gees style for class initialization and instance initialization block when there are methods before the block.
    doTextTest(
      "class FormattingTest {\n" +
      "  void foo() {}\n" +
      "  {\n" +
      "  } \n" +
      "}",
      "class FormattingTest {\n" +
      "  void foo() {}\n" +
      "  {\n" +
      "  } \n" +
      "}"
    );

    // Flying gees style for class initialization and multiple instance initialization blocks.
    doTextTest(
      "class FormattingTest {\n" +
      "  {\n" +
      "  } \n" +
      "  {\n" +
      "  }\n" +
      "}",
      "class FormattingTest {\n" +
      "  {\n" +
      "  } \n" +
      "  {\n" +
      "  }\n" +
      "}"
    );
  }
}
