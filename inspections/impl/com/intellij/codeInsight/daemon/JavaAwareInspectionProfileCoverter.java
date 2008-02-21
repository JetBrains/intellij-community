/*
 * User: anna
 * Date: 21-Feb-2008
 */
package com.intellij.codeInsight.daemon;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.ModifiableModel;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.javaDoc.JavaDocLocalInspection;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

public class JavaAwareInspectionProfileCoverter extends InspectionProfileConvertor{
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
  protected void fillErrorLevels(final ModifiableModel profile) {
    super.fillErrorLevels(profile);

    //javadoc attributes
    final InspectionProfileEntry inspectionTool = profile.getInspectionTool(JavaDocLocalInspection.SHORT_NAME);
    JavaDocLocalInspection inspection = (JavaDocLocalInspection)((LocalInspectionToolWrapper)inspectionTool).getTool();
    inspection.myAdditionalJavadocTags = myAdditionalJavadocTags;
  }
}