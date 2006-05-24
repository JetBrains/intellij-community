package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.deprecation.DeprecationInspection;
import com.intellij.codeInspection.javaDoc.JavaDocReferenceInspection;
import com.intellij.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection;
import com.intellij.codeInspection.unusedImport.UnusedImportLocalInspection;
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspection;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import org.jdom.Element;

public interface HighlightInfoType {
  HighlightInfoType WRONG_REF = new HighlightInfoTypeImpl(HighlightSeverity.ERROR, CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES);
  HighlightInfoType ERROR = new HighlightInfoTypeImpl(HighlightSeverity.ERROR, CodeInsightColors.ERRORS_ATTRIBUTES);

  HighlightInfoType ASPECT_ERROR = new HighlightInfoTypeImpl(HighlightSeverity.ERROR, CodeInsightColors.ERRORS_ATTRIBUTES);
  HighlightInfoType ASPECT_WARNING = new HighlightInfoTypeImpl(HighlightSeverity.WARNING, CodeInsightColors.WARNINGS_ATTRIBUTES);

  HighlightInfoType UNCHECKED_WARNING = new HighlightInfoTypeSeverityByKeyAttrBySeverity(HighlightDisplayKey.find(UncheckedWarningLocalInspection.SHORT_NAME) == null ?
                                                                                         HighlightDisplayKey.register(UncheckedWarningLocalInspection.SHORT_NAME, UncheckedWarningLocalInspection.DISPLAY_NAME, UncheckedWarningLocalInspection.ID) : HighlightDisplayKey.find(UncheckedWarningLocalInspection.SHORT_NAME));

  HighlightInfoType WRONG_ELEMENT_NAME = new HighlightInfoTypeImpl(HighlightSeverity.ERROR, CodeInsightColors.ERRORS_ATTRIBUTES);

  HighlightInfoType GENERIC_WARNINGS_OR_ERRORS_FROM_SERVER = new HighlightInfoTypeImpl(HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING, CodeInsightColors.GENERIC_SERVER_ERROR_OR_WARNING);  

  HighlightInfoType UNUSED_SYMBOL = new HighlightInfoTypeSeverityByKey(HighlightDisplayKey.find(UnusedSymbolLocalInspection.SHORT_NAME) == null ?
                                                                       HighlightDisplayKey.register(UnusedSymbolLocalInspection.SHORT_NAME, UnusedSymbolLocalInspection.DISPLAY_NAME) : HighlightDisplayKey.find(UnusedSymbolLocalInspection.SHORT_NAME),
                                                                       CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES);
  HighlightInfoType UNUSED_IMPORT = new HighlightInfoTypeSeverityByKey(HighlightDisplayKey.find(UnusedImportLocalInspection.SHORT_NAME) == null ?
                                                                       HighlightDisplayKey.register(UnusedImportLocalInspection.SHORT_NAME, UnusedImportLocalInspection.DISPLAY_NAME) : HighlightDisplayKey.find(UnusedImportLocalInspection.SHORT_NAME), CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES);
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
  HighlightInfoType INFO = new HighlightInfoTypeImpl(HighlightSeverity.INFO, CodeInsightColors.INFO_ATTRIBUTES);
  HighlightInfoType OVERFLOW_WARNING = new HighlightInfoTypeImpl(HighlightSeverity.WARNING, CodeInsightColors.ERRORS_ATTRIBUTES);
  HighlightInfoType INFORMATION = new HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, CodeInsightColors.INFORMATION_ATTRIBUTES);

  /** @fabrique does not highlight returns outside method in codefragments */
  HighlightInfoType RETURN_OUTSIDE_METHOD = new HighlightInfoTypeImpl(HighlightSeverity.ERROR, CodeInsightColors.ERRORS_ATTRIBUTES);
  HighlightInfoType UNHANDLED_EXCEPTION = new HighlightInfoTypeImpl(HighlightSeverity.ERROR, CodeInsightColors.ERRORS_ATTRIBUTES);

  HighlightSeverity getSeverity(PsiElement psiElement);

  TextAttributesKey getAttributesKey();

  class HighlightInfoTypeImpl implements HighlightInfoType, JDOMExternalizable {
    private HighlightSeverity mySeverity;
    private TextAttributesKey myAttributesKey;


    //read external only
    public HighlightInfoTypeImpl() {
      mySeverity = new HighlightSeverity();
      myAttributesKey = new TextAttributesKey();
    }

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

    public void readExternal(Element element) throws InvalidDataException {
      mySeverity.readExternal(element);
      myAttributesKey.readExternal(element);
    }

    public void writeExternal(Element element) throws WriteExternalException {
      mySeverity.writeExternal(element);
      myAttributesKey.writeExternal(element);
    }


    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final HighlightInfoTypeImpl that = (HighlightInfoTypeImpl)o;

      if (!myAttributesKey.equals(that.myAttributesKey)) return false;
      if (!mySeverity.equals(that.mySeverity)) return false;

      return true;
    }

    public int hashCode() {
      int result;
      result = mySeverity.hashCode();
      result = 29 * result + myAttributesKey.hashCode();
      return result;
    }
  }

  class HighlightInfoTypeSeverityByKey implements HighlightInfoType {
    static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.HighlightInfoType.HighlightInfoTypeSeverityByKey");

    private final TextAttributesKey myAttributesKey;
    private final HighlightDisplayKey myToolKey;

    public HighlightInfoTypeSeverityByKey(HighlightDisplayKey severityKey, TextAttributesKey attributesKey) {
      myToolKey = severityKey;
      myAttributesKey = attributesKey;
    }

    public HighlightSeverity getSeverity(final PsiElement psiElement) {
      HighlightDisplayLevel level = (psiElement != null ? InspectionProjectProfileManager.getInstance(psiElement.getProject()).getInspectionProfile(psiElement) :
                                     ((InspectionProfile)InspectionProfileManager.getInstance().getRootProfile())).getErrorLevel(myToolKey);
      LOG.assertTrue(level != HighlightDisplayLevel.DO_NOT_SHOW);
      return level.getSeverity();
    }

    public TextAttributesKey getAttributesKey() {
      return myAttributesKey;
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    public String toString() {
      return "HighlightInfoTypeSeverityByKey[severity=" + myToolKey + ", key=" + myAttributesKey + "]";
    }

    public HighlightDisplayKey getSeverityKey() {
      return myToolKey;
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
      return level.getSeverity();
    }

    public TextAttributesKey getAttributesKey() {
      final HighlightSeverity severity = getSeverity(null);
      final HighlightInfoTypeImpl infoType = SeverityRegistrar.getHighlightInfoTypeBySeverity(severity);
      return infoType != null ? infoType.getAttributesKey() : (severity == HighlightSeverity.ERROR ? CodeInsightColors.ERRORS_ATTRIBUTES
                                                               : (severity == HighlightSeverity.WARNING ? CodeInsightColors.WARNINGS_ATTRIBUTES : CodeInsightColors.INFO_ATTRIBUTES));
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    public String toString() {
      return "HighlightInfoTypeSeverityByKeyAttrBySeverity[severity=" + mySeverityKey + "]";

    }
  }
}
