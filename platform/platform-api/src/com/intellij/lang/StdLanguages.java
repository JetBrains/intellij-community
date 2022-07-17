// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang;

import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.StdFileTypes;

/**
 * Defines the standard languages supported by IDEA.
 *
 * @author max
 */
public final class StdLanguages {
  /**
   * The definition for the Java language.
   *
   * @deprecated use {@linkplain com.intellij.lang.java.JavaLanguage#INSTANCE} instead.
   */
  @Deprecated(forRemoval = true)
  public static final Language JAVA = StdFileTypes.JAVA.getLanguage();

  /**
   * The definition for the XML language.
   *
   * @deprecated use {@linkplain com.intellij.lang.xml.XMLLanguage#INSTANCE} instead.
   */
  @Deprecated public static final Language XML = StdFileTypes.XML.getLanguage();

  /**
   * The definition for the Text language
   *
   * @deprecated use {@linkplain com.intellij.openapi.fileTypes.PlainTextLanguage#INSTANCE} instead.
   */
  @Deprecated(forRemoval = true)
  public static final Language TEXT = FileTypes.PLAIN_TEXT.getLanguage();

  private StdLanguages() { }
}
