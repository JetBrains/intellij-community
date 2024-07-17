// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.DeprecationUtil;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Set;

import static org.jetbrains.annotations.Nls.Capitalization.Sentence;

public interface HighlightInfoType {
  @NonNls String UNUSED_SYMBOL_SHORT_NAME = "unused";

  HighlightInfoType ERROR = new HighlightInfoTypeImpl(HighlightSeverity.ERROR, CodeInsightColors.ERRORS_ATTRIBUTES);
  HighlightInfoType WARNING = new HighlightInfoTypeImpl(HighlightSeverity.WARNING, CodeInsightColors.WARNINGS_ATTRIBUTES);
  /** @deprecated use {@link #WEAK_WARNING} instead */
  @Deprecated HighlightInfoType INFO = new HighlightInfoTypeImpl(HighlightSeverity.INFO, CodeInsightColors.INFO_ATTRIBUTES);
  HighlightInfoType WEAK_WARNING = new HighlightInfoTypeImpl(HighlightSeverity.WEAK_WARNING, CodeInsightColors.WEAK_WARNING_ATTRIBUTES);
  HighlightInfoType INFORMATION = new HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, CodeInsightColors.INFORMATION_ATTRIBUTES);
  /**
   * An unresolved reference, which might be not resolved because the project is not completely loaded yet.
   * Used in {@linkplain com.intellij.openapi.project.IncompleteDependenciesService incomplete dependencies} mode only.
   */
  HighlightInfoType PENDING_REFERENCE = new HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, CodeInsightColors.INFORMATION_ATTRIBUTES);
  HighlightInfoType TEXT_ATTRIBUTES = new HighlightInfoTypeImpl(HighlightSeverity.TEXT_ATTRIBUTES, CodeInsightColors.CONSIDERATION_ATTRIBUTES);

  HighlightInfoType WRONG_REF = new HighlightInfoTypeImpl(HighlightSeverity.ERROR, CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES);

  HighlightInfoType GENERIC_WARNINGS_OR_ERRORS_FROM_SERVER = new HighlightInfoTypeImpl(HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING, CodeInsightColors.GENERIC_SERVER_ERROR_OR_WARNING);

  HighlightInfoType UNUSED_SYMBOL = new HighlightInfoTypeSeverityByKey(
    HighlightDisplayKey.findOrRegister(UNUSED_SYMBOL_SHORT_NAME, getUnusedSymbolDisplayName(), UNUSED_SYMBOL_SHORT_NAME),
    CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES);

  HighlightInfoType DEPRECATED = new HighlightInfoTypeSeverityByKey(
    HighlightDisplayKey.findOrRegister(
      DeprecationUtil.DEPRECATION_SHORT_NAME, DeprecationUtil.getDeprecationDisplayName(), DeprecationUtil.DEPRECATION_ID),
    CodeInsightColors.DEPRECATED_ATTRIBUTES);

  HighlightInfoType MARKED_FOR_REMOVAL = new HighlightInfoTypeSeverityByKey(
    HighlightDisplayKey.findOrRegister(
      DeprecationUtil.FOR_REMOVAL_SHORT_NAME, DeprecationUtil.getForRemovalDisplayName(), DeprecationUtil.FOR_REMOVAL_ID),
    CodeInsightColors.MARKED_FOR_REMOVAL_ATTRIBUTES);

  HighlightSeverity SYMBOL_TYPE_SEVERITY = new HighlightSeverity("SYMBOL_TYPE_SEVERITY", HighlightSeverity.INFORMATION.myVal-2);

  HighlightInfoType TODO = new HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, CodeInsightColors.TODO_DEFAULT_ATTRIBUTES);  // these are default attributes, can be configured differently for specific patterns
  HighlightInfoType UNHANDLED_EXCEPTION = new HighlightInfoTypeImpl(HighlightSeverity.ERROR, CodeInsightColors.ERRORS_ATTRIBUTES);

  HighlightSeverity INJECTED_FRAGMENT_SYNTAX_SEVERITY = new HighlightSeverity("INJECTED_FRAGMENT_SYNTAX", SYMBOL_TYPE_SEVERITY.myVal - 2);
  HighlightSeverity INJECTED_FRAGMENT_SEVERITY = new HighlightSeverity("INJECTED_FRAGMENT", SYMBOL_TYPE_SEVERITY.myVal - 1);
  HighlightInfoType INJECTED_LANGUAGE_FRAGMENT = new HighlightInfoTypeImpl(INJECTED_FRAGMENT_SYNTAX_SEVERITY, CodeInsightColors.INFORMATION_ATTRIBUTES);
  HighlightInfoType INJECTED_LANGUAGE_BACKGROUND = new HighlightInfoTypeImpl(INJECTED_FRAGMENT_SEVERITY, CodeInsightColors.INFORMATION_ATTRIBUTES);
  
  HighlightInfoType POSSIBLE_PROBLEM = new HighlightInfoTypeImpl(SYMBOL_TYPE_SEVERITY, HighlighterColors.NO_HIGHLIGHTING);

  HighlightSeverity ELEMENT_UNDER_CARET_SEVERITY = new HighlightSeverity("ELEMENT_UNDER_CARET", HighlightSeverity.ERROR.myVal + 1);
  HighlightInfoType ELEMENT_UNDER_CARET_READ = new HighlightInfoType.HighlightInfoTypeImpl(ELEMENT_UNDER_CARET_SEVERITY, EditorColors.IDENTIFIER_UNDER_CARET_ATTRIBUTES);
  HighlightInfoType ELEMENT_UNDER_CARET_WRITE = new HighlightInfoType.HighlightInfoTypeImpl(ELEMENT_UNDER_CARET_SEVERITY, EditorColors.WRITE_IDENTIFIER_UNDER_CARET_ATTRIBUTES);
  HighlightInfoType ELEMENT_UNDER_CARET_STRUCTURAL =
    new HighlightInfoType.HighlightInfoTypeImpl(ELEMENT_UNDER_CARET_SEVERITY, CodeInsightColors.MATCHED_BRACE_ATTRIBUTES);

  HighlightSeverity HIGHLIGHTED_REFERENCE_SEVERITY = new HighlightSeverity("HIGHLIGHTED_REFERENCE", SYMBOL_TYPE_SEVERITY.myVal - 1);

  /**
   * @see com.intellij.openapi.editor.impl.RangeHighlighterImpl#VISIBLE_IF_FOLDED
   */
  Set<HighlightInfoType> VISIBLE_IF_FOLDED = Set.of(
    ELEMENT_UNDER_CARET_READ, 
    ELEMENT_UNDER_CARET_WRITE,
    WARNING,
    ERROR,
    WRONG_REF
  );

  @NotNull
  HighlightSeverity getSeverity(@Nullable PsiElement psiElement);

  @NotNull
  TextAttributesKey getAttributesKey();

  default boolean isInspectionHighlightInfoType() {
    return false;
  }

  class HighlightInfoTypeImpl implements HighlightInfoType, HighlightInfoType.UpdateOnTypingSuppressible {
    private final HighlightSeverity mySeverity;
    private final TextAttributesKey attributeKey;
    private final boolean needUpdateOnTyping;

    //read external only
    HighlightInfoTypeImpl(@NotNull Element element) {
      mySeverity = new HighlightSeverity(element);
      attributeKey = new TextAttributesKey(element);
      needUpdateOnTyping = false;
    }

    public HighlightInfoTypeImpl(@NotNull HighlightSeverity severity, @NotNull TextAttributesKey attributesKey) {
      this(severity, attributesKey, true);
    }

    public HighlightInfoTypeImpl(@NotNull HighlightSeverity severity, @NotNull TextAttributesKey attributesKey, boolean needsUpdateOnTyping) {
      mySeverity = severity;
      attributeKey = attributesKey;
      needUpdateOnTyping = needsUpdateOnTyping;
    }

    /** Whether the corresponding severity should be available for choosing and editing in inspection settings */
    public boolean isApplicableToInspections() {
      return true;
    }

    @Override
    public @NotNull HighlightSeverity getSeverity(@Nullable PsiElement psiElement) {
      return mySeverity;
    }

    @Override
    public @NotNull TextAttributesKey getAttributesKey() {
      return attributeKey;
    }

    @Override
    public String toString() {
      return "HighlightInfoTypeImpl[severity=" + mySeverity + ", key=" + attributeKey + "]";
    }

    public void writeExternal(Element element) {
      try {
        mySeverity.writeExternal(element);
      }
      catch (WriteExternalException e) {
        throw new RuntimeException(e);
      }
      attributeKey.writeExternal(element);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof HighlightInfoTypeImpl that)) return false;

      if (!Comparing.equal(attributeKey, that.attributeKey)) return false;
      if (!mySeverity.equals(that.mySeverity)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = mySeverity.hashCode();
      result = 29 * result + attributeKey.hashCode();
      return result;
    }

    @Override
    public boolean needsUpdateOnTyping() {
      return needUpdateOnTyping;
    }
  }

  final class HighlightInfoTypeSeverityByKey implements HighlightInfoType {
    private final TextAttributesKey attributeKey;
    private final HighlightDisplayKey myToolKey;

    public HighlightInfoTypeSeverityByKey(@NotNull HighlightDisplayKey severityKey, @NotNull TextAttributesKey attributesKey) {
      myToolKey = severityKey;
      attributeKey = attributesKey;
    }

    @Override
    public @NotNull HighlightSeverity getSeverity(PsiElement psiElement) {
      InspectionProfile profile = psiElement == null
                                  ? InspectionProfileManager.getInstance().getCurrentProfile()
                                  : InspectionProjectProfileManager.getInstance(psiElement.getProject()).getCurrentProfile();
      return profile.getErrorLevel(myToolKey, psiElement).getSeverity();
    }

    @Override
    public @NotNull TextAttributesKey getAttributesKey() {
      return attributeKey;
    }

    @Override
    public String toString() {
      return "HighlightInfoTypeSeverityByKey[severity=" + myToolKey + ", key=" + attributeKey + "]";
    }

    public HighlightDisplayKey getSeverityKey() {
      return myToolKey;
    }
  }

  @FunctionalInterface
  interface Iconable {
    @NotNull
    Icon getIcon();
  }

  @FunctionalInterface
  interface UpdateOnTypingSuppressible {
    boolean needsUpdateOnTyping();
  }

  static @Nls(capitalization = Sentence) String getUnusedSymbolDisplayName() {
    return AnalysisBundle.message("inspection.dead.code.display.name");
  }
}
