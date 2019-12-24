// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration;

import com.google.common.collect.ImmutableList;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.impl.DependentSdkType;
import com.intellij.openapi.roots.ui.configuration.SdkListItem.*;
import com.intellij.openapi.roots.ui.configuration.SdkDetector.DetectedSdkListener;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel.NewSdkAction;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.EventListener;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public class SdkListModelBuilder {
  @Nullable private final Project myProject;
  @NotNull private final ProjectSdksModel mySdkModel;
  @NotNull private final Condition<? super Sdk> mySdkFilter;
  @NotNull private final Condition<? super SdkTypeId> mySdkTypeFilter;
  @NotNull private final Condition<? super SdkTypeId> mySdkTypeCreationFilter;

  @NotNull private final EventDispatcher<ModelListener> myModelListener = EventDispatcher.create(ModelListener.class);

  private boolean mySuggestedItemsConnected = false;
  private boolean myIsSdkDetectorInProgress = false;

  private ImmutableList<SdkItem> myHead = ImmutableList.of();
  private ImmutableList<ActionItem> myDownloadActions = ImmutableList.of();
  private ImmutableList<ActionItem> myAddActions = ImmutableList.of();
  private ImmutableList<SuggestedItem> mySuggestions = ImmutableList.of();
  private ProjectSdkItem myProjectSdkItem = null;
  private NoneSdkItem myNoneSdkItem = null;
  private InvalidSdkItem myInvalidItem = null;

  public SdkListModelBuilder(@Nullable Project project,
                             @NotNull ProjectSdksModel sdkModel,
                             @Nullable Condition<? super SdkTypeId> sdkTypeFilter,
                             @Nullable Condition<? super SdkTypeId> sdkTypeCreationFilter,
                             @Nullable Condition<? super Sdk> sdkFilter) {
    myProject = project;
    mySdkModel = sdkModel;

    mySdkTypeFilter = type -> type != null
                              && (sdkTypeFilter == null || sdkTypeFilter.value(type));

    Condition<SdkTypeId> simpleJavaTypeFix = notSimpleJavaSdkTypeIfAlternativeExists();
    mySdkTypeCreationFilter = type -> type != null
                                      && (!(type instanceof SdkType) || ((SdkType)type).allowCreationByUser())
                                      && mySdkTypeFilter.value(type)
                                      && (sdkTypeCreationFilter == null || sdkTypeCreationFilter.value(type))
                                      && simpleJavaTypeFix.value(type);

    mySdkFilter = sdk -> sdk != null
                         && mySdkTypeFilter.value(sdk.getSdkType())
                         && (sdkFilter == null || sdkFilter.value(sdk));
  }

  /**
   * Implement this listener to turn a given {@link SdkListModel}
   * into a specific model and apply it for the control
   * @see #addModelListener(ModelListener)
   */
  public interface ModelListener extends EventListener {
    /**
     * Implement this method to turn a given {@link SdkListModel}
     * into a specific model and apply it for the control
     */
    default void syncModel(@NotNull SdkListModel model) {}
  }

  public void addModelListener(@NotNull ModelListener listener) {
    myModelListener.addListener(listener);
  }

  public void removeListener(@NotNull ModelListener listener) {
    myModelListener.removeListener(listener);
  }

  @NotNull
  private SdkListModel syncModel() {
    SdkListModel model = buildModel();
    myModelListener.getMulticaster().syncModel(model);
    return model;
  }

  @NotNull
  public SdkListModel buildModel() {
    ImmutableList.Builder<SdkListItem> newModel = ImmutableList.builder();


    if (myNoneSdkItem != null) {
      newModel.add(myNoneSdkItem);
    }
    if (myProjectSdkItem != null) {
      Sdk projectSdk = mySdkModel.getProjectSdk();
      if (projectSdk == null || mySdkFilter.value(projectSdk)) {
        newModel.add(myProjectSdkItem);
      }
    }
    if (myInvalidItem != null) {
      newModel.add(myInvalidItem);
    }

    newModel.addAll(myHead);

    ImmutableList<ActionItem> subItems = ImmutableList.<ActionItem>builder()
      .addAll(myDownloadActions)
      .addAll(myAddActions)
      .build();

    if (subItems.size() > 3 && !newModel.build().isEmpty()) {
      newModel.add(new GroupItem(AllIcons.General.Add, "Add SDK", subItems));
    }
    else {
      newModel.addAll(subItems);
    }

    for (SuggestedItem item : mySuggestions) {
      if (!isApplicableSuggestedItem(item)) continue;
      newModel.add(item);
    }

    return new SdkListModel(myIsSdkDetectorInProgress, newModel.build());
  }

  private boolean isApplicableSuggestedItem(@NotNull SuggestedItem item) {
    if (!mySdkTypeFilter.value(item.getSdkType())) return false;

    for (Sdk sdk : mySdkModel.getSdks()) {
      if (FileUtil.pathsEqual(sdk.getHomePath(), item.getHomePath())) return false;
    }
    return true;
  }

  @NotNull
  public SdkListItem showProjectSdkItem() {
    ProjectSdkItem projectSdkItem = new ProjectSdkItem();
    if (Objects.equals(myProjectSdkItem, projectSdkItem)) return myProjectSdkItem;
    myProjectSdkItem = projectSdkItem;
    syncModel();
    return myProjectSdkItem;
  }

  @NotNull
  public SdkListItem showNoneSdkItem() {
    NoneSdkItem noneSdkItem = new NoneSdkItem();
    if (Objects.equals(myNoneSdkItem, noneSdkItem)) return myNoneSdkItem;
    myNoneSdkItem = noneSdkItem;
    syncModel();
    return myNoneSdkItem;
  }

  @NotNull
  public SdkListItem showInvalidSdkItem(String name) {
    InvalidSdkItem invalidItem = new InvalidSdkItem(name);
    if (Objects.equals(myInvalidItem, invalidItem)) return myInvalidItem;
    myInvalidItem = invalidItem;
    syncModel();
    return myInvalidItem;
  }

  public void reloadSdks() {
    ImmutableList.Builder<SdkItem> newHead = new ImmutableList.Builder<>();
    for (Sdk sdk : sortSdks(mySdkModel.getSdks())) {
      if (!mySdkFilter.value(sdk)) continue;

      newHead.add(newSdkItem(sdk));
    }

    myHead = newHead.build();
    syncModel();
  }

  @NotNull
  private SdkItem newSdkItem(@NotNull Sdk sdk) {
    return new SdkItem(sdk) {
      @Override
      boolean hasSameSdk(@NotNull Sdk value) {
        return Objects.equals(getSdk(), value) || Objects.equals(mySdkModel.findSdk(getSdk()), value);
      }
    };
  }

  /**
   * Executes an action that is associated with the given {@param item}.
   * <br/>
   * If there are no actions associated, method returns {@code false},
   * the {@param afterExecution} is NOT executed
   * <br/>
   * If there is action associated, it is scheduled for execution. The
   * {@param afterExecution} callback is ONLY if the action execution
   * ended up successfully and a new item was added to the model. In that
   * case the callback is executed after the model is updated and the
   * {@link #syncModel()} is invoked. The implementation may not
   * execute the callback or and model update for any internal and
   * non-selectable items
   *
   * @return {@code true} if action was started and the {@param afterExecution}
   *                      callback could happen later, {@code false} otherwise
   */
  public boolean executeAction(@NotNull JComponent parent,
                               @NotNull SdkListItem item,
                               @NotNull Consumer<? super SdkListItem> afterExecution) {
    Consumer<Sdk> onNewSdkAdded = sdk -> {
      reloadSdks();
      SdkItem sdkItem = newSdkItem(sdk);
      afterExecution.consume(sdkItem);
    };

    if (item instanceof ActionItem) {
      NewSdkAction action = ((ActionItem)item).myAction;
      action.actionPerformed(null, parent, onNewSdkAdded);
      return true;
    }

    if (item instanceof SuggestedItem) {
      SuggestedItem suggestedItem = (SuggestedItem)item;
      mySdkModel.addSdk(suggestedItem.getSdkType(), suggestedItem.getHomePath(), onNewSdkAdded);
      return true;
    }

    return false;
  }

  public void reloadActions() {
    Map<SdkType, NewSdkAction> downloadActions = mySdkModel.createDownloadActions(mySdkTypeCreationFilter);
    Map<SdkType, NewSdkAction> addActions = mySdkModel.createAddActions(mySdkTypeCreationFilter);

    myDownloadActions = createActions(ActionRole.DOWNLOAD, downloadActions);
    myAddActions = createActions(ActionRole.ADD, addActions);
    syncModel();
  }

  public void detectItems(@NotNull JComponent parent, @NotNull Disposable lifetime) {
    if (mySuggestedItemsConnected) return;
    mySuggestedItemsConnected = true;

    SdkDetector.getInstance().getDetectedSdksWithUpdate(myProject, lifetime, ModalityState.stateForComponent(parent), new DetectedSdkListener() {
      @Override
      public void onSearchStarted() {
        mySuggestions = ImmutableList.of();
        myIsSdkDetectorInProgress = true;
        syncModel();
      }

      @Override
      public void onSdkDetected(@NotNull SdkType type, @NotNull String version, @NotNull String home) {
        SuggestedItem item = new SuggestedItem(type, version, home);

        mySuggestions = ImmutableList.<SuggestedItem>builder()
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
  private static ImmutableList<ActionItem> createActions(@NotNull ActionRole role,
                                                         @NotNull Map<SdkType, NewSdkAction> actions) {
    ImmutableList.Builder<ActionItem> builder = ImmutableList.builder();
    for (NewSdkAction action : actions.values()) {
      builder.add(new ActionItem(role, action, null));
    }
    return builder.build();
  }

  @NotNull
  private static Sdk[] sortSdks(@NotNull final Sdk[] sdks) {
    Sdk[] clone = sdks.clone();
    Arrays.sort(clone, (sdk1, sdk2) -> {
      SdkType sdkType1 = (SdkType)sdk1.getSdkType();
      SdkType sdkType2 = (SdkType)sdk2.getSdkType();
      return !sdkType1.equals(sdkType2)
             ? StringUtil.compare(sdkType1.getPresentableName(), sdkType2.getPresentableName(), true)
             : sdkType1.getComparator().compare(sdk1, sdk2);
    });
    return clone;
  }

  private static Condition<SdkTypeId> notSimpleJavaSdkTypeIfAlternativeExists() {
    boolean hasNotSimple = Stream.of(SdkType.getAllTypes())
      .filter(SimpleJavaSdkType.notSimpleJavaSdkType()::value)
      .anyMatch(it -> it instanceof JavaSdkType && !(it instanceof DependentSdkType));

    if (hasNotSimple) {
      //we found another JavaSdkType (e.g. JavaSdkImpl), there is no need for SimpleJavaSdkType
      return SimpleJavaSdkType.notSimpleJavaSdkType();
    } else {
      //there is only one JavaSdkType, so it is no need to filter anything
      return id -> true;
    }
  }
}
