package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.deprecation.DeprecationInspection;
import com.intellij.codeInspection.ex.InspectionProfile;
import com.intellij.codeInspection.javaDoc.JavaDocReferenceInspection;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;

public interface HighlightInfoType {
  HighlightInfoType WRONG_REF = new HighlightInfoTypeImpl(HighlightSeverity.ERROR, CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES);
  HighlightInfoType ERROR = new HighlightInfoTypeImpl(HighlightSeverity.ERROR, CodeInsightColors.ERRORS_ATTRIBUTES);

  HighlightInfoType ASPECT_ERROR = new HighlightInfoTypeImpl(HighlightSeverity.ERROR, CodeInsightColors.ERRORS_ATTRIBUTES);
  HighlightInfoType ASPECT_WARNING = new HighlightInfoTypeImpl(HighlightSeverity.WARNING, CodeInsightColors.WARNINGS_ATTRIBUTES);

  HighlightInfoType UNCHECKED_WARNING = new HighlightInfoTypeSeverityByKeyAttrBySeverity(HighlightDisplayKey.UNCHECKED_WARNING);

  HighlightInfoType WRONG_ELEMENT_NAME = new HighlightInfoTypeImpl(HighlightSeverity.ERROR, CodeInsightColors.ERRORS_ATTRIBUTES);

  HighlightInfoType UNUSED_SYMBOL = new HighlightInfoTypeSeverityByKey(HighlightDisplayKey.UNUSED_SYMBOL, CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES);
  HighlightInfoType UNUSED_IMPORT = new HighlightInfoTypeSeverityByKey(HighlightDisplayKey.UNUSED_IMPORT, CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES);
  HighlightInfoType DEPRECATED = new HighlightInfoTypeSeverityByKey(HighlightDisplayKey.find(DeprecationInspection.SHORT_NAME) == null ?
                                                                    HighlightDisplayKey.register(DeprecationInspection.SHORT_NAME, DeprecationInspection.DISPLAY_NAME) : HighlightDisplayKey.find(DeprecationInspection.SHORT_NAME), CodeInsightColors.DEPRECATED_ATTRIBUTES);

  HighlightInfoType JAVADOC_WRONG_REF = new HighlightInfoTypeSeverityByKey(HighlightDisplayKey.find(JavaDocReferenceInspection.SHORT_NAME) == null ?
                                                                           HighlightDisplayKey.register(JavaDocReferenceInspection.SHORT_NAME, JavaDocReferenceInspection.DISPLAY_NAME) : HighlightDisplayKey.find(JavaDocReferenceInspection.SHORT_NAME), CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES);


