/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ide.fileTemplates.impl;

import com.intellij.lexer.Lexer;
import com.intellij.testFramework.LexerTestCase;

/**
 * @author peter
 */
public class FileTemplateLexerTest extends LexerTestCase {

  public void testEscapes() {
    doTest("\\#include foo $bar", "ESCAPE ('\\#')\n" +
                                  "TEXT ('include foo ')\n" +
                                  "MACRO ('$bar')");
  }

  public void testLiveTemplates() {
    doTest("#[[$FOO$]]#", "ESCAPE ('#[[')\n" +
                          "MACRO ('$FOO$')\n" +
                          "ESCAPE (']]#')");
  }

  @Override
  protected Lexer createLexer() {
    return FileTemplateConfigurable.createDefaultLexer();
  }

  @Override
  protected String getDirPath() {
    return null;
  }
}