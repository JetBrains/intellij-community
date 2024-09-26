// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;


/**
 * Inherit from this class and register implementation as {@code sdkType} extension in plugin.xml to provide a custom type of
 * <a href="https://www.jetbrains.com/help/idea/sdk.html">SDK</a>. Users can create and assign SDKs to modules in Project Structure dialog.
 * You may use {@link ProjectJdkTable} to add or remove SDK in code, {@link com.intellij.openapi.roots.ModifiableRootModel#setSdk} to assign
 * SDK to a specific module, and {@link com.intellij.openapi.roots.ProjectRootManager#setProjectSdk} to use it as the default SDK in the project.
 */
public abstract class SdkType implements SdkTypeId {
  public static final ExtensionPointName<SdkType> EP_NAME = new ExtensionPointName<>("com.intellij.sdkType");

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
   * <p/>
   * This method should work fast and allow running from the EDT thread. See the {@link #suggestHomePaths()}
   * for more advanced scenarios
   * @see #suggestHomePaths()
   */
  public abstract @Nullable String suggestHomePath();

  /**
   * Returns a list of all valid SDKs found on this host.
   * <p/>
   * E.g. for Python SDK on Unix the method may return {@code ["/usr/bin/python2", "/usr/bin/python3"]}.
   * <p/>
   * This method may take significant time to execute. The implementation may check {@link ProgressManager#checkCanceled()}
   * for possible interruption request. It is not recommended to call this method from a ETD thread. See
   * an alternative {@link #suggestHomePath()} method for EDT-friendly calls.
   * @see #suggestHomePath()
   *
   * @deprecated Use {@link #suggestHomePaths(Project)}
   */
  @Deprecated
  public @NotNull Collection<String> suggestHomePaths() {
    String home = suggestHomePath();
    return ContainerUtil.createMaybeSingletonList(home);
  }

  /**
   * Returns a list of all valid SDKs found on the host where {@code project} is located.
   * <p/>
   * E.g. for Python SDK on Unix the method may return {@code ["/usr/bin/python2", "/usr/bin/python3"]}.
   * <p/>
   * This method may take significant time to execute. The implementation may check {@link ProgressManager#checkCanceled()}
   * for possible interruption request. It is not recommended to call this method from a ETD thread. See
   * an alternative {@link #suggestHomePath()} method for EDT-friendly calls.
   */
  public @NotNull Collection<String> suggestHomePaths(@Nullable Project project) {
    return suggestHomePaths();
  }

  /**
   * This method is used to decide if a given {@link VirtualFile} has something in common
   * with this {@link SdkType}.
   * <p>
   * For example, it can be used by the IDE to decide showing SDK related editor notifications or quick fixes
   */
  public boolean isRelevantForFile(@NotNull Project project, @NotNull VirtualFile file) {
    return true;
  }

  /**
   * If a path selected in the file chooser is not a valid SDK home path, returns an adjusted version of the path that is again
   * checked for validity.
   *
   * @param homePath the path selected in the file chooser.
   * @return the path to be used as the SDK home.
   */
  public @NotNull String adjustSelectedSdkHome(@NotNull String homePath) {
    return homePath;
  }

  public abstract boolean isValidSdkHome(@NotNull String path);

  /**
   * Returns the message to be shown to the user when {@link #isValidSdkHome(String)} returned false for the path.
   */
  public String getInvalidHomeMessage(@NotNull String path) {
    return new File(path).isDirectory()
      ? ProjectBundle.message("sdk.configure.home.invalid.error", getPresentableName())
      : ProjectBundle.message("sdk.configure.home.file.invalid.error", getPresentableName());
  }

  @Override
  public @Nullable String getVersionString(@NotNull Sdk sdk) {
    String homePath = sdk.getHomePath();
    return homePath == null ? null : getVersionString(homePath);
  }

  public @Nullable String getVersionString(@NotNull String sdkHome) {
    return null;
  }

  public abstract @NotNull String suggestSdkName(@Nullable String currentSdkName, @NotNull String sdkHome);

  /**
   * Returns a comparator used to order SDKs in project or module settings combo boxes.
   * When different SDK types return the same comparator instance, they are sorted together.
   */
  public @NotNull Comparator<Sdk> getComparator() {
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
  public abstract @Nullable AdditionalDataConfigurable createAdditionalDataConfigurable(@NotNull SdkModel sdkModel, @NotNull SdkModificator sdkModificator);

  public @Nullable SdkAdditionalData loadAdditionalData(@NotNull Element additional) {
    return null;
  }

  @Override
  public @Nullable SdkAdditionalData loadAdditionalData(@NotNull Sdk currentSdk, @NotNull Element additional) {
    return loadAdditionalData(additional);
  }

  @Override
  public @NotNull String getName() {
    return myName;
  }

  public abstract @NotNull @Nls(capitalization = Nls.Capitalization.Title) String getPresentableName();

  public Icon getIcon() {
    return null;
  }

  public @NotNull String getHelpTopic() {
    return "preferences.jdks";
  }

  /**
   * @deprecated {@link #getIcon} is also used for add actions.
   */
  @Deprecated
  public Icon getIconForAddAction() {
    return getIcon();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SdkType sdkType)) return false;

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

  public @NotNull FileChooserDescriptor getHomeChooserDescriptor() {
    FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false) {
      @Override
      public void validateSelectedFiles(VirtualFile @NotNull [] files) throws Exception {
        if (files.length != 0) {
          String selectedPath = files[0].getPath();
          boolean valid = isValidSdkHome(selectedPath);
          if (!valid) {
            valid = isValidSdkHome(adjustSelectedSdkHome(selectedPath));
            if (!valid) {
              String message = getInvalidHomeMessage(selectedPath);
              throw new Exception(message);
            }
          }
        }
      }
    };
    descriptor.setTitle(ProjectBundle.message("sdk.configure.home.title", getPresentableName()));
    return descriptor;
  }

