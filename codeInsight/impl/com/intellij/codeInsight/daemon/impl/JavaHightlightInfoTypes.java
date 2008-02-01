/*
 * User: anna
 * Date: 01-Feb-2008
 */
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.javaDoc.JavaDocReferenceInspection;
import com.intellij.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection;
import com.intellij.codeInspection.unusedImport.UnusedImportLocalInspection;
import com.intellij.openapi.editor.colors.CodeInsightColors;

public class JavaHightlightInfoTypes {

  public static final HighlightInfoType UNUSED_IMPORT = new HighlightInfoType.HighlightInfoTypeSeverityByKey(
    HighlightDisplayKey.find(UnusedImportLocalInspection.SHORT_NAME) == null ?
                                                                       HighlightDisplayKey.register(UnusedImportLocalInspection.SHORT_NAME, UnusedImportLocalInspection.DISPLAY_NAME) : HighlightDisplayKey.find(UnusedImportLocalInspection.SHORT_NAME), CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES);
  public static final HighlightInfoType JAVADOC_WRONG_REF = new HighlightInfoType.HighlightInfoTypeSeverityByKey(HighlightDisplayKey.find(
    JavaDocReferenceInspection.SHORT_NAME) == null ?
                                                                           HighlightDisplayKey.register(JavaDocReferenceInspection.SHORT_NAME, JavaDocReferenceInspection.DISPLAY_NAME) : HighlightDisplayKey.find(JavaDocReferenceInspection.SHORT_NAME), CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES);
  public static final HighlightInfoType UNCHECKED_WARNING = new HighlightInfoType.HighlightInfoTypeSeverityByKeyAttrBySeverity(HighlightDisplayKey.find(
    UncheckedWarningLocalInspection.SHORT_NAME) == null ?
                                                                                         HighlightDisplayKey.register(UncheckedWarningLocalInspection.SHORT_NAME, UncheckedWarningLocalInspection.DISPLAY_NAME, UncheckedWarningLocalInspection.ID) : HighlightDisplayKey.find(UncheckedWarningLocalInspection.SHORT_NAME));

  private JavaHightlightInfoTypes() {
  }
}