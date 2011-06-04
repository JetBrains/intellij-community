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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.MacFileChooserDialog;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author yole
 */
public class SdkConfigurationUtil {
  private SdkConfigurationUtil() {
  }

  public static void createSdk(final Project project, final Sdk[] existingSdks,
                               final Consumer<Sdk> onSdkCreatedCallBack,
                               final SdkType... sdkTypes) {
    if (sdkTypes.length == 0) {
      onSdkCreatedCallBack.consume(null);
      return;
    }
    final FileChooserDescriptor descriptor = createCompositeDescriptor(sdkTypes);
    if (SystemInfo.isMac) {
      descriptor.putUserData(MacFileChooserDialog.NATIVE_MAC_FILE_CHOOSER_SHOW_HIDDEN_FILES_ENABLED, Boolean.TRUE);
    }
    String suggestedPath = sdkTypes [0].suggestHomePath();
    VirtualFile suggestedDir = suggestedPath == null
                               ? null
                               :  LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(suggestedPath));
    FileChooser.chooseFilesWithSlideEffect(descriptor, project, suggestedDir, new Consumer<VirtualFile[]>() {
      @Override
      public void consume(VirtualFile[] selectedFiles) {
        if (selectedFiles.length > 0) {
          for (SdkType sdkType : sdkTypes) {
            if (sdkType.isValidSdkHome(selectedFiles[0].getPath())) {
              onSdkCreatedCallBack.consume(setupSdk(existingSdks, selectedFiles[0], sdkType, false, null, null));
              return;
            }
          }
        }
        onSdkCreatedCallBack.consume(null);
      }
    });
  }

  private static FileChooserDescriptor createCompositeDescriptor(final SdkType... sdkTypes) {
    FileChooserDescriptor descriptor0 = sdkTypes [0].getHomeChooserDescriptor();
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
                         ? ProjectBundle.message("sdk.configure.home.invalid.error", sdkTypes [0].getPresentableName())
                         : ProjectBundle.message("sdk.configure.home.file.invalid.error", sdkTypes [0].getPresentableName());
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
      final String sdkName = customSdkSuggestedName == null
                             ? createUniqueSdkName(sdkType, homeDir.getPath(), sdksList)
                             : createUniqueSdkName(customSdkSuggestedName, sdksList);
      sdk = new ProjectJdkImpl(sdkName, sdkType);

      if (additionalData != null) {
        // additional initialization.
        // E.g. some ruby sdks must be initialized before
        // setupSdkPaths() method invocation
        sdk.setSdkAdditionalData(additionalData);
      }

      sdk.setHomePath(homeDir.getPath());
      sdkType.setupSdkPaths(sdk);
    }
    catch (Exception e) {
      if (!silent) {
        Messages.showErrorDialog("Error configuring SDK: " +
                                 e.getMessage() +
                                 ".\nPlease make sure that " +
                                 FileUtil.toSystemDependentName(homeDir.getPath()) +
                                 " is a valid home path for this SDK type.", "Error configuring SDK");
      }
      return null;
    }
    return sdk;
  }

  public static void setDirectoryProjectSdk(@NotNull final Project project, final Sdk sdk) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        ProjectRootManager.getInstance(project).setProjectSdk(sdk);
        final Module[] modules = ModuleManager.getInstance(project).getModules();
        if (modules.length > 0) {
          final ModifiableRootModel model = ModuleRootManager.getInstance(modules[0]).getModifiableModel();
          model.inheritSdk();
          model.commit();
        }
      }
    });
  }

  public static void configureDirectoryProjectSdk(final Project project, final SdkType... sdkTypes) {
    Sdk existingSdk = ProjectRootManager.getInstance(project).getProjectSdk();
    if (existingSdk != null && ArrayUtil.contains(existingSdk.getSdkType(), sdkTypes)) {
      return;
    }

    Sdk sdk = findOrCreateSdk(sdkTypes);
    if (sdk != null) {
      setDirectoryProjectSdk(project, sdk);
    }
  }

  @Nullable
  public static Sdk findOrCreateSdk(final SdkType... sdkTypes) {
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
      if (sdks.size() > 0) {
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
   * @param path identifies the SDK
   * @param sdkType
   * @return newly created SDK, or null.
   */
  @Nullable
  public static Sdk createAndAddSDK(final String path, SdkType sdkType) {
    VirtualFile sdkHome = ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
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

  public static void selectSdkHome(final SdkType sdkType, @NotNull final Consumer<String> consumer){
    final FileChooserDescriptor descriptor = sdkType.getHomeChooserDescriptor();
    FileChooser.chooseFilesWithSlideEffect(descriptor, null, getSuggestedSdkRoot(sdkType),
                                           new Consumer<VirtualFile[]>() {
                                             @Override
                                             public void consume(final VirtualFile[] chosen) {
                                               if (chosen != null && chosen.length != 0) {
                                                 final String path = chosen[0].getPath();
                                                 if (sdkType.isValidSdkHome(path)) {
                                                   consumer.consume(path);
                                                   return;
                                                 }

                                                 String adjustedPath = sdkType.adjustSelectedSdkHome(path);
                                                 if (sdkType.isValidSdkHome(adjustedPath)) {
                                                   consumer.consume(adjustedPath);
                                                 }
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

  public static void suggestAndAddSdk(final Project project,
                                       SdkType sdkType,
                                       final Sdk[] existingSdks,
                                       JComponent popupOwner,
                                       final Consumer<Sdk> callback) {
    Collection<String> sdkHomes = sdkType.suggestHomePaths();
    List<String> suggestedSdkHomes = filterExistingPaths(sdkType, sdkHomes, existingSdks);
    if (suggestedSdkHomes.size() > 0) {
      suggestedSdkHomes.add(null);
      showSuggestedHomesPopup(project, sdkType, existingSdks, suggestedSdkHomes, popupOwner, callback);
    }
    else {
      createSdk(project, existingSdks, callback, sdkType);
    }
  }

  private static void showSuggestedHomesPopup(final Project project,
                                              final SdkType sdkType,
                                              final Sdk[] existingSdks,
                                              List<String> suggestedSdkHomes,
                                              JComponent popupOwner,
                                              final Consumer<Sdk> callback) {
    ListPopupStep sdkHomesStep = new BaseListPopupStep<String>("Select Interpreter Path", suggestedSdkHomes) {
      @NotNull
      @Override
      public String getTextFor(String value) {
        return value == null ? "Specify Other..." : FileUtil.toSystemDependentName(value);
      }

      @Override
      public PopupStep onChosen(String selectedValue, boolean finalChoice) {
        if (selectedValue == null) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              createSdk(project, existingSdks, callback, sdkType);
            }
          }, ModalityState.current());
        }
        else {
          Sdk sdk = setupSdk(existingSdks, LocalFileSystem.getInstance().findFileByPath(selectedValue),
                                                  sdkType, false, null, null);
          callback.consume(sdk);
        }
        return FINAL_CHOICE;
      }
    };
    final ListPopup popup = JBPopupFactory.getInstance().createListPopup(sdkHomesStep);
    popup.showUnderneathOf(popupOwner);
  }

  private static List<String> filterExistingPaths(SdkType sdkType, Collection<String> sdkHomes, final Sdk[] sdks) {
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
