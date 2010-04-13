/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.lang.java;

import com.intellij.lang.LiteralEscaper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.JavaTokenType;
import com.intellij.openapi.util.text.StringUtil;

/**
 * @author yole
 */
public class JavaLiteralEscaper implements LiteralEscaper {
  public String getEscapedText(final PsiElement context, final String originalText) {
    if (context instanceof PsiJavaToken && ((PsiJavaToken)context).getTokenType() == JavaTokenType.STRING_LITERAL) {
      return StringUtil.escapeStringCharacters(originalText);
    }
    return originalText;
  }

  public String escapeText(String originalText) {
    return StringUtil.escapeStringCharacters(originalText);
  }

  public String unescapeText(String originalText) {
    return StringUtil.unescapeStringCharacters(originalText);
  }
}
