// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.regexp.ecmascript;

import com.intellij.lang.Language;
import org.intellij.lang.regexp.RegExpLanguage;

/**
 * @author Konstantin.Ulitin
 */
public class EcmaScriptRegexpLanguage extends Language {

  public static final EcmaScriptRegexpLanguage INSTANCE = new EcmaScriptRegexpLanguage();
  public static final String ID = "JSRegexp";

  public EcmaScriptRegexpLanguage() {
    super(RegExpLanguage.INSTANCE, ID);
  }
}
