// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.xmlb.annotations.Attribute;

/**
 * @author Dmitry Avdeev
 */
public class LocalInspectionEP extends InspectionEP implements LocalInspectionTool.LocalDefaultNameProvider {

  public static final ExtensionPointName<LocalInspectionEP> LOCAL_INSPECTION = ExtensionPointName.create("com.intellij.localInspection");

  /**
   * @see InspectionProfileEntry#getSuppressId()
   */
  @Attribute("suppressId")
  public String id;

  /**
   * @see InspectionProfileEntry#getAlternativeID()
   */
  @Attribute("alternativeId")
  public String alternativeId;

  /**
   * @see LocalInspectionTool#runForWholeFile()
   */
  @Attribute("runForWholeFile")
  public boolean runForWholeFile;

  /**
   * @see com.intellij.codeInspection.ex.UnfairLocalInspectionTool
   */
  @Attribute("unfair")
  public boolean unfair;

  @Override
  public String getDefaultID() {
    return id;
  }

  @Override
  public String getDefaultAlternativeID() {
    return alternativeId;
  }
}
