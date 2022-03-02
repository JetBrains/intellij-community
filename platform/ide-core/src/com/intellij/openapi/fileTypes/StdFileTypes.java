// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes;

import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.highlighter.WorkspaceFileType;
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

  /**
   * @deprecated use {@link WorkspaceFileType#INSTANCE} instead.
   */
  @Deprecated(forRemoval = true)
  public static volatile FileType IDEA_WORKSPACE = FileTypeManager.getInstance().getStdFileType("IDEA_WORKSPACE");

  /**
   * @deprecated use {@link ProjectFileType#INSTANCE} instead.
   */
  @Deprecated(forRemoval = true)
  public static volatile FileType IDEA_PROJECT = FileTypeManager.getInstance().getStdFileType("IDEA_PROJECT");

  /**
   * @deprecated use {@link ModuleFileType#INSTANCE} instead.
   */
  @Deprecated(forRemoval = true)
  public static volatile FileType IDEA_MODULE = FileTypeManager.getInstance().getStdFileType("IDEA_MODULE");


  public static class StdFileTypesUpdater implements FileTypeListener {
    @Override
    public void fileTypesChanged(@NotNull FileTypeEvent event) {
      FileType addedFileType = event.getAddedFileType();
      if (addedFileType != null) {
        String name = addedFileType.getName();
        switch (name) {
          case "JAVA": JAVA = (LanguageFileType)addedFileType; break;
          case "CLASS": CLASS = addedFileType; break;
          case "JSP": JSP = (LanguageFileType)addedFileType; break;
          case "JSPX": JSPX = (LanguageFileType)addedFileType; break;
          case "XML": XML = (LanguageFileType)addedFileType; break;
          case "DTD": DTD = (LanguageFileType)addedFileType; break;
          case "HTML": HTML = (LanguageFileType)addedFileType; break;
          case "XHTML": XHTML = (LanguageFileType)addedFileType; break;
          case "JavaScript": JS = (LanguageFileType)addedFileType; break;
          case "Properties": PROPERTIES = (LanguageFileType)addedFileType; break;
          case "GUI_DESIGNER_FORM": GUI_DESIGNER_FORM = addedFileType; break;
          case "IDEA_WORKSPACE": IDEA_WORKSPACE = addedFileType; break;
          case "IDEA_PROJECT": IDEA_PROJECT = addedFileType; break;
          case "IDEA_MODULE": IDEA_MODULE = addedFileType; break;
        }
      }

      FileType removedFileType = event.getRemovedFileType();
      if (removedFileType != null) {
        String name = removedFileType.getName();
        switch (name) {
          case "JAVA": JAVA = PLAIN_TEXT; break;
          case "CLASS": CLASS = PLAIN_TEXT; break;
          case "JSP": JSP = PLAIN_TEXT; break;
          case "JSPX": JSPX = PLAIN_TEXT; break;
          case "XML": XML = PLAIN_TEXT; break;
          case "DTD": DTD = PLAIN_TEXT; break;
          case "HTML": HTML = PLAIN_TEXT; break;
          case "XHTML": XHTML = PLAIN_TEXT; break;
          case "JavaScript": JS = PLAIN_TEXT; break;
          case "Properties": PROPERTIES = PLAIN_TEXT; break;
          case "GUI_DESIGNER_FORM": GUI_DESIGNER_FORM = PLAIN_TEXT; break;
          case "IDEA_WORKSPACE": IDEA_WORKSPACE = PLAIN_TEXT; break;
          case "IDEA_PROJECT": IDEA_PROJECT = PLAIN_TEXT; break;
          case "IDEA_MODULE": IDEA_MODULE = PLAIN_TEXT; break;
          case "PATCH":
            break;
        }
      }
    }
  }
}
