// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.regexp.ecmascript;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteralValue;
import com.intellij.util.SmartList;
import org.intellij.lang.annotations.Language;
import org.intellij.lang.regexp.RegExpMatch;
import org.intellij.lang.regexp.RegExpMatchResult;
import org.intellij.lang.regexp.RegExpMatcherProvider;
import org.intellij.lang.regexp.intention.CheckRegExpForm;
import org.jetbrains.annotations.NotNull;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.SimpleBindings;
import java.util.List;

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
        "var regexp = RegExp(\"" + StringUtil.escapeStringCharacters(regExp) + "\",'g" + modifiers + "');\n" +
        "var str = \"" + StringUtil.escapeStringCharacters(sampleText) + "\";\n" +
        "var match;\n" +
        "\n" +
        "var RegExpMatch = Java.type(\"org.intellij.lang.regexp.RegExpMatch\");\n" +
        "var prev = null;\n" +
        "while ((match = regexp.exec(str)) !== null) {\n" +
        "  var r = new RegExpMatch(match.index, regexp.lastIndex);\n" +
        "  if (r.equals(prev)) break;\n" +
        "  prev = r;\n" +
        "  result.add(r);\n" +
        "}\n" +
        "result";
      final SimpleBindings bindings = new SimpleBindings();
      bindings.put("result", new SmartList<>());
      @SuppressWarnings("unchecked") final List<RegExpMatch> result = (List<RegExpMatch>)engine.eval(script, bindings);
      CheckRegExpForm.setMatches(regExpFile, result);
      return result.isEmpty() ? RegExpMatchResult.NO_MATCH : RegExpMatchResult.FOUND;
    }
    catch (Exception e) {
      return RegExpMatchResult.BAD_REGEXP;
    }
  }
}
