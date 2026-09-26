// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.regexp.ecmascript;

import org.intellij.lang.regexp.RegExpSyntaxHighlighterFactory;

/**
 * @author Bas Leijdekkers
 */
public class EcmaScriptRegExpSyntaxHighlighterFactory extends RegExpSyntaxHighlighterFactory {
  public EcmaScriptRegExpSyntaxHighlighterFactory() {
    super(EcmaScriptRegexpLanguage.INSTANCE);
  }
}
