// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.daemon;

import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.javaDoc.JavaDocLocalInspection;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.psi.PsiElement;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

public class JavaAwareInspectionProfileCoverter extends InspectionProfileConvertor {
  private String myAdditionalJavadocTags;
  @NonNls private static final String ADDITONAL_JAVADOC_TAGS_OPTION = "ADDITIONAL_JAVADOC_TAGS";

  public JavaAwareInspectionProfileCoverter(InspectionProfileManager manager) {
    super(manager);
  }

  @Override
  protected boolean processElement(final Element option, final String name) {
    if (super.processElement(option, name)) {
      return true;
    }
    if (name.equals(ADDITONAL_JAVADOC_TAGS_OPTION)) {
      myAdditionalJavadocTags = option.getAttributeValue(VALUE_ATT);
      return true;
    }
    return false;
  }

  @Override
  protected void fillErrorLevels(InspectionProfileImpl profile) {
    super.fillErrorLevels(profile);

    //javadoc attributes
    InspectionToolWrapper toolWrapper = profile.getInspectionTool(JavaDocLocalInspection.SHORT_NAME, (PsiElement)null);
    JavaDocLocalInspection inspection = (JavaDocLocalInspection)toolWrapper.getTool();
    inspection.myAdditionalJavadocTags = myAdditionalJavadocTags;
  }
}