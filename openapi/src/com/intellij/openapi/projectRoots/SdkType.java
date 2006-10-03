/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.projectRoots;

import com.intellij.openapi.util.IconLoader;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

public abstract class SdkType {
  private final String myName;

  /**
   * @return path to set up filechooser to or null if not applicable
   */
  @Nullable
  public abstract String suggestHomePath();

  public abstract boolean isValidSdkHome(String path);

  @Nullable
  public abstract String getVersionString(String sdkHome);

  public abstract String suggestSdkName(String currentSdkName, String sdkHome);

  public abstract void setupSdkPaths(Sdk sdk);

  /**
   * @return Configurable object for the sdk's additional data or null if not applicable
   * @param sdkModel
   * @param sdkModificator
   */
  public abstract AdditionalDataConfigurable createAdditionalDataConfigurable(SdkModel sdkModel, SdkModificator sdkModificator);

  @Nullable
  public abstract String getBinPath(Sdk sdk);

  @Nullable
  public abstract String getToolsPath(Sdk sdk);

  @Nullable
  public abstract String getVMExecutablePath(Sdk sdk);

  @Nullable
  public abstract String getRtLibraryPath(Sdk sdk);

  public abstract void saveAdditionalData(SdkAdditionalData additionalData, Element additional);

  public abstract SdkAdditionalData loadAdditionalData(Element additional);

  public SdkType(@NonNls String name) {
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
