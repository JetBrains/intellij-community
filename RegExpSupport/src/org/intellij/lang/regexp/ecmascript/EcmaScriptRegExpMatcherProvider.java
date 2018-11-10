// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.regexp.ecmascript;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteralValue;
import org.intellij.lang.annotations.Language;
import org.intellij.lang.regexp.RegExpMatchResult;
import org.intellij.lang.regexp.RegExpMatcherProvider;
import org.jetbrains.annotations.NotNull;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

/**
 * @author Bas Leijdekkers
 */
public class EcmaScriptRegExpMatcherProvider implements RegExpMatcherProvider {

  @NotNull
  @Override
  public RegExpMatchResult matches(String regExp, PsiFile regExpFile, PsiElement elementInHost, String sampleText, long timeoutMillis) {
    String modifiers = "";
    if (elementInHost instanceof PsiLiteralValue) {
      final String text = elementInHost.getText();
      final int slash = StringUtil.isQuotedString(text) ? -1 : text.lastIndexOf('/');
      if (slash > 0) {
        if (text.indexOf('i', slash) > 0) {
          modifiers += 'i';
        }
        if (text.indexOf('m', slash) > 0) {
          modifiers += 'm';
        }
        if (text.indexOf('u', slash) > 0) {
          modifiers += 'u';
        }
      }
    }
    final ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
    try {
      @Language("Nashorn JS")
      final String script =
        "var a = \"" + StringUtil.escapeStringCharacters(sampleText) + "\".match(/" + StringUtil.escapeChar(regExp, '/') + "/" + modifiers + ");\n" +
        "a !== null";
      return (engine.eval(script) == Boolean.TRUE) ? RegExpMatchResult.MATCHES : RegExpMatchResult.NO_MATCH;
    }
    catch (ScriptException e) {
      return RegExpMatchResult.BAD_REGEXP;
    }
  }
}
