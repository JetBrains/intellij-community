/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.SyntaxHighlighterColors;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public interface HighlightInfoType {
  @NonNls String UNUSED_SYMBOL_SHORT_NAME = "UNUSED_SYMBOL";
  @NonNls String UNUSED_SYMBOL_DISPLAY_NAME = InspectionsBundle.message("unused.symbol");
  @NonNls String UNUSED_SYMBOL_ID = "UnusedDeclaration";

  @NonNls String DEPRECATION_SHORT_NAME = "Deprecation";
  @NonNls String DEPRECATION_ID = "deprecation";
  @NonNls String DEPRECATION_DISPLAY_NAME = InspectionsBundle.message("inspection.deprecated.display.name");

  HighlightInfoType WRONG_REF = new HighlightInfoTypeImpl(HighlightSeverity.ERROR, CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES);
  HighlightInfoType ERROR = new HighlightInfoTypeImpl(HighlightSeverity.ERROR, CodeInsightColors.ERRORS_ATTRIBUTES);

  HighlightInfoType GENERIC_WARNINGS_OR_ERRORS_FROM_SERVER = new HighlightInfoTypeImpl(HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING, CodeInsightColors.GENERIC_SERVER_ERROR_OR_WARNING);

  HighlightInfoType DUPLICATE_FROM_SERVER = new HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, CodeInsightColors.DUPLICATE_FROM_SERVER);

  HighlightInfoType UNUSED_SYMBOL = new HighlightInfoTypeSeverityByKey(HighlightDisplayKey.find(UNUSED_SYMBOL_SHORT_NAME) == null ?
                                                                       HighlightDisplayKey.register(UNUSED_SYMBOL_SHORT_NAME,
                                                                                                    UNUSED_SYMBOL_DISPLAY_NAME,
                                                                                                    UNUSED_SYMBOL_ID) : HighlightDisplayKey.find(UNUSED_SYMBOL_SHORT_NAME),
                                                                       CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES);


  HighlightInfoType DEPRECATED = new HighlightInfoTypeSeverityByKey(
    HighlightDisplayKey.find(DEPRECATION_SHORT_NAME) == null ? HighlightDisplayKey
      .register(DEPRECATION_SHORT_NAME, DEPRECATION_DISPLAY_NAME, DEPRECATION_ID) : HighlightDisplayKey.find(DEPRECATION_SHORT_NAME),
    CodeInsightColors.DEPRECATED_ATTRIBUTES);

  HighlightInfoType LOCAL_VARIABLE = new HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, CodeInsightColors.LOCAL_VARIABLE_ATTRIBUTES);
  HighlightInfoType INSTANCE_FIELD = new HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, CodeInsightColors.INSTANCE_FIELD_ATTRIBUTES);
  HighlightInfoType STATIC_FIELD = new HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, CodeInsightColors.STATIC_FIELD_ATTRIBUTES);
  HighlightInfoType PARAMETER = new HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, CodeInsightColors.PARAMETER_ATTRIBUTES);
  // t.o.d.o attributes depend on the t.o.d.o text
  HighlightInfoType TODO = new HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, null);
  HighlightInfoType METHOD_CALL = new HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, CodeInsightColors.METHOD_CALL_ATTRIBUTES);
  HighlightInfoType METHOD_DECLARATION = new HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, CodeInsightColors.METHOD_DECLARATION_ATTRIBUTES);
  HighlightInfoType CONSTRUCTOR_CALL = new HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, CodeInsightColors.CONSTRUCTOR_CALL_ATTRIBUTES);
  HighlightInfoType CONSTRUCTOR_DECLARATION = new HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, CodeInsightColors.CONSTRUCTOR_DECLARATION_ATTRIBUTES);
  HighlightInfoType STATIC_METHOD = new HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, CodeInsightColors.STATIC_METHOD_ATTRIBUTES);
  HighlightInfoType CLASS_NAME = new HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, CodeInsightColors.CLASS_NAME_ATTRIBUTES);
  HighlightInfoType INTERFACE_NAME = new HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, CodeInsightColors.INTERFACE_NAME_ATTRIBUTES);
  HighlightInfoType TYPE_PARAMETER_NAME = new HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, CodeInsightColors.TYPE_PARAMETER_NAME_ATTRIBUTES);
  HighlightInfoType ABSTRACT_CLASS_NAME = new HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, CodeInsightColors.ABSTRACT_CLASS_NAME_ATTRIBUTES);
  HighlightInfoType ANNOTATION_NAME = new HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, CodeInsightColors.ANNOTATION_NAME_ATTRIBUTES);
  HighlightInfoType ANNOTATION_ATTRIBUTE_NAME = new HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, CodeInsightColors.ANNOTATION_ATTRIBUTE_NAME_ATTRIBUTES);
  HighlightInfoType REASSIGNED_LOCAL_VARIABLE = new HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, CodeInsightColors.REASSIGNED_LOCAL_VARIABLE_ATTRIBUTES);
  HighlightInfoType REASSIGNED_PARAMETER = new HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, CodeInsightColors.REASSIGNED_PARAMETER_ATTRIBUTES);
  HighlightInfoType IMPLICIT_ANONYMOUS_CLASS_PARAMETER = new HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, CodeInsightColors.IMPLICIT_ANONYMOUS_CLASS_PARAMETER_ATTRIBUTES);

  HighlightInfoType WARNING = new HighlightInfoTypeImpl(HighlightSeverity.WARNING, CodeInsightColors.WARNINGS_ATTRIBUTES);
  HighlightInfoType INFO = new HighlightInfoTypeImpl(HighlightSeverity.INFO, CodeInsightColors.INFO_ATTRIBUTES);
  HighlightInfoType INFORMATION = new HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, CodeInsightColors.INFORMATION_ATTRIBUTES);
  HighlightSeverity INJECTED_FRAGMENT_SEVERITY = new HighlightSeverity("INJECTED_FRAGMENT_SEVERITY", HighlightSeverity.INFORMATION.myVal-1);
  HighlightInfoType INJECTED_LANGUAGE_FRAGMENT = new HighlightInfoTypeImpl(INJECTED_FRAGMENT_SEVERITY, CodeInsightColors.INFORMATION_ATTRIBUTES);

  HighlightInfoType UNHANDLED_EXCEPTION = new HighlightInfoTypeImpl(HighlightSeverity.ERROR, CodeInsightColors.ERRORS_ATTRIBUTES);
  HighlightInfoType JAVA_KEYWORD = new HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, SyntaxHighlighterColors.KEYWORD);

  HighlightSeverity getSeverity(PsiElement psiElement);

  TextAttributesKey getAttributesKey();

  class HighlightInfoTypeImpl implements HighlightInfoType, JDOMExternalizable {
    private final HighlightSeverity mySeverity;
    private final TextAttributesKey myAttributesKey;

    //read external only
    public HighlightInfoTypeImpl() {
      mySeverity = new HighlightSeverity();
      myAttributesKey = new TextAttributesKey();
    }

    public HighlightInfoTypeImpl(@NotNull HighlightSeverity severity, TextAttributesKey attributesKey) {
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

      if (!Comparing.equal(myAttributesKey, that.myAttributesKey)) return false;
      if (!mySeverity.equals(that.mySeverity)) return false;

      return true;
    }

    public int hashCode() {
      int result = mySeverity.hashCode();
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
      InspectionProfile profile = psiElement == null
                                  ? (InspectionProfile)InspectionProfileManager.getInstance().getRootProfile()
                                  : InspectionProjectProfileManager.getInstance(psiElement.getProject()).getInspectionProfile();
      HighlightDisplayLevel level = profile.getErrorLevel(myToolKey, psiElement);
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
      HighlightDisplayLevel level = psiElement != null ? InspectionProjectProfileManager.getInstance(psiElement.getProject()).getInspectionProfile().getErrorLevel(mySeverityKey,
                                                                                                                                                                   psiElement) : ((InspectionProfile)InspectionProfileManager.getInstance().getRootProfile()).getErrorLevel(mySeverityKey,
                                                                                                                                                                                                                                                                         psiElement);
      LOG.assertTrue(level != HighlightDisplayLevel.DO_NOT_SHOW);
      return level.getSeverity();
    }

    public TextAttributesKey getAttributesKey() {
      final HighlightSeverity severity = getSeverity(null);
      final HighlightInfoTypeImpl infoType = SeverityRegistrar.getInstance().getHighlightInfoTypeBySeverity(severity);
      return infoType != null
             ? infoType.getAttributesKey()
             : severity == HighlightSeverity.ERROR
               ? CodeInsightColors.ERRORS_ATTRIBUTES
               : severity == HighlightSeverity.WARNING ? CodeInsightColors.WARNINGS_ATTRIBUTES : CodeInsightColors.INFO_ATTRIBUTES;
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    public String toString() {
      return "HighlightInfoTypeSeverityByKeyAttrBySeverity[severity=" + mySeverityKey + "]";

    }
  }
}
