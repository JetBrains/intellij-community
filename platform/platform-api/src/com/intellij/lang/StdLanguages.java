// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang;

import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.StdFileTypes;
import org.jetbrains.annotations.ApiStatus;

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
  @Deprecated @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public static final Language JAVA = StdFileTypes.JAVA.getLanguage();

  /**
   * The definition for the DTD language.
   *
   * @deprecated use {@linkplain com.intellij.lang.dtd.DTDLanguage#INSTANCE} instead.
   */
  @Deprecated @ApiStatus.ScheduledForRemoval(inVersion = "2021.2")
  public static final Language DTD = StdFileTypes.DTD.getLanguage();

  /**
   * The definition for the XML language.
   *
   * @deprecated use {@linkplain com.intellij.lang.xml.XMLLanguage#INSTANCE} instead.
   */
  @Deprecated public static final Language XML = StdFileTypes.XML.getLanguage();

  /**
   * The definition for the HTML language.
   *
   * @deprecated use {@linkplain com.intellij.lang.html.HTMLLanguage#INSTANCE} instead.
   */
  @Deprecated @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public static final Language HTML = StdFileTypes.HTML.getLanguage();

  /**
   * The definition for the Text language
   *
   * @deprecated use {@linkplain com.intellij.openapi.fileTypes.PlainTextLanguage#INSTANCE} instead.
   */
  @Deprecated @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public static final Language TEXT = FileTypes.PLAIN_TEXT.getLanguage();

  private StdLanguages() { }
}