  /** @fabrique */
  HighlightInfoType LOCAL_VARIABLE = new HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, CodeInsightColors.LOCAL_VARIABLE_ATTRIBUTES);
  HighlightInfoType INSTANCE_FIELD = new HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, CodeInsightColors.INSTANCE_FIELD_ATTRIBUTES);
  HighlightInfoType STATIC_FIELD = new HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, CodeInsightColors.STATIC_FIELD_ATTRIBUTES);
  HighlightInfoType PARAMETER = new HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, CodeInsightColors.PARAMETER_ATTRIBUTES);
  // t.o.d.o attributes depend on the t.o.d.o text
  HighlightInfoType TODO = new HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, null);
  HighlightInfoType JOIN_POINT = new HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, CodeInsightColors.JOIN_POINT);
  HighlightInfoType METHOD_CALL = new HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, CodeInsightColors.METHOD_CALL_ATTRIBUTES);
  HighlightInfoType METHOD_DECLARATION = new HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, CodeInsightColors.METHOD_DECLARATION_ATTRIBUTES);
  HighlightInfoType CONSTRUCTOR_CALL = new HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, CodeInsightColors.CONSTRUCTOR_CALL_ATTRIBUTES);
  HighlightInfoType CONSTRUCTOR_DECLARATION = new HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, CodeInsightColors.CONSTRUCTOR_DECLARATION_ATTRIBUTES);
  HighlightInfoType STATIC_METHOD = new HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, CodeInsightColors.STATIC_METHOD_ATTRIBUTES);
  HighlightInfoType CLASS_NAME = new HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, CodeInsightColors.CLASS_NAME_ATTRIBUTES);
  HighlightInfoType INTERFACE_NAME = new HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, CodeInsightColors.INTERFACE_NAME_ATTRIBUTES);
  HighlightInfoType TYPE_PARAMETER_NAME = new HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, CodeInsightColors.TYPE_PARAMETER_NAME_ATTRIBUTES);
  HighlightInfoType ABSTRACT_CLASS_NAME = new HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, CodeInsightColors.ABSTRACT_CLASS_NAME_ATTRIBUTES);
  HighlightInfoType JAVA_KEYWORD = new HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, HighlighterColors.JAVA_KEYWORD);
  HighlightInfoType ANNOTATION_NAME = new HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, CodeInsightColors.ANNOTATION_NAME_ATTRIBUTES);
  HighlightInfoType ANNOTATION_ATTRIBUTE_NAME = new HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, CodeInsightColors.ANNOTATION_ATTRIBUTE_NAME_ATTRIBUTES);
  HighlightInfoType REASSIGNED_LOCAL_VARIABLE = new HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, CodeInsightColors.REASSIGNED_LOCAL_VARIABLE_ATTRIBUTES);
  HighlightInfoType REASSIGNED_PARAMETER = new HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, CodeInsightColors.REASSIGNED_PARAMETER_ATTRIBUTES);

  HighlightInfoType WARNING = new HighlightInfoTypeImpl(HighlightSeverity.WARNING, CodeInsightColors.WARNINGS_ATTRIBUTES);
  HighlightInfoType OVERFLOW_WARNING = new HighlightInfoTypeImpl(HighlightSeverity.WARNING, CodeInsightColors.ERRORS_ATTRIBUTES);
  HighlightInfoType INFORMATION = new HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, CodeInsightColors.INFORMATION_ATTRIBUTES);

  /** @fabrique does not highlight returns outside method in codefragments */
  HighlightInfoType RETURN_OUTSIDE_METHOD = new HighlightInfoTypeImpl(HighlightSeverity.ERROR, CodeInsightColors.ERRORS_ATTRIBUTES);
  HighlightInfoType UNHANDLED_EXCEPTION = new HighlightInfoTypeImpl(HighlightSeverity.ERROR, CodeInsightColors.ERRORS_ATTRIBUTES);

  HighlightSeverity getSeverity(PsiElement psiElement);

  TextAttributesKey getAttributesKey();

  class HighlightInfoTypeImpl implements HighlightInfoType {
    private final HighlightSeverity mySeverity;
    private final TextAttributesKey myAttributesKey;

    public HighlightInfoTypeImpl(HighlightSeverity severity, TextAttributesKey attributesKey) {
      mySeverity = severity;
      myAttributesKey = attributesKey;
    }

    public HighlightSeverity getSeverity(PsiElement psiElement) {
      return mySeverity;
    }

    public TextAttributesKey getAttributesKey() {
      return myAttributesKey;
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    public String toString() {
      return "HighlightInfoTypeImpl[severity=" + mySeverity + ", key=" + myAttributesKey + "]";
    }
  }

  class HighlightInfoTypeSeverityByKey implements HighlightInfoType {
    static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.HighlightInfoType.HighlightInfoTypeSeverityByKey");

    private final TextAttributesKey myAttributesKey;
    private final HighlightDisplayKey mySeverityKey;

    public HighlightInfoTypeSeverityByKey(HighlightDisplayKey severityKey, TextAttributesKey attributesKey) {
      mySeverityKey = severityKey;
      myAttributesKey = attributesKey;
    }

    public HighlightSeverity getSeverity(final PsiElement psiElement) {
      HighlightDisplayLevel level = (psiElement != null ? InspectionProjectProfileManager.getInstance(psiElement.getProject()).getInspectionProfile(psiElement) :
                                    ((InspectionProfile)InspectionProfileManager.getInstance().getRootProfile())).getErrorLevel(mySeverityKey);
      LOG.assertTrue(level != HighlightDisplayLevel.DO_NOT_SHOW);
      return level == HighlightDisplayLevel.ERROR ? HighlightSeverity.ERROR : HighlightSeverity.WARNING;
    }

    public TextAttributesKey getAttributesKey() {
      return myAttributesKey;
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    public String toString() {
      return "HighlightInfoTypeSeverityByKey[severity=" + mySeverityKey + ", key=" + myAttributesKey + "]";
    }

    public HighlightDisplayKey getSeverityKey() {
      return mySeverityKey;
    }
  }

  class HighlightInfoTypeSeverityByKeyAttrBySeverity implements HighlightInfoType {
    static final Logger LOG = Logger.getInstance(
      "#com.intellij.codeInsight.daemon.impl.HighlightInfoType.HighlightInfoTypeSeverityByKeyAttrBySeverity");

    private final HighlightDisplayKey mySeverityKey;

    public HighlightInfoTypeSeverityByKeyAttrBySeverity(HighlightDisplayKey severityKey) {
      mySeverityKey = severityKey;
    }

    public HighlightSeverity getSeverity(final PsiElement psiElement) {
      HighlightDisplayLevel level = psiElement != null ? InspectionProjectProfileManager.getInstance(psiElement.getProject()).getInspectionProfile(psiElement).getErrorLevel(mySeverityKey) : ((InspectionProfile)InspectionProfileManager.getInstance().getRootProfile()).getErrorLevel(mySeverityKey);
      LOG.assertTrue(level != HighlightDisplayLevel.DO_NOT_SHOW);
      return level == HighlightDisplayLevel.ERROR ? HighlightSeverity.ERROR : HighlightSeverity.WARNING;
    }

    public TextAttributesKey getAttributesKey() {
      return getSeverity(null) == HighlightSeverity.ERROR ? CodeInsightColors.ERRORS_ATTRIBUTES : CodeInsightColors.WARNINGS_ATTRIBUTES;
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    public String toString() {
      return "HighlightInfoTypeSeverityByKeyAttrBySeverity[severity=" + mySeverityKey + "]";

    }
  }
}
