// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration;

import com.google.common.collect.ImmutableList;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.ui.configuration.JdkComboBox.*;
import com.intellij.openapi.roots.ui.configuration.SdkDetector.DetectedSdkListener;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel.NewSdkAction;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public abstract class JdkListModelBuilder {
  @Nullable private final Project myProject;
  @NotNull private final ProjectSdksModel mySdkModel;
  @NotNull private final Condition<? super Sdk> mySdkFilter;
  @NotNull private final Condition<? super SdkTypeId> mySdkTypeFilter;
  @NotNull private final Condition<? super SdkTypeId> mySdkTypeCreationFilter;

  private boolean mySuggestedItemsConnected = false;
  private boolean myIsSdkDetectorInProgress = false;

  private JdkComboBoxItem myFirstItem = null;
  private ImmutableList<ActualJdkComboBoxItem> myHead = ImmutableList.of();
  private ImmutableList<ActionJdkItem> myDownloadActions = ImmutableList.of();
  private ImmutableList<ActionJdkItem> myAddActions = ImmutableList.of();
  private ImmutableList<SuggestedJdkItem> mySuggestions = ImmutableList.of();
  private JdkComboBox.InvalidJdkComboBoxItem myInvalidJdkItem = null;

  protected JdkListModelBuilder(@Nullable Project project,
                                @NotNull ProjectSdksModel sdkModel,
                                @Nullable Condition<? super SdkTypeId> sdkTypeFilter,
                                @Nullable Condition<? super SdkTypeId> sdkTypeCreationFilter,
                                @Nullable Condition<? super Sdk> sdkFilter) {
    myProject = project;
    mySdkModel = sdkModel;

    mySdkTypeFilter = type -> type != null
                              && (sdkTypeFilter == null || sdkTypeFilter.value(type));

    mySdkTypeCreationFilter = type -> type != null
                                      && (!(type instanceof SdkType) || ((SdkType)type).allowCreationByUser())
                                      && mySdkTypeFilter.value(type)
                                      && (sdkTypeCreationFilter == null || sdkTypeCreationFilter.value(type));

    mySdkFilter = sdk -> sdk != null
                         && mySdkTypeFilter.value(sdk.getSdkType())
                         && (sdkFilter == null || sdkFilter.value(sdk));
  }

  /**
   * Implement this method to turn a given {@link JdkListModel}
   * into a specific model and apply it for the control
   */
  protected abstract void syncModel(@NotNull JdkListModel model);

  private void syncModel() {
    syncModel(buildModel());
  }

  @NotNull
  public JdkListModel buildModel() {
    ImmutableList.Builder<JdkComboBoxItem> newModel = ImmutableList.builder();

    if (myFirstItem instanceof ProjectJdkComboBoxItem) {
      Sdk projectSdk = mySdkModel.getProjectSdk();
      if (projectSdk == null || mySdkFilter.value(projectSdk)) {
        newModel.add(myFirstItem);
      }
    }
    else if (myFirstItem != null) {
      newModel.add(myFirstItem);
    }

    newModel.addAll(myHead);
    if (myInvalidJdkItem != null) {
      newModel.add(myInvalidJdkItem);
    }

    ImmutableList<ActionJdkItem> subItems = ImmutableList.<ActionJdkItem>builder()
      .addAll(myDownloadActions)
      .addAll(myAddActions)
      .build();

    if (subItems.size() > 3) {
      newModel.add(new ActionGroupJdkItem(AllIcons.General.Add, "Add SDK", subItems));
    }
    else {
      newModel.addAll(subItems);
    }

    for (SuggestedJdkItem item : mySuggestions) {
      if (!isApplicableSuggestedItem(item)) continue;
      newModel.add(item);
    }

    return new JdkListModel(myIsSdkDetectorInProgress, newModel.build());
  }

  private boolean isApplicableSuggestedItem(@NotNull SuggestedJdkItem item) {
    if (!mySdkTypeFilter.value(item.getSdkType())) return false;

    for (Sdk sdk : mySdkModel.getSdks()) {
      if (FileUtil.pathsEqual(sdk.getHomePath(), item.getPath())) return false;
    }
    return true;
  }

  @Nullable
  public JdkComboBoxItem setFirstItem(@NotNull JdkComboBoxItem firstItem) {
    if (Objects.equals(myFirstItem, firstItem)) return myFirstItem;
    myFirstItem = firstItem;
    syncModel();
    return firstItem;
  }

  @NotNull
  public JdkComboBoxItem setInvalidJdk(String name) {
    if (myInvalidJdkItem == null || !Objects.equals(myInvalidJdkItem.getSdkName(), name)) {
      myInvalidJdkItem = new JdkComboBox.InvalidJdkComboBoxItem(name);
      syncModel();
    }
    return myInvalidJdkItem;
  }

  public void reloadSdks() {
    ImmutableList.Builder<ActualJdkComboBoxItem> newHead = new ImmutableList.Builder<>();
    for (Sdk jdk : sortSdks(mySdkModel.getSdks())) {
      if (!mySdkFilter.value(jdk)) continue;

      newHead.add(new ActualJdkComboBoxItem(jdk) {
        @Override
        boolean hasSameSdk(@NotNull Sdk value) {
          return Objects.equals(getJdk(), value) || Objects.equals(mySdkModel.findSdk(getJdk()), value);
        }
      });
    }

    myHead = newHead.build();
    syncModel();
  }

  public void reloadActions(@NotNull JComponent parent,
                            @Nullable Sdk selectedSdk,
                            @NotNull Consumer<? super Sdk> onNewSdkAdded) {
    Map<SdkType, NewSdkAction> downloadActions = mySdkModel.createDownloadActions(parent, selectedSdk, onNewSdkAdded,
                                                                                  mySdkTypeCreationFilter);
    Map<SdkType, NewSdkAction> addActions = mySdkModel.createAddActions(parent, selectedSdk, onNewSdkAdded, mySdkTypeCreationFilter);

    myDownloadActions = createActions(parent, ActionRole.DOWNLOAD, downloadActions);
    myAddActions = createActions(parent, ActionRole.ADD, addActions);
    syncModel();
  }

  public void detectItems(@NotNull JComponent parent,
                          @NotNull Consumer<? super Sdk> onNewSdkAdded) {
    if (mySuggestedItemsConnected) return;
    mySuggestedItemsConnected = true;

    SdkDetector.getInstance().getDetectedSdksWithUpdate(myProject, parent, new DetectedSdkListener() {
      @Override
      public void onSearchStarted() {
        mySuggestions = ImmutableList.of();
        myIsSdkDetectorInProgress = true;
        syncModel();
      }

      @Override
      public void onSdkDetected(@NotNull SdkType type, @Nullable String version, @NotNull String home) {
        SuggestedJdkItem item = new SuggestedJdkItem(type, version, home) {
          @Override
          void executeAction() {
            mySdkModel.addSdk(getSdkType(), getPath(), onNewSdkAdded);
          }
        };

        mySuggestions = ImmutableList.<SuggestedJdkItem>builder()
          .addAll(mySuggestions)
          .add(item)
          .build();
        syncModel();
      }

      @Override
      public void onSearchCompleted() {
        myIsSdkDetectorInProgress = false;
        syncModel();
      }
    });
  }

  @NotNull
  private static ImmutableList<ActionJdkItem> createActions(@NotNull JComponent parent,
                                                            @NotNull ActionRole role,
                                                            @NotNull Map<SdkType, NewSdkAction> actions) {
    ImmutableList.Builder<ActionJdkItem> builder = ImmutableList.builder();
    for (NewSdkAction action : actions.values()) {
      builder.add(new ActionJdkItem(role, action) {
        @Override
        void executeAction() {
          DataContext dataContext = DataManager.getInstance().getDataContext(parent);
          AnActionEvent event = new AnActionEvent(null,
                                                  dataContext,
                                                  ActionPlaces.UNKNOWN,
                                                  new Presentation(""),
                                                  ActionManager.getInstance(),
                                                  0);
          myAction.actionPerformed(event);
        }
      });
    }
    return builder.build();
  }

  @NotNull
  private static Sdk[] sortSdks(@NotNull final Sdk[] sdks) {
    Sdk[] clone = sdks.clone();
    Arrays.sort(clone, (sdk1, sdk2) -> {
      SdkType sdkType1 = (SdkType)sdk1.getSdkType();
      SdkType sdkType2 = (SdkType)sdk2.getSdkType();
      if (!sdkType1.getComparator().equals(sdkType2.getComparator())) return StringUtil
        .compare(sdkType1.getPresentableName(), sdkType2.getPresentableName(), true);
      return sdkType1.getComparator().compare(sdk1, sdk2);
    });
    return clone;
  }
}
