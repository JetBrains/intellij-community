// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.regexp.ecmascript;

import com.intellij.lang.Language;
import org.intellij.lang.regexp.RegExpLanguage;

/**
 * @author Bas Leijdekkers
 */
public class EcmaScriptUnicodeRegexpLanguage extends Language {

  public static final EcmaScriptUnicodeRegexpLanguage INSTANCE = new EcmaScriptUnicodeRegexpLanguage();
  public static final String ID = "JSUnicodeRegexp";

  public EcmaScriptUnicodeRegexpLanguage() {
    super(RegExpLanguage.INSTANCE, ID);
  }
}
