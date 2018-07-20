// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.DeprecationUtil;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public interface HighlightInfoType {
  @NonNls String UNUSED_SYMBOL_SHORT_NAME = "unused";
  @NonNls String UNUSED_SYMBOL_DISPLAY_NAME = InspectionsBundle.message("inspection.dead.code.display.name");

  HighlightInfoType ERROR = new HighlightInfoTypeImpl(HighlightSeverity.ERROR, CodeInsightColors.ERRORS_ATTRIBUTES);
  HighlightInfoType WARNING = new HighlightInfoTypeImpl(HighlightSeverity.WARNING, CodeInsightColors.WARNINGS_ATTRIBUTES);
  /** @deprecated use {@link #WEAK_WARNING} instead */
  @Deprecated HighlightInfoType INFO = new HighlightInfoTypeImpl(HighlightSeverity.INFO, CodeInsightColors.INFO_ATTRIBUTES);
  HighlightInfoType WEAK_WARNING = new HighlightInfoTypeImpl(HighlightSeverity.WEAK_WARNING, CodeInsightColors.WEAK_WARNING_ATTRIBUTES);
  HighlightInfoType INFORMATION = new HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, CodeInsightColors.INFORMATION_ATTRIBUTES);

  HighlightInfoType WRONG_REF = new HighlightInfoTypeImpl(HighlightSeverity.ERROR, CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES);

  HighlightInfoType GENERIC_WARNINGS_OR_ERRORS_FROM_SERVER = new HighlightInfoTypeImpl(HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING, CodeInsightColors.GENERIC_SERVER_ERROR_OR_WARNING);

  HighlightInfoType DUPLICATE_FROM_SERVER = new HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, CodeInsightColors.DUPLICATE_FROM_SERVER);

  HighlightInfoType UNUSED_SYMBOL = new HighlightInfoTypeSeverityByKey(
    HighlightDisplayKey.findOrRegister(UNUSED_SYMBOL_SHORT_NAME, UNUSED_SYMBOL_DISPLAY_NAME, UNUSED_SYMBOL_SHORT_NAME),
    CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES);

  HighlightInfoType DEPRECATED = new HighlightInfoTypeSeverityByKey(
    HighlightDisplayKey.findOrRegister(
      DeprecationUtil.DEPRECATION_SHORT_NAME, DeprecationUtil.DEPRECATION_DISPLAY_NAME, DeprecationUtil.DEPRECATION_ID),
    CodeInsightColors.DEPRECATED_ATTRIBUTES);

  HighlightInfoType MARKED_FOR_REMOVAL = new HighlightInfoTypeSeverityByKey(
    HighlightDisplayKey.findOrRegister(
      DeprecationUtil.FOR_REMOVAL_SHORT_NAME, DeprecationUtil.FOR_REMOVAL_DISPLAY_NAME, DeprecationUtil.FOR_REMOVAL_ID),
    CodeInsightColors.MARKED_FOR_REMOVAL_ATTRIBUTES);

  HighlightSeverity SYMBOL_TYPE_SEVERITY = new HighlightSeverity("SYMBOL_TYPE_SEVERITY", HighlightSeverity.INFORMATION.myVal-2);

  /**
   * @deprecated For Java use JavaHighlightInfoTypes.LOCAL_VARIABLE or create a language-specific HighlightInfoType.
   * The field will be removed in version 17.
   */
  @SuppressWarnings("deprecation")
  @Deprecated
  HighlightInfoType LOCAL_VARIABLE = new HighlightInfoTypeImpl(SYMBOL_TYPE_SEVERITY, CodeInsightColors.LOCAL_VARIABLE_ATTRIBUTES);
  /**
   * @deprecated For Java use JavaHighlightInfoTypes.INSTANCE_FIELD or create a language-specific HighlightInfoType.
   * The field will be removed in version 17.
   */
  @SuppressWarnings("deprecation")
  @Deprecated
  HighlightInfoType INSTANCE_FIELD = new HighlightInfoTypeImpl(SYMBOL_TYPE_SEVERITY, CodeInsightColors.INSTANCE_FIELD_ATTRIBUTES);
  /**
   * @deprecated For Java use JavaHighlightInfoTypes.STATIC_FIELD or create a language-specific HighlightInfoType.
   * The field will be removed in version 17.
   */
  @SuppressWarnings("deprecation")
  @Deprecated
  HighlightInfoType STATIC_FIELD = new HighlightInfoTypeImpl(SYMBOL_TYPE_SEVERITY, CodeInsightColors.STATIC_FIELD_ATTRIBUTES);
  /**
   * @deprecated For Java use JavaHighlightInfoTypes.STATIC_FINAL_FIELD or create a language-specific HighlightInfoType.
   * The field will be removed in version 17.
   */
  @SuppressWarnings("deprecation")
  @Deprecated
  HighlightInfoType STATIC_FINAL_FIELD = new HighlightInfoTypeImpl(SYMBOL_TYPE_SEVERITY, CodeInsightColors.STATIC_FINAL_FIELD_ATTRIBUTES);
  /**
   * @deprecated For Java use JavaHighlightInfoTypes.PARAMETER or create a language-specific HighlightInfoType.
   * The field will be removed in version 17.
   */
  @SuppressWarnings("deprecation")
  @Deprecated
  HighlightInfoType PARAMETER = new HighlightInfoTypeImpl(SYMBOL_TYPE_SEVERITY, CodeInsightColors.PARAMETER_ATTRIBUTES);
  /**
   * @deprecated For Java use JavaHighlightInfoTypes.METHOD_CALL or create a language-specific HighlightInfoType.
   * The field will be removed in version 17.
   */
  @SuppressWarnings("deprecation")
  @Deprecated
  HighlightInfoType METHOD_CALL = new HighlightInfoTypeImpl(SYMBOL_TYPE_SEVERITY, CodeInsightColors.METHOD_CALL_ATTRIBUTES);
  /**
   * @deprecated For Java use JavaHighlightInfoTypes.METHOD_DECLARATION or create a language-specific HighlightInfoType.
   * The field will be removed in version 17.
   */
  @SuppressWarnings("deprecation")
  @Deprecated
  HighlightInfoType METHOD_DECLARATION = new HighlightInfoTypeImpl(SYMBOL_TYPE_SEVERITY, CodeInsightColors.METHOD_DECLARATION_ATTRIBUTES);
  /**
   * @deprecated For Java use JavaHighlightInfoTypes.CONSTRUCTOR_CALL or create a language-specific HighlightInfoType.
   * The field will be removed in version 17.
   */
  @Deprecated
  HighlightInfoType CONSTRUCTOR_CALL = new HighlightInfoTypeImpl(SYMBOL_TYPE_SEVERITY, CodeInsightColors.CONSTRUCTOR_CALL_ATTRIBUTES);
  /**
   * @deprecated For Java use JavaHighlightInfoTypes.CONSTRUCTOR_DECLARATION or create a language-specific HighlightInfoType.
   * The field will be removed in version 17.
   */
  @Deprecated
  HighlightInfoType CONSTRUCTOR_DECLARATION = new HighlightInfoTypeImpl(SYMBOL_TYPE_SEVERITY, CodeInsightColors.CONSTRUCTOR_DECLARATION_ATTRIBUTES);
  /**
   * @deprecated For Java use JavaHighlightInfoTypes.STATIC_METHOD or create a language-specific HighlightInfoType.
   * The field will be removed in version 17.
   */
  @SuppressWarnings("deprecation")
  @Deprecated
  HighlightInfoType STATIC_METHOD = new HighlightInfoTypeImpl(SYMBOL_TYPE_SEVERITY, CodeInsightColors.STATIC_METHOD_ATTRIBUTES);
  /**
   * @deprecated For Java use JavaHighlightInfoTypes.ABSTRACT_METHOD or create a language-specific HighlightInfoType.
   * The field will be removed in version 17.
   */
  @Deprecated
  HighlightInfoType ABSTRACT_METHOD = new HighlightInfoTypeImpl(SYMBOL_TYPE_SEVERITY, CodeInsightColors.ABSTRACT_METHOD_ATTRIBUTES);
  /**
   * @deprecated For Java use JavaHighlightInfoTypes.INHERITED_METHOD or create a language-specific HighlightInfoType.
   * The field will be removed in version 17.
   */
  @Deprecated
  HighlightInfoType INHERITED_METHOD = new HighlightInfoTypeImpl(SYMBOL_TYPE_SEVERITY, CodeInsightColors.INHERITED_METHOD_ATTRIBUTES);
  /**
   * @deprecated For Java use JavaHighlightInfoTypes.CLASS_NAME or create a language-specific HighlightInfoType.
   * The field will be removed in version 17.
   */
  @SuppressWarnings("deprecation")
  @Deprecated
  HighlightInfoType CLASS_NAME = new HighlightInfoTypeImpl(SYMBOL_TYPE_SEVERITY, CodeInsightColors.CLASS_NAME_ATTRIBUTES);
  /**
   * @deprecated For Java use JavaHighlightInfoTypes.ANONYMOUS_CLASS_NAME or create a language-specific HighlightInfoType.
   * The field will be removed in version 17.
   */
  @Deprecated
  HighlightInfoType ANONYMOUS_CLASS_NAME = new HighlightInfoTypeImpl(SYMBOL_TYPE_SEVERITY, CodeInsightColors.ANONYMOUS_CLASS_NAME_ATTRIBUTES);
  /**
   * @deprecated For Java use JavaHighlightInfoTypes.INTERFACE_NAME or create a language-specific HighlightInfoType.
   * The field will be removed in version 17.
   */
  @SuppressWarnings("deprecation")
  @Deprecated
  HighlightInfoType INTERFACE_NAME = new HighlightInfoTypeImpl(SYMBOL_TYPE_SEVERITY, CodeInsightColors.INTERFACE_NAME_ATTRIBUTES);
  /**
   * @deprecated For Java use JavaHighlightInfoTypes.ENUM_NAME or create a language-specific HighlightInfoType.
   * The field will be removed in version 17.
   */
  @Deprecated
  HighlightInfoType ENUM_NAME = new HighlightInfoTypeImpl(SYMBOL_TYPE_SEVERITY, CodeInsightColors.ENUM_NAME_ATTRIBUTES);
  /**
   * @deprecated For Java use JavaHighlightInfoTypes.TYPE_PARAMETER_NAME or create a language-specific HighlightInfoType.
   * The field will be removed in version 17.
   */
  @Deprecated
  HighlightInfoType TYPE_PARAMETER_NAME = new HighlightInfoTypeImpl(SYMBOL_TYPE_SEVERITY, CodeInsightColors.TYPE_PARAMETER_NAME_ATTRIBUTES);
  /**
   * @deprecated For Java use JavaHighlightInfoTypes.ABSTRACT_CLASS_NAME or create a language-specific HighlightInfoType.
   * The field will be removed in version 17.
   */
  @Deprecated
  HighlightInfoType ABSTRACT_CLASS_NAME = new HighlightInfoTypeImpl(SYMBOL_TYPE_SEVERITY, CodeInsightColors.ABSTRACT_CLASS_NAME_ATTRIBUTES);
  /**
   * @deprecated For Java use JavaHighlightInfoTypes.ANNOTATION_NAME or create a language-specific HighlightInfoType.
   * The field will be removed in version 17.
   */
  @Deprecated
  HighlightInfoType ANNOTATION_NAME = new HighlightInfoTypeImpl(SYMBOL_TYPE_SEVERITY, CodeInsightColors.ANNOTATION_NAME_ATTRIBUTES);
  /**
   * @deprecated For Java use JavaHighlightInfoTypes.ANNOTATION_ATTRIBUTE_NAME or create a language-specific HighlightInfoType.
   * The field will be removed in version 17.
   */
  @Deprecated
  HighlightInfoType ANNOTATION_ATTRIBUTE_NAME = new HighlightInfoTypeImpl(SYMBOL_TYPE_SEVERITY, CodeInsightColors.ANNOTATION_ATTRIBUTE_NAME_ATTRIBUTES);
  /**
   * @deprecated For Java use JavaHighlightInfoTypes.REASSIGNED_LOCAL_VARIABLE or create a language-specific HighlightInfoType.
   * The field will be removed in version 17.
   */
  @Deprecated
  HighlightInfoType REASSIGNED_LOCAL_VARIABLE = new HighlightInfoTypeImpl(SYMBOL_TYPE_SEVERITY, CodeInsightColors.REASSIGNED_LOCAL_VARIABLE_ATTRIBUTES);
  /**
   * @deprecated For Java use JavaHighlightInfoTypes.REASSIGNED_PARAMETER or create a language-specific HighlightInfoType.
   * The field will be removed in version 17.
   */
  @Deprecated
  HighlightInfoType REASSIGNED_PARAMETER = new HighlightInfoTypeImpl(SYMBOL_TYPE_SEVERITY, CodeInsightColors.REASSIGNED_PARAMETER_ATTRIBUTES);
  /**
   * @deprecated For Java use JavaHighlightInfoTypes.IMPLICIT_ANONYMOUS_CLASS_PARAMETER or create a language-specific HighlightInfoType.
   * The field will be removed in version 17.
   */
  @Deprecated
  HighlightInfoType IMPLICIT_ANONYMOUS_CLASS_PARAMETER = new HighlightInfoTypeImpl(SYMBOL_TYPE_SEVERITY, CodeInsightColors.IMPLICIT_ANONYMOUS_CLASS_PARAMETER_ATTRIBUTES);

  HighlightInfoType TODO = new HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, CodeInsightColors.TODO_DEFAULT_ATTRIBUTES, false);  // these are default attributes, can be configured differently for specific patterns
  HighlightInfoType UNHANDLED_EXCEPTION = new HighlightInfoTypeImpl(HighlightSeverity.ERROR, CodeInsightColors.ERRORS_ATTRIBUTES);

  HighlightSeverity INJECTED_FRAGMENT_SEVERITY = new HighlightSeverity("INJECTED_FRAGMENT", SYMBOL_TYPE_SEVERITY.myVal - 1);
  HighlightInfoType INJECTED_LANGUAGE_FRAGMENT = new HighlightInfoTypeImpl(SYMBOL_TYPE_SEVERITY, CodeInsightColors.INFORMATION_ATTRIBUTES);
  HighlightInfoType INJECTED_LANGUAGE_BACKGROUND = new HighlightInfoTypeImpl(INJECTED_FRAGMENT_SEVERITY, CodeInsightColors.INFORMATION_ATTRIBUTES);

  HighlightSeverity ELEMENT_UNDER_CARET_SEVERITY = new HighlightSeverity("ELEMENT_UNDER_CARET", HighlightSeverity.ERROR.myVal + 1);
  HighlightInfoType ELEMENT_UNDER_CARET_READ = new HighlightInfoType.HighlightInfoTypeImpl(ELEMENT_UNDER_CARET_SEVERITY, EditorColors.IDENTIFIER_UNDER_CARET_ATTRIBUTES);
  HighlightInfoType ELEMENT_UNDER_CARET_WRITE = new HighlightInfoType.HighlightInfoTypeImpl(ELEMENT_UNDER_CARET_SEVERITY, EditorColors.WRITE_IDENTIFIER_UNDER_CARET_ATTRIBUTES);

  /**
   * @see com.intellij.openapi.editor.impl.RangeHighlighterImpl#VISIBLE_IF_FOLDED
   */
  Set<HighlightInfoType> VISIBLE_IF_FOLDED = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
    ELEMENT_UNDER_CARET_READ, 
    ELEMENT_UNDER_CARET_WRITE,
    WARNING,
    ERROR,
    WRONG_REF
  )));

  @NotNull
  HighlightSeverity getSeverity(@Nullable PsiElement psiElement);

  TextAttributesKey getAttributesKey();

  class HighlightInfoTypeImpl implements HighlightInfoType, HighlightInfoType.UpdateOnTypingSuppressible {
    private final HighlightSeverity mySeverity;
    private final TextAttributesKey myAttributesKey;
    private boolean myNeedsUpdateOnTyping;

    //read external only
    HighlightInfoTypeImpl(@NotNull Element element) {
      mySeverity = new HighlightSeverity(element);
      myAttributesKey = new TextAttributesKey(element);
    }

    public HighlightInfoTypeImpl(@NotNull HighlightSeverity severity, TextAttributesKey attributesKey) {
      this(severity, attributesKey, true);
    }

    public HighlightInfoTypeImpl(@NotNull HighlightSeverity severity, TextAttributesKey attributesKey, boolean needsUpdateOnTyping) {
      mySeverity = severity;
      myAttributesKey = attributesKey;
      myNeedsUpdateOnTyping = needsUpdateOnTyping;
    }

    @Override
    @NotNull
    public HighlightSeverity getSeverity(@Nullable PsiElement psiElement) {
      return mySeverity;
    }

    @Override
    public TextAttributesKey getAttributesKey() {
      return myAttributesKey;
    }

    @Override
    @SuppressWarnings({"HardCodedStringLiteral"})
    public String toString() {
      return "HighlightInfoTypeImpl[severity=" + mySeverity + ", key=" + myAttributesKey + "]";
    }

    public void writeExternal(Element element) {
      try {
        mySeverity.writeExternal(element);
      }
      catch (WriteExternalException e) {
        throw new RuntimeException(e);
      }
      myAttributesKey.writeExternal(element);
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final HighlightInfoTypeImpl that = (HighlightInfoTypeImpl)o;

      if (!Comparing.equal(myAttributesKey, that.myAttributesKey)) return false;
      if (!mySeverity.equals(that.mySeverity)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = mySeverity.hashCode();
      result = 29 * result + myAttributesKey.hashCode();
      return result;
    }

    @Override
    public boolean needsUpdateOnTyping() {
      return myNeedsUpdateOnTyping;
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

    @Override
    @NotNull
    public HighlightSeverity getSeverity(final PsiElement psiElement) {
      InspectionProfile profile = psiElement == null
                                  ? InspectionProfileManager.getInstance().getCurrentProfile()
                                  : InspectionProjectProfileManager.getInstance(psiElement.getProject()).getCurrentProfile();
      return profile.getErrorLevel(myToolKey, psiElement).getSeverity();
    }

    @Override
    public TextAttributesKey getAttributesKey() {
      return myAttributesKey;
    }

    @Override
    @SuppressWarnings({"HardCodedStringLiteral"})
    public String toString() {
      return "HighlightInfoTypeSeverityByKey[severity=" + myToolKey + ", key=" + myAttributesKey + "]";
    }

    public HighlightDisplayKey getSeverityKey() {
      return myToolKey;
    }
  }

  interface Iconable {
    Icon getIcon();
  }

  interface UpdateOnTypingSuppressible {
    boolean needsUpdateOnTyping();
  }
}