  public @NotNull @NlsContexts.Label String getHomeFieldLabel() {
    return ProjectBundle.message("sdk.configure.type.home.path", getPresentableName());
  }

  public @Nullable String getDefaultDocumentationUrl(@NotNull Sdk sdk) {
    return null;
  }

  public @Nullable String getDownloadSdkUrl() {
    return null;
  }

  /**
   * @deprecated Please use {@link #getAllTypeList()}
   */
  @Deprecated
  public static SdkType @NotNull [] getAllTypes() {
    return EP_NAME.getExtensions();
  }

  public static @NotNull List<SdkType> getAllTypeList() {
    return EP_NAME.getExtensionList();
  }

  public static @Nullable SdkType findByName(@Nullable String sdkName) {
    if (sdkName == null) return null;

    for (SdkType sdkType : EP_NAME.getExtensionList()) {
      if (Comparing.strEqual(sdkType.getName(), sdkName)) {
        return sdkType;
      }
    }
    return null;
  }

  public static @NotNull <T extends SdkType> T findInstance(@NotNull Class<T> sdkTypeClass) {
    for (SdkType sdkType : EP_NAME.getExtensionList()) {
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
   * @implSpec method's implementations should not add sdk to the jdkTable neither invoke {@link SdkType#setupSdkPaths}. Only create and
   * and pass to the callback. The rest is done by {@link com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel#setupSdk(Sdk, Consumer)}
   *
   * @see #supportsCustomCreateUI()
   */
  public void showCustomCreateUI(@NotNull SdkModel sdkModel,
                                 @NotNull JComponent parentComponent,
                                 @Nullable Sdk selectedSdk,
                                 @NotNull Consumer<? super Sdk> sdkCreatedCallback) {
  }

  /**
   * Checks if the home directory of the specified SDK is valid. By default, checks that the directory points to a valid local
   * path. Can be overridden for remote SDKs.
   *
   * @param sdk the SDK to validate the path for.
   * @return true if the home path is valid, false otherwise.
   */
  public boolean sdkHasValidPath(@NotNull Sdk sdk) {
    VirtualFile homeDir = sdk.getHomeDirectory();
    return homeDir != null && homeDir.isValid();
  }

  public @NotNull String sdkPath(@NotNull VirtualFile homePath) {
    return homePath.getPath();
  }

  /**
   * If this method returns false, this SDK type will not be shown in the SDK type chooser popup when the user
   * creates a new SDK.
   */
  public boolean allowCreationByUser() {
    return true;
  }
}
