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
package com.intellij.openapi.projectRoots;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class SdkType {
  public static ExtensionPointName<SdkType> EP_NAME = ExtensionPointName.create("com.intellij.sdkType");

  private final String myName;

  /**
   * @return path to set up filechooser to or null if not applicable
   */
  @Nullable
  public abstract String suggestHomePath();

  /**
   * If a path selected in the file chooser is not a valid SDK home path, returns an adjusted version of the path that is again
   * checked for validity.
   * 
   * @param homePath the path selected in the file chooser.
   * @return the path to be used as the SDK home.
   */

  public String adjustSelectedSdkHome(String homePath) {
    return homePath;
  }

  public abstract boolean isValidSdkHome(String path);


  @Nullable
  public String getVersionString(Sdk sdk) {
    return getVersionString(sdk.getHomePath());
  }

  @Nullable
  public String getVersionString(String sdkHome){
    return null;
  }

  public abstract String suggestSdkName(String currentSdkName, String sdkHome);

  public void setupSdkPaths(Sdk sdk) {}

  public boolean setupSdkPaths(final Sdk sdk, final SdkModel sdkModel) {
    setupSdkPaths(sdk);
    return true;
  }

  /**
   * @return Configurable object for the sdk's additional data or null if not applicable
   * @param sdkModel
   * @param sdkModificator
   */
  public abstract AdditionalDataConfigurable createAdditionalDataConfigurable(SdkModel sdkModel, SdkModificator sdkModificator);

  public abstract void saveAdditionalData(SdkAdditionalData additionalData, Element additional);

  @Nullable
  public SdkAdditionalData loadAdditionalData(Element additional) {
    return null;
  }

  @Nullable
  public SdkAdditionalData loadAdditionalData(Sdk currentSdk, Element additional) {
    return loadAdditionalData(additional);
  }


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

  public FileChooserDescriptor getHomeChooserDescriptor() {
    final FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false) {
      public void validateSelectedFiles(VirtualFile[] files) throws Exception {
        if (files.length != 0){
          final String selectedPath = files[0].getPath();
          boolean valid = isValidSdkHome(selectedPath);
          if (!valid){
            valid = isValidSdkHome(adjustSelectedSdkHome(selectedPath));
            if (!valid) {
              String message = files[0].isDirectory()
                               ? ProjectBundle.message("sdk.configure.home.invalid.error", getPresentableName())
                               : ProjectBundle.message("sdk.configure.home.file.invalid.error", getPresentableName());
              throw new Exception(message);
            }
          }
        }
      }
    };
    descriptor.setTitle(ProjectBundle.message("sdk.configure.home.title", getPresentableName()));
    return descriptor;
  }


  public String getHomeFieldLabel() {
    return ProjectBundle.message("sdk.configure.type.home.path", getPresentableName());
  }

  public static SdkType[] getAllTypes() {
    List<SdkType> allTypes = new ArrayList<SdkType>();
    Collections.addAll(allTypes, ApplicationManager.getApplication().getComponents(SdkType.class));
    Collections.addAll(allTypes, Extensions.getExtensions(EP_NAME));
    return allTypes.toArray(new SdkType[allTypes.size()]);
  }

  public static <T extends SdkType> T findInstance(final Class<T> sdkTypeClass) {
    for (SdkType sdkType : Extensions.getExtensions(EP_NAME)) {
      if (sdkTypeClass.equals(sdkType.getClass())) {
        //noinspection unchecked
        return (T)sdkType;
      }
    }
    assert false;
    return null;
  }

  public boolean isRootTypeApplicable(final OrderRootType type) {
    return true;
  }
}
