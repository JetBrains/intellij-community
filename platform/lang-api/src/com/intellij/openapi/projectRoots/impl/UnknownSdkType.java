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
  private static final Map<String, UnknownSdkType> ourTypeNameToInstanceMap = new HashMap<String, UnknownSdkType>();

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
