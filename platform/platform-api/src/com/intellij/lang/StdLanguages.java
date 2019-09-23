/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.lang;

import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.StdFileTypes;

/**
 * Defines the standard languages supported by IDEA.
 *
 * @author max
 */
public class StdLanguages {
  /**
   * The definition for the Java language.
   *
   * @deprecated use {@linkplain com.intellij.lang.java.JavaLanguage#INSTANCE} instead.
   */
  @Deprecated public static final Language JAVA = StdFileTypes.JAVA.getLanguage();

  /**
   * The definition for the DTD language.
   *
   * @deprecated use {@linkplain com.intellij.lang.dtd.DTDLanguage#INSTANCE} instead.
   */
  @Deprecated public static final Language DTD = StdFileTypes.DTD.getLanguage();

  /**
   * The definition for the JSP language.
   *
   * @deprecated use {@linkplain com.intellij.lang.jsp.NewJspLanguage#INSTANCE} instead.
   */
  @Deprecated public static final Language JSP = StdFileTypes.JSP.getLanguage();

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
  @Deprecated public static final Language HTML = StdFileTypes.HTML.getLanguage();

  /**
   * The definition for the XHTML language.
   *
   * @deprecated use {@linkplain com.intellij.lang.xhtml.XHTMLLanguage#INSTANCE} instead.
   */
  @Deprecated public static final Language XHTML = StdFileTypes.XHTML.getLanguage();

  /**
   * The definition for the JSPX language (JSP with XML syntax).
   *
   * @deprecated use {@linkplain com.intellij.lang.jspx.JSPXLanguage#INSTANCE} instead.
   */
  @Deprecated public static final Language JSPX = StdFileTypes.JSPX.getLanguage();

  /**
   * The definition for the Text language
   *
   * @deprecated use {@linkplain com.intellij.openapi.fileTypes.PlainTextLanguage#INSTANCE} instead.
   */
  @Deprecated public static final Language TEXT = FileTypes.PLAIN_TEXT.getLanguage();

  private StdLanguages() { }
}
