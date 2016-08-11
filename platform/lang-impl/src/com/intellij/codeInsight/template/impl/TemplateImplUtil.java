/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.codeInsight.template.impl;

import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.hash.LinkedHashMap;

/**
 * @author Maxim.Mossienko
 */
public class TemplateImplUtil {

  public static LinkedHashMap<String, Variable> parseVariables(CharSequence text) {
    LinkedHashMap<String, Variable> variables = new LinkedHashMap<>();
    TemplateTextLexer lexer = new TemplateTextLexer();
    lexer.start(text);

    while (true) {
      IElementType tokenType = lexer.getTokenType();
      if (tokenType == null) break;
      int start = lexer.getTokenStart();
      int end = lexer.getTokenEnd();
      String token = text.subSequence(start, end).toString();
      if (tokenType == TemplateTokenType.VARIABLE) {
        String name = token.substring(1, token.length() - 1);
        if (!variables.containsKey(name)) {
          variables.put(name, new Variable(name, "", "", true));
        }
      }
      lexer.advance();
    }
    return variables;
  }

  public static boolean isValidVariableName(String varName) {
    return parseVariables("$" + varName + "$").containsKey(varName);
  }
}
