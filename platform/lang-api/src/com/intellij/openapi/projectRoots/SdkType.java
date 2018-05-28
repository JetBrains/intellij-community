// Copyright 2000-2017 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.intellij.openapi.projectRoots;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.IconUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public abstract class SdkType implements SdkTypeId {
  public static final ExtensionPointName<SdkType> EP_NAME = ExtensionPointName.create("com.intellij.sdkType");

  private static final Comparator<Sdk> ALPHABETICAL_COMPARATOR = (sdk1, sdk2) -> StringUtil.compare(sdk1.getName(), sdk2.getName(), true);

  private final String myName;

  public SdkType(@NotNull String name) {
    myName = name;
  }

  /**
   * Returns a recommended starting path for a file chooser (where SDKs of this type are usually may be found),
   * or {@code null} if not applicable/no SDKs found.
   * <p/>
   * E.g. for Python SDK on Unix the method may return either {@code "/usr/bin"} or {@code "/usr/bin/python"}
   * (if there is only one Python interpreter installed on a host).
   */
  @Nullable
  public abstract String suggestHomePath();

  /**
   * Returns a list of all valid SDKs found on this host.
   * <p/>
   * E.g. for Python SDK on Unix the method may return {@code ["/usr/bin/python2", "/usr/bin/python3"]}.
   */
  @NotNull
  public Collection<String> suggestHomePaths() {
    String home = suggestHomePath();
    return home != null ? Collections.singletonList(home) : Collections.emptyList();
  }

  /**
   * If a path selected in the file chooser is not a valid SDK home path, returns an adjusted version of the path that is again
   * checked for validity.
   *
   * @param homePath the path selected in the file chooser.
   * @return the path to be used as the SDK home.
   */
  @NotNull
  public String adjustSelectedSdkHome(@NotNull String homePath) {
    return homePath;
  }

  public abstract boolean isValidSdkHome(String path);

  @Override
  @Nullable
  public String getVersionString(@NotNull Sdk sdk) {
    return getVersionString(sdk.getHomePath());
  }

  @Nullable
  public String getVersionString(String sdkHome) {
    return null;
  }

  public abstract String suggestSdkName(String currentSdkName, String sdkHome);

  /**
   * Returns a comparator used to order SDKs in project or module settings combo boxes.
   * When different SDK types return the same comparator instance, they are sorted together.
   */
  @NotNull
  public Comparator<Sdk> getComparator() {
    return ALPHABETICAL_COMPARATOR;
  }

  public boolean setupSdkPaths(@NotNull Sdk sdk, @NotNull SdkModel sdkModel) {
    setupSdkPaths(sdk);
    return true;
  }

  public void setupSdkPaths(@NotNull Sdk sdk) {}

  /**
   * @return Configurable object for the SDKs additional data or null if not applicable
   */
  @Nullable
  public abstract AdditionalDataConfigurable createAdditionalDataConfigurable(@NotNull SdkModel sdkModel, @NotNull SdkModificator sdkModificator);

  @Nullable
  public SdkAdditionalData loadAdditionalData(Element additional) {
    return null;
  }

  @Override
  @Nullable
  public SdkAdditionalData loadAdditionalData(@NotNull Sdk currentSdk, Element additional) {
    return loadAdditionalData(additional);
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @NotNull
  public abstract String getPresentableName();

  public Icon getIcon() {
    return null;
  }

  @NotNull
  public String getHelpTopic() {
    return "preferences.jdks";
  }

  @NotNull
  public Icon getIconForAddAction() {
    return IconUtil.getAddIcon();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SdkType)) return false;

    SdkType sdkType = (SdkType)o;

    if (!myName.equals(sdkType.myName)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myName.hashCode();
  }

  @Override
  public String toString() {
    return getName();
  }

  @NotNull
  public FileChooserDescriptor getHomeChooserDescriptor() {
    FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false) {
      @Override
      public void validateSelectedFiles(VirtualFile[] files) throws Exception {
        if (files.length != 0) {
          String selectedPath = files[0].getPath();
          boolean valid = isValidSdkHome(selectedPath);
          if (!valid) {
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

  @NotNull
  public String getHomeFieldLabel() {
    return ProjectBundle.message("sdk.configure.type.home.path", getPresentableName());
  }

  @Nullable
  public String getDefaultDocumentationUrl(@NotNull Sdk sdk) {
    return null;
  }

  @Nullable
  public String getDownloadSdkUrl() {
    return null;
  }

  @NotNull
  @SuppressWarnings("deprecation")
  public static SdkType[] getAllTypes() {
    List<SdkType> allTypes = new ArrayList<>();
    Collections.addAll(allTypes, ApplicationManager.getApplication().getComponents(SdkType.class));
    Collections.addAll(allTypes, Extensions.getExtensions(EP_NAME));
    return allTypes.toArray(new SdkType[0]);
  }

  @NotNull
  public static <T extends SdkType> T findInstance(@NotNull Class<T> sdkTypeClass) {
    for (SdkType sdkType : Extensions.getExtensions(EP_NAME)) {
      if (sdkTypeClass.equals(sdkType.getClass())) {
        return sdkTypeClass.cast(sdkType);
      }
    }
    throw new IllegalArgumentException("Unknown SDk type: " + sdkTypeClass);
  }

  /**
   * @return for sdk build over another sdk, returns type of the nested sdk, 
   *         e.g. plugins or android sdks are build over java sdk and for them the method returns {@link JavaSdkType},
   *         null otherwise
   */
  public SdkType getDependencyType() {
    return null;
  }

  public boolean isRootTypeApplicable(@NotNull OrderRootType type) {
    return true;
  }

  /**
   * If this method returns true, instead of showing the standard file path chooser when a new SDK of the type is created,
   * the {@link #showCustomCreateUI} method is called.
   *
   * @return true if the custom create UI is supported, false otherwise.
   * @since 12.0
   */
  public boolean supportsCustomCreateUI() {
    return false;
  }

  /**
   * Shows the custom SDK create UI based on selected SDK in parent component. The returned SDK needs to have the correct name and home path;
   * the framework will call setupSdkPaths() on the returned SDK.
   *
   * @param sdkModel           the list of SDKs currently displayed in the configuration dialog.
   * @param parentComponent    the parent component for showing the dialog.
   * @param selectedSdk        current selected sdk in parentComponent
   * @param sdkCreatedCallback the callback to which the created SDK is passed.
   * @since 2017.1
   */
  @SuppressWarnings("deprecation")
  public void showCustomCreateUI(@NotNull SdkModel sdkModel,
                                 @NotNull JComponent parentComponent,
                                 @Nullable Sdk selectedSdk,
                                 @NotNull Consumer<Sdk> sdkCreatedCallback) {
    showCustomCreateUI(sdkModel, parentComponent, sdkCreatedCallback);
  }

  /** @deprecated use {@link #showCustomCreateUI(SdkModel, JComponent, Sdk, Consumer)} method instead */
  @SuppressWarnings("DeprecatedIsStillUsed")
  public void showCustomCreateUI(@NotNull SdkModel sdkModel, @NotNull JComponent parentComponent, @NotNull Consumer<Sdk> sdkCreatedCallback) { }

  /**
   * Checks if the home directory of the specified SDK is valid. By default, checks that the directory points to a valid local
   * path. Can be overridden for remote SDKs.
   *
   * @param sdk the SDK to validate the path for.
   * @return true if the home path is valid, false otherwise.
   * @since 12.1
   */
  public boolean sdkHasValidPath(@NotNull Sdk sdk) {
    VirtualFile homeDir = sdk.getHomeDirectory();
    return homeDir != null && homeDir.isValid();
  }

  public String sdkPath(@NotNull VirtualFile homePath) {
    return homePath.getPath();
  }
}