/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.PathChooserDialog;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.NullableConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author yole
 */
public class SdkConfigurationUtil {
  private SdkConfigurationUtil() {
  }

  public static void createSdk(@Nullable final Project project,
                               final Sdk[] existingSdks,
                               final NullableConsumer<Sdk> onSdkCreatedCallBack,
                               final SdkType... sdkTypes) {
    if (sdkTypes.length == 0) {
      onSdkCreatedCallBack.consume(null);
      return;
    }
    final FileChooserDescriptor descriptor = createCompositeDescriptor(sdkTypes);
    if (SystemInfo.isMac) {
      descriptor.putUserData(PathChooserDialog.NATIVE_MAC_CHOOSER_SHOW_HIDDEN_FILES, Boolean.TRUE);
    }
    String suggestedPath = sdkTypes[0].suggestHomePath();
    VirtualFile suggestedDir = suggestedPath == null
                               ? null
                               : LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(suggestedPath));
    FileChooser.chooseFiles(descriptor, project, suggestedDir, new FileChooser.FileChooserConsumer() {
      @Override
      public void consume(List<VirtualFile> selectedFiles) {
        for (SdkType sdkType : sdkTypes) {
          if (sdkType.isValidSdkHome(selectedFiles.get(0).getPath())) {
            onSdkCreatedCallBack.consume(setupSdk(existingSdks, selectedFiles.get(0), sdkType, false, null, null));
            return;
          }
        }
        onSdkCreatedCallBack.consume(null);
      }

      @Override
      public void cancelled() {
        onSdkCreatedCallBack.consume(null);
      }
    });
  }

  private static FileChooserDescriptor createCompositeDescriptor(final SdkType... sdkTypes) {
    FileChooserDescriptor descriptor0 = sdkTypes[0].getHomeChooserDescriptor();
    FileChooserDescriptor descriptor = new FileChooserDescriptor(descriptor0.isChooseFiles(), descriptor0.isChooseFolders(),
                                                                 descriptor0.isChooseJars(), descriptor0.isChooseJarsAsFiles(),
                                                                 descriptor0.isChooseJarContents(), descriptor0.isChooseMultiple()) {

      @Override
      public void validateSelectedFiles(final VirtualFile[] files) throws Exception {
        if (files.length > 0) {
          for (SdkType type : sdkTypes) {
            if (type.isValidSdkHome(files[0].getPath())) {
              return;
            }
          }
        }
        String message = files.length > 0 && files[0].isDirectory()
                         ? ProjectBundle.message("sdk.configure.home.invalid.error", sdkTypes[0].getPresentableName())
                         : ProjectBundle.message("sdk.configure.home.file.invalid.error", sdkTypes[0].getPresentableName());
        throw new Exception(message);
      }
    };
    descriptor.setTitle(descriptor0.getTitle());
    return descriptor;
  }

  public static void addSdk(@NotNull final Sdk sdk) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        ProjectJdkTable.getInstance().addJdk(sdk);
      }
    });
  }

  public static void removeSdk(final Sdk sdk) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        ProjectJdkTable.getInstance().removeJdk(sdk);
      }
    });
  }

  @Nullable
  public static Sdk setupSdk(final Sdk[] allSdks,
                             final VirtualFile homeDir, final SdkType sdkType, final boolean silent,
                             @Nullable final SdkAdditionalData additionalData,
                             @Nullable final String customSdkSuggestedName) {
    final List<Sdk> sdksList = Arrays.asList(allSdks);

    final ProjectJdkImpl sdk;
    try {
      String sdkPath = sdkType.sdkPath(homeDir);
      
      final String sdkName = customSdkSuggestedName == null
                             ? createUniqueSdkName(sdkType, sdkPath, sdksList)
                             : createUniqueSdkName(customSdkSuggestedName, sdksList);
      sdk = new ProjectJdkImpl(sdkName, sdkType);

      if (additionalData != null) {
        // additional initialization.
        // E.g. some ruby sdks must be initialized before
        // setupSdkPaths() method invocation
        sdk.setSdkAdditionalData(additionalData);
      }

      sdk.setHomePath(sdkPath);
      sdkType.setupSdkPaths(sdk);
    }
    catch (Exception e) {
      if (!silent) {
        Messages.showErrorDialog("Error configuring SDK: " +
                                 e.getMessage() +
                                 ".\nPlease make sure that " +
                                 FileUtil.toSystemDependentName(homeDir.getPath()) +
                                 " is a valid home path for this SDK type.", "Error Configuring SDK");
      }
      return null;
    }
    return sdk;
  }

  public static void setDirectoryProjectSdk(@NotNull final Project project, @Nullable final Sdk sdk) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        ProjectRootManager.getInstance(project).setProjectSdk(sdk);
        final Module[] modules = ModuleManager.getInstance(project).getModules();
        if (modules.length > 0) {
          ModuleRootModificationUtil.setSdkInherited(modules[0]);
        }
      }
    });
  }

  public static void configureDirectoryProjectSdk(final Project project,
                                                  @Nullable Comparator<Sdk> preferredSdkComparator,
                                                  final SdkType... sdkTypes) {
    Sdk existingSdk = ProjectRootManager.getInstance(project).getProjectSdk();
    if (existingSdk != null && ArrayUtil.contains(existingSdk.getSdkType(), sdkTypes)) {
      return;
    }

    Sdk sdk = findOrCreateSdk(preferredSdkComparator, sdkTypes);
    if (sdk != null) {
      setDirectoryProjectSdk(project, sdk);
    }
  }

  @Nullable
  public static Sdk findOrCreateSdk(@Nullable Comparator<Sdk> comparator, final SdkType... sdkTypes) {
    final Project defaultProject = ProjectManager.getInstance().getDefaultProject();
    final Sdk sdk = ProjectRootManager.getInstance(defaultProject).getProjectSdk();
    if (sdk != null) {
      for (SdkType type : sdkTypes) {
        if (sdk.getSdkType() == type) {
          return sdk;
        }
      }
    }
    for (SdkType type : sdkTypes) {
      List<Sdk> sdks = ProjectJdkTable.getInstance().getSdksOfType(type);
      if (!sdks.isEmpty()) {
        if (comparator != null) {
          Collections.sort(sdks, comparator);
        }
        return sdks.get(0);
      }
    }
    for (SdkType sdkType : sdkTypes) {
      final String suggestedHomePath = sdkType.suggestHomePath();
      if (suggestedHomePath != null && sdkType.isValidSdkHome(suggestedHomePath)) {
        Sdk an_sdk = createAndAddSDK(suggestedHomePath, sdkType);
        if (an_sdk != null) return an_sdk;
      }
    }
    return null;
  }

  /**
   * Tries to create an SDK identified by path; if successful, add the SDK to the global SDK table.
   *
   * @param path    identifies the SDK
   * @param sdkType
   * @return newly created SDK, or null.
   */
  @Nullable
  public static Sdk createAndAddSDK(final String path, SdkType sdkType) {
    VirtualFile sdkHome = ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
      @Override
      public VirtualFile compute() {
        return LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
      }
    });
    if (sdkHome != null) {
      final Sdk newSdk = setupSdk(ProjectJdkTable.getInstance().getAllJdks(), sdkHome, sdkType, true, null, null);
      if (newSdk != null) {
        addSdk(newSdk);
      }
      return newSdk;
    }
    return null;
  }

  public static String createUniqueSdkName(SdkType type, String home, final Collection<Sdk> sdks) {
    return createUniqueSdkName(type.suggestSdkName(null, home), sdks);
  }

  public static String createUniqueSdkName(final String suggestedName, final Collection<Sdk> sdks) {
    final Set<String> names = new HashSet<String>();
    for (Sdk jdk : sdks) {
      names.add(jdk.getName());
    }
    String newSdkName = suggestedName;
    int i = 0;
    while (names.contains(newSdkName)) {
      newSdkName = suggestedName + " (" + (++i) + ")";
    }
    return newSdkName;
  }

  public static void selectSdkHome(final SdkType sdkType, @NotNull final Consumer<String> consumer) {
    final FileChooserDescriptor descriptor = sdkType.getHomeChooserDescriptor();
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      Sdk sdk = ProjectJdkTable.getInstance().findMostRecentSdkOfType(sdkType);
      if (sdk == null) throw new RuntimeException("No SDK of type " + sdkType + " found");
      consumer.consume(sdk.getHomePath());
      return;
    }
    FileChooser.chooseFiles(descriptor, null, getSuggestedSdkRoot(sdkType), new Consumer<List<VirtualFile>>() {
      @Override
      public void consume(final List<VirtualFile> chosen) {
        final String path = chosen.get(0).getPath();
        if (sdkType.isValidSdkHome(path)) {
          consumer.consume(path);
          return;
        }

        final String adjustedPath = sdkType.adjustSelectedSdkHome(path);
        if (sdkType.isValidSdkHome(adjustedPath)) {
          consumer.consume(adjustedPath);
        }
      }
    });
  }

  @Nullable
  public static VirtualFile getSuggestedSdkRoot(SdkType sdkType) {
    final String homepath = sdkType.suggestHomePath();
    if (homepath == null) return null;
    return LocalFileSystem.getInstance().findFileByPath(homepath);
  }

  public static List<String> filterExistingPaths(SdkType sdkType, Collection<String> sdkHomes, final Sdk[] sdks) {
    List<String> result = new ArrayList<String>();
    for (String sdkHome : sdkHomes) {
      if (findByPath(sdkType, sdks, sdkHome) == null) {
        result.add(sdkHome);
      }
    }
    return result;
  }

  @Nullable
  private static Sdk findByPath(SdkType sdkType, Sdk[] sdks, String sdkHome) {
    for (Sdk sdk : sdks) {
      if (sdk.getSdkType() == sdkType &&
          FileUtil.pathsEqual(FileUtil.toSystemIndependentName(sdk.getHomePath()), FileUtil.toSystemIndependentName(sdkHome))) {
        return sdk;
      }
    }
    return null;
  }
}
