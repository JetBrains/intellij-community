/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.util.IconLoader;
import org.jdom.Element;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Used as a plug for all SDKs which type cannot be determined (for example, plugin that registered a custom type has been deinstalled)
 * @author Eugene Zhuravlev
 *         Date: Dec 11, 2004
 */
public class UnknownSdkType extends SdkType{
  public static final Icon ICON = IconLoader.getIcon("/nodes/unknownJdkClosed.png");
  private static final Icon JDK_ICON_EXPANDED = IconLoader.getIcon("/nodes/unknownJdkOpen.png");
  private static Map<String, UnknownSdkType> ourTypeNameToInstanceMap = new HashMap<String, UnknownSdkType>();

  /**
   * @param typeName the name of the SDK type that this SDK serves as a plug for
   */
  private UnknownSdkType(String typeName) {
    super(typeName);
  }

  public static UnknownSdkType getInstance(String typeName) {
    UnknownSdkType instance = ourTypeNameToInstanceMap.get(typeName);
    if (instance == null) {
      instance = new UnknownSdkType(typeName);
      ourTypeNameToInstanceMap.put(typeName, instance);
    }                                                                  
    return instance;
  }

  public String suggestHomePath() {
    return null;
  }

  public boolean isValidSdkHome(String path) {
    return false;
  }

  public String getVersionString(String sdkHome) {
    return "";
  }

  public String suggestSdkName(String currentSdkName, String sdkHome) {
    return currentSdkName;
  }

  public void setupSdkPaths(Sdk sdk) {
  }

  public AdditionalDataConfigurable createAdditionalDataConfigurable(SdkModel sdkModel, SdkModificator sdkModificator) {
    return null;
  }

  public String getBinPath(Sdk sdk) {
    return null;
  }

  public String getToolsPath(Sdk sdk) {
    return null;
  }

  public String getVMExecutablePath(Sdk sdk) {
    return null;
  }

  public void saveAdditionalData(SdkAdditionalData additionalData, Element additional) {
  }

  public SdkAdditionalData loadAdditionalData(Element additional) {
    return null;
  }

  public String getPresentableName() {
    return ProjectBundle.message("sdk.unknown.name");
  }

  public Icon getIcon() {
    return ICON;
  }

  public Icon getIconForExpandedTreeNode() {
    return JDK_ICON_EXPANDED;
  }
}
