/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.projectRoots;

import org.jdom.Element;

import javax.swing.*;

import com.intellij.openapi.util.IconLoader;

public abstract class SdkType {
  private final String myName;

  public abstract boolean isValidSdkHome(String path);

  public abstract String getVersionString(String sdkHome);

  public abstract String suggestSdkName(String currentSdkName, String sdkHome);

  public abstract void setupSdkPaths(Sdk sdk);

  /**
   * @return Configurable object for the sdk's additional data or null if not applicable
   * @param sdkModel
   * @param sdkModificator
   */
  public abstract AdditionalDataConfigurable createAdditionalDataConfigurable(SdkModel sdkModel, SdkModificator sdkModificator);

  public abstract String getBinPath(Sdk sdk);

  public abstract String getToolsPath(Sdk sdk);

  public abstract String getVMExecutablePath(Sdk sdk);

  public abstract String getRtLibraryPath(Sdk sdk);

  public abstract void saveAdditionalData(SdkAdditionalData additionalData, Element additional);

  public abstract SdkAdditionalData loadAdditionalData(Element additional);

  public SdkType(String name) {
    myName = name;
  }

  public String getName() {
    return myName;
  }

  public abstract String getPresentableName();

  public Icon getIcon() {
    return null;
  }

  public Icon getIconForExpandedTreeNode() {
    return getIcon();
  }

  public Icon getIconForAddAction() {
    return IconLoader.getIcon("/general/add.png");
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SdkType)) return false;

    final SdkType sdkType = (SdkType)o;

    if (!myName.equals(sdkType.myName)) return false;

    return true;
  }

  public int hashCode() {
    return myName.hashCode();
  }

  public String toString() {
    return getName();
  }

}
