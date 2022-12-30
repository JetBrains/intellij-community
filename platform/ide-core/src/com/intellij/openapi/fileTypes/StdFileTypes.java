// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes;

import org.jetbrains.annotations.NotNull;

/**
 * @deprecated use file type definitions from the corresponding plugins instead.
 */
@SuppressWarnings("StaticNonFinalField")
@Deprecated
public final class StdFileTypes extends FileTypes {
  private StdFileTypes() { }

  /**
   * @deprecated use {@link com.intellij.ide.highlighter.JavaFileType#INSTANCE} instead.
   */
  @Deprecated
  public static volatile LanguageFileType JAVA = (LanguageFileType)FileTypeManager.getInstance().getStdFileType("JAVA");

  /**
   * @deprecated use {@link com.intellij.ide.highlighter.JavaClassFileType#INSTANCE} instead.
   */
  @Deprecated
  public static volatile FileType CLASS = FileTypeManager.getInstance().getStdFileType("CLASS");

  /**
   * @deprecated use {@link com.intellij.jsp.highlighter.NewJspFileType#INSTANCE} instead.
   */
  @Deprecated(forRemoval = true)
  public static volatile LanguageFileType JSP = (LanguageFileType)FileTypeManager.getInstance().getStdFileType("JSP");

  /**
   * @deprecated use {@link com.intellij.jsp.highlighter.JspxFileType#INSTANCE} instead.
   */
  @Deprecated(forRemoval = true)
  public static volatile LanguageFileType JSPX = (LanguageFileType)FileTypeManager.getInstance().getStdFileType("JSPX");

  /**
   * @deprecated use {@link com.intellij.ide.highlighter.XmlFileType#INSTANCE} instead.
   */
  @Deprecated
  public static volatile LanguageFileType XML = (LanguageFileType)FileTypeManager.getInstance().getStdFileType("XML");

  /**
   * @deprecated use {@link com.intellij.ide.highlighter.DTDFileType#INSTANCE} instead.
   */
  @Deprecated(forRemoval = true)
  public static volatile LanguageFileType DTD = (LanguageFileType)FileTypeManager.getInstance().getStdFileType("DTD");

  /**
   * @deprecated use {@link com.intellij.ide.highlighter.HtmlFileType#INSTANCE} instead.
   */
  @Deprecated
  public static volatile LanguageFileType HTML = (LanguageFileType)FileTypeManager.getInstance().getStdFileType("HTML");

  /**
   * @deprecated use {@link com.intellij.ide.highlighter.XHtmlFileType#INSTANCE} instead.
   */
  @Deprecated(forRemoval = true)
  public static volatile LanguageFileType XHTML = (LanguageFileType)FileTypeManager.getInstance().getStdFileType("XHTML");

  /**
   * @deprecated Instead of using this field, add possibly optional dependency on JavaScript plugin and
   * use {@link com.intellij.lang.javascript.JavaScriptFileType#INSTANCE}.
   */
  @Deprecated
  public static volatile LanguageFileType JS = (LanguageFileType)FileTypeManager.getInstance().getStdFileType("JavaScript");

  /**
   * @deprecated use {@link com.intellij.lang.properties.PropertiesFileType#INSTANCE} instead.
   */
  @Deprecated(forRemoval = true)
  public static volatile LanguageFileType PROPERTIES = (LanguageFileType)FileTypeManager.getInstance().getStdFileType("Properties");

  /**
   * @deprecated use {@link com.intellij.uiDesigner.GuiFormFileType#INSTANCE} instead.
   */
  @Deprecated(forRemoval = true)
  public static volatile FileType GUI_DESIGNER_FORM = FileTypeManager.getInstance().getStdFileType("GUI_DESIGNER_FORM");


  public static class StdFileTypesUpdater implements FileTypeListener {
    @Override
    public void fileTypesChanged(@NotNull FileTypeEvent event) {
      FileType addedFileType = event.getAddedFileType();
      if (addedFileType != null) {
        String name = addedFileType.getName();
        switch (name) {
          case "JAVA" -> JAVA = (LanguageFileType)addedFileType;
          case "CLASS" -> CLASS = addedFileType;
          case "JSP" -> JSP = (LanguageFileType)addedFileType;
          case "JSPX" -> JSPX = (LanguageFileType)addedFileType;
          case "XML" -> XML = (LanguageFileType)addedFileType;
          case "DTD" -> DTD = (LanguageFileType)addedFileType;
          case "HTML" -> HTML = (LanguageFileType)addedFileType;
          case "XHTML" -> XHTML = (LanguageFileType)addedFileType;
          case "JavaScript" -> JS = (LanguageFileType)addedFileType;
          case "Properties" -> PROPERTIES = (LanguageFileType)addedFileType;
          case "GUI_DESIGNER_FORM" -> GUI_DESIGNER_FORM = addedFileType;
        }
      }

      FileType removedFileType = event.getRemovedFileType();
      if (removedFileType != null) {
        String name = removedFileType.getName();
        switch (name) {
          case "JAVA" -> JAVA = PLAIN_TEXT;
          case "CLASS" -> CLASS = PLAIN_TEXT;
          case "JSP" -> JSP = PLAIN_TEXT;
          case "JSPX" -> JSPX = PLAIN_TEXT;
          case "XML" -> XML = PLAIN_TEXT;
          case "DTD" -> DTD = PLAIN_TEXT;
          case "HTML" -> HTML = PLAIN_TEXT;
          case "XHTML" -> XHTML = PLAIN_TEXT;
          case "JavaScript" -> JS = PLAIN_TEXT;
          case "Properties" -> PROPERTIES = PLAIN_TEXT;
          case "GUI_DESIGNER_FORM" -> GUI_DESIGNER_FORM = PLAIN_TEXT;
          case "PATCH" -> {}
        }
      }
    }
  }
}
