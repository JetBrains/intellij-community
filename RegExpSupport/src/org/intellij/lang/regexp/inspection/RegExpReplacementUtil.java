/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.intellij.lang.regexp.inspection;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlElement;
import com.intellij.xml.util.XmlStringUtil;
import org.intellij.lang.regexp.psi.RegExpElement;
import org.intellij.lang.regexp.psi.impl.RegExpElementImpl;

/**
 * @author Bas Leijdekkers
 */
public class RegExpReplacementUtil {

  private RegExpReplacementUtil() {}

  public static String escapeForContext(String text, RegExpElement element) {
    final PsiElement context = element.getContainingFile().getContext();
    if (RegExpElementImpl.isLiteralExpression(context)) {
      return StringUtil.escapeStringCharacters(text);
    }
    else if (context instanceof XmlElement) {
      return XmlStringUtil.escapeString(text);
    }
    else {
      return text;
    }
  }
}
