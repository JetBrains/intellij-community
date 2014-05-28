/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 21-Feb-2008
 */
package com.intellij.codeInsight.daemon;

import com.intellij.codeInspection.ModifiableModel;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.javaDoc.JavaDocLocalInspectionBase;
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
    final InspectionToolWrapper toolWrapper = profile.getInspectionTool(JavaDocLocalInspectionBase.SHORT_NAME, null);
    JavaDocLocalInspectionBase inspection = (JavaDocLocalInspectionBase)toolWrapper.getTool();
    inspection.myAdditionalJavadocTags = myAdditionalJavadocTags;
  }
}