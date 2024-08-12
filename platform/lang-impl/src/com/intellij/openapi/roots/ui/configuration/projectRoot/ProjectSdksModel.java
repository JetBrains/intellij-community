// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.execution.wsl.WslPath;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.MasterDetailsComponent;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts.ListItem;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Consumer;
import com.intellij.util.EventDispatcher;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static com.intellij.openapi.util.NlsActions.ActionText;

/**
 * @author anna
 */
public class ProjectSdksModel implements SdkModel {
  private static final Logger LOG = Logger.getInstance(ProjectSdksModel.class);

  private final HashMap<Sdk, Sdk> myProjectSdks = new HashMap<>();
  private final EventDispatcher<Listener> mySdkEventsDispatcher = EventDispatcher.create(Listener.class);

  private boolean myModified;

  private Sdk myProjectSdk;
  private boolean myInitialized;

  @Override
  public @NotNull Listener getMulticaster() {
    return mySdkEventsDispatcher.getMulticaster();
  }

  @Override
  public Sdk @NotNull [] getSdks() {
    return myProjectSdks.values().toArray(new Sdk[0]);
  }

  @Override
  public @Nullable Sdk findSdk(@NotNull String sdkName) {
    for (Sdk projectJdk : myProjectSdks.values()) {
      if (sdkName.equals(projectJdk.getName())) return projectJdk;
    }
    return null;
  }

  @Override
  public void addListener(@NotNull Listener listener) {
    mySdkEventsDispatcher.addListener(listener);
  }

  @Override
  public void removeListener(@NotNull Listener listener) {
    mySdkEventsDispatcher.removeListener(listener);
  }

  public void syncSdks() {
    final Sdk[] projectSdks = ProjectJdkTable.getInstance().getAllJdks();
    for (Sdk sdk : projectSdks) {
      if (myProjectSdks.containsKey(sdk) || myProjectSdks.containsValue(sdk)) continue;

      Sdk editableCopy;
      try {
        editableCopy = sdk.clone();
      }
      catch (CloneNotSupportedException e) {
        LOG.error(e);
        continue;
      }

      myProjectSdks.put(sdk, editableCopy);
      SdkDownloadTracker.getInstance().registerEditableSdk(sdk, editableCopy);
      getMulticaster().sdkAdded(editableCopy);
    }
  }

  public void reset(@Nullable Project project) {
    myProjectSdks.clear();
    ProjectJdkTable jdkTable = ProjectJdkTable.getInstance();
    jdkTable.preconfigure();
    final Sdk[] projectSdks = jdkTable.getAllJdks();
    for (Sdk sdk : projectSdks) {
      try {
        Sdk editable = sdk.clone();
        myProjectSdks.put(sdk, editable);
        SdkDownloadTracker.getInstance().registerEditableSdk(sdk, editable);
      }
      catch (CloneNotSupportedException e) {
        LOG.error(e);
      }
    }
    if (project != null) {
      String sdkName = ProjectRootManager.getInstance(project).getProjectSdkName();
      myProjectSdk = sdkName == null ? null : findSdk(sdkName);
    }
    myModified = false;
    myInitialized = true;
  }

  public void disposeUIResources() {
    StreamEx.ofValues(myProjectSdks).select(Disposable.class).forEach(Disposer::dispose);
    myProjectSdks.clear();
    myInitialized = false;
  }

  public @NotNull HashMap<Sdk, Sdk> getProjectSdks() {
    return myProjectSdks;
  }

  public boolean isModified() {
    return myModified;
  }

  public void apply() throws ConfigurationException {
    apply(null);
  }

  public void apply(@Nullable MasterDetailsComponent configurable) throws ConfigurationException {
    apply(configurable, false);
  }

  public void apply(@Nullable MasterDetailsComponent configurable, boolean addedOnly) throws ConfigurationException {
    String[] errorString = new String[1];
    if (!canApply(errorString, configurable, addedOnly)) {
      throw new ConfigurationException(errorString[0]);
    }

    doApply();
    myModified = false;
  }

  private void doApply() {
    ApplicationManager.getApplication().runWriteAction(() -> {
      final ArrayList<Sdk> itemsInTable = new ArrayList<>();
      final ProjectJdkTable jdkTable = ProjectJdkTable.getInstance();
      final Sdk[] allFromTable = jdkTable.getAllJdks();

      // Delete removed and fill itemsInTable
      for (final Sdk tableItem : allFromTable) {
        if (myProjectSdks.containsKey(tableItem)) {
          itemsInTable.add(tableItem);
        }
        else {
          jdkTable.removeJdk(tableItem);
        }
      }

      // Now all removed items are deleted from table, itemsInTable contains all items in table
      for (Sdk originalJdk : itemsInTable) {
        final Sdk modifiedJdk = myProjectSdks.get(originalJdk);
        LOG.assertTrue(modifiedJdk != null);
        LOG.assertTrue(originalJdk != modifiedJdk);
        jdkTable.updateJdk(originalJdk, modifiedJdk);
      }
      // Add new items to table
      final Sdk[] allJdks = jdkTable.getAllJdks();
      for (final Sdk projectJdk : myProjectSdks.keySet()) {
        LOG.assertTrue(projectJdk != null);
        if (ArrayUtilRt.find(allJdks, projectJdk) == -1) {
          jdkTable.addJdk(projectJdk);
          jdkTable.updateJdk(projectJdk, myProjectSdks.get(projectJdk));

          SdkDownloadTracker.getInstance().tryRegisterSdkDownloadFailureHandler(projectJdk, () -> {
            ApplicationManager.getApplication().runWriteAction(() -> {
              ProjectJdkTable.getInstance().removeJdk(projectJdk);
            });
          });
        }
      }
    });
  }

  private boolean canApply(String @NotNull [] errorString, @Nullable MasterDetailsComponent rootConfigurable, boolean addedOnly) throws ConfigurationException {
    Map<Sdk, Sdk> sdks = new LinkedHashMap<>(myProjectSdks);
    if (addedOnly) {
      Sdk[] allJdks = ProjectJdkTable.getInstance().getAllJdks();
      for (Sdk jdk : allJdks) {
        sdks.remove(jdk);
      }
    }
    ArrayList<String> allNames = new ArrayList<>();
    Sdk itemWithError = null;
    for (Sdk currItem : sdks.values()) {
      String currName = currItem.getName();
      if (currName.isEmpty()) {
        itemWithError = currItem;
        errorString[0] = ProjectBundle.message("sdk.list.name.required.error");
        break;
      }
      if (allNames.contains(currName)) {
        itemWithError = currItem;
        errorString[0] = ProjectBundle.message("sdk.list.unique.name.required.error");
        break;
      }
      final SdkAdditionalData sdkAdditionalData = currItem.getSdkAdditionalData();
      if (sdkAdditionalData instanceof ValidatableSdkAdditionalData) {
        try {
          ((ValidatableSdkAdditionalData)sdkAdditionalData).checkValid(this);
        }
        catch (ConfigurationException e) {
          if (rootConfigurable != null) {
            final Object projectJdk = rootConfigurable.getSelectedObject();
            if (!(projectJdk instanceof Sdk) ||
                !Comparing.strEqual(((Sdk)projectJdk).getName(), currName)) { //do not leave current item with current name
              rootConfigurable.selectNodeInTree(currName);
            }
          }
          throw new ConfigurationException(ProjectBundle.message("sdk.configuration.exception", currName) + " " + e.getMessage());
        }
      }
      allNames.add(currName);
    }
    if (itemWithError == null) return true;
    if (rootConfigurable != null) {
      rootConfigurable.selectNodeInTree(itemWithError.getName());
    }
    return false;
  }

  public void removeSdk(@NotNull Sdk editableObject) {
    Sdk projectJdk = null;
    for (Sdk jdk : myProjectSdks.keySet()) {
      if (myProjectSdks.get(jdk) == editableObject) {
        projectJdk = jdk;
        break;
      }
    }
    if (projectJdk != null) {
      myProjectSdks.remove(projectJdk);
      SdkDownloadTracker.getInstance().onSdkRemoved(projectJdk);
      mySdkEventsDispatcher.getMulticaster().beforeSdkRemove(projectJdk);
      myModified = true;
    }
  }

  public void createAddActions(@NotNull DefaultActionGroup group,
                               @NotNull JComponent parent,
                               @NotNull java.util.function.Consumer<? super Sdk> updateTree,
                               @Nullable Predicate<? super SdkTypeId> filter) {
    createAddActions(group, parent, null, updateTree, filter);
  }

  private static @NotNull List<SdkType> getAddableSdkTypes(@Nullable Predicate<? super SdkTypeId> filter) {
    List<SdkType> result = new ArrayList<>();
    for (SdkType t : SdkType.getAllTypeList()) {
      if (t.allowCreationByUser() && (filter == null || filter.test(t))) {
        result.add(t);
      }
    }
    return result;
  }

  public void createAddActions(@NotNull DefaultActionGroup group,
                               @NotNull JComponent parent,
                               @Nullable Sdk selectedSdk,
                               @NotNull java.util.function.Consumer<? super Sdk> updateTree,
                               @Nullable Predicate<? super SdkTypeId> filter) {

    Map<SdkType, NewSdkAction> downloadActions = createDownloadActions(filter);
    Map<SdkType, NewSdkAction> defaultAddActions = createAddActions(filter);

    for (SdkType type : getAddableSdkTypes(filter)) {
      NewSdkAction downloadAction = downloadActions.get(type);
      if (downloadAction != null) {
        group.add(downloadAction.setOverrides(selectedSdk, parent, updateTree));
      }
      NewSdkAction defaultAction = defaultAddActions.get(type);
      if (defaultAction != null) {
        group.add(defaultAction.setOverrides(selectedSdk, parent, updateTree));
      }
    }
  }

  public abstract static class NewSdkAction extends DumbAwareAction {
    private final SdkType mySdkType;

    private Sdk mySelectedSdkOverride;
    private JComponent myParentOverride;
    private java.util.function.Consumer<? super Sdk> myCallbackOverride;

    private final @NotNull @ListItem String myListItemText;
    /** this is the text that is shown for the item in the nested list pop-up of SdkPopup **/
    private final @NotNull @ListItem String myListSubItemText;

    private NewSdkAction(@NotNull SdkType sdkType,
                         @NotNull @ActionText String actionText,
                         @NotNull @ListItem String listItemText,
                         @NotNull @ListItem String listSubItemText,
                         @Nullable Icon icon) {
      super(actionText, null, icon);
      myListItemText = listItemText;
      myListSubItemText = listSubItemText;
      mySdkType = sdkType;
    }

    public final @NotNull @ListItem String getListItemText() {
      return myListItemText;
    }

    public final @NotNull @ListItem String getListSubItemText() {
      return myListSubItemText;
    }

    @NotNull
    NewSdkAction setOverrides(@Nullable Sdk selectedSdkFallback,
                              @NotNull JComponent parentFallback,
                              @NotNull java.util.function.Consumer<? super Sdk> callbackFallback) {
      mySelectedSdkOverride = selectedSdkFallback;
      myParentOverride = parentFallback;
      myCallbackOverride = callbackFallback;
      return this;
    }

    public final @NotNull SdkType getSdkType() {
      return mySdkType;
    }

    @Override
    public final void actionPerformed(@NotNull AnActionEvent e) {
      Sdk selectedSdk = mySelectedSdkOverride;
      JComponent parent = myParentOverride;
      java.util.function.Consumer<? super Sdk> callback = myCallbackOverride;

      if (callback == null || parent == null) return;
      actionPerformed(selectedSdk, parent, callback);
    }

    public abstract void actionPerformed(@Nullable Sdk selectedSdk,
                                         @NotNull JComponent parent,
                                         @NotNull java.util.function.Consumer<? super Sdk> callback);
  }

  public @NotNull Map<SdkType, NewSdkAction> createDownloadActions(@Nullable Predicate<? super SdkTypeId> filter) {
    Map<SdkType, NewSdkAction> result = new LinkedHashMap<>();
    for (final SdkType type : getAddableSdkTypes(filter)) {
      SdkDownload downloadExtension = SdkDownload.EP_NAME.findFirstSafe(it -> it.supportsDownload(type));
      if (downloadExtension == null) continue;

      String text = ProjectBundle.message("sdk.configure.download.action", type.getPresentableName());
      String title = ProjectBundle.message("sdk.configure.download.actionTitle", type.getPresentableName());
      String subText = ProjectBundle.message("sdk.configure.download.subAction", type.getPresentableName());

      NewSdkAction downloadAction = new NewSdkAction(type, title, text, subText, downloadExtension.getIconForDownloadAction(type)) {
        @Override
        public void actionPerformed(@Nullable Sdk selectedSdk,
                                    @NotNull JComponent parent,
                                    @NotNull java.util.function.Consumer<? super Sdk> callback) {
          doDownload(downloadExtension, parent, selectedSdk, type, callback);
        }
      };

      result.put(type, downloadAction);
    }
    return result;
  }

  protected boolean forceAddActionToSelectFromDisk(@NotNull SdkType type) {
    return false;
  }

  public @NotNull Map<SdkType, NewSdkAction> createAddActions(@Nullable Predicate<? super SdkTypeId> filter) {
    Map<SdkType, NewSdkAction> result = new LinkedHashMap<>();
    for (final SdkType type : getAddableSdkTypes(filter)) {
      String sdkPresentableName = type.getPresentableName(), text, title, subText;

      boolean isForce = forceAddActionToSelectFromDisk(type);
      if (isForce) {
        text = ProjectBundle.message("sdk.configure.addFromDisk.sdkType.action", sdkPresentableName);
        title = ProjectBundle.message("sdk.configure.addFromDisk.sdkType.actionTitle", sdkPresentableName);
        subText = ProjectBundle.message("sdk.configure.addFromDisk.sdkType.subAction", sdkPresentableName);
      } else {
        text = ProjectBundle.message("sdk.configure.add.sdkType.action", sdkPresentableName);
        title = ProjectBundle.message("sdk.configure.add.sdkType.actionTitle", sdkPresentableName);
        subText = ProjectBundle.message("sdk.configure.add.sdkType.subAction", sdkPresentableName);
      }

      NewSdkAction addAction = new NewSdkAction(type, title, text, subText, type.getIcon()) {
        @Override
        public void actionPerformed(@Nullable Sdk selectedSdk,
                                    @NotNull JComponent parent,
                                    @NotNull java.util.function.Consumer<? super Sdk> callback) {
          if (!isForce && type.supportsCustomCreateUI()) {
            type.showCustomCreateUI(ProjectSdksModel.this, parent, selectedSdk, sdk -> setupSdk(sdk, callback));
          }
          else {
            SdkConfigurationUtil.selectSdkHome(type, home -> addSdk(type, home, sdk -> callback.accept(sdk)));
          }
        }
      };
      result.put(type, addAction);
    }
    return result;
  }

  public void doAdd(@NotNull JComponent parent, final @NotNull SdkType type, final @NotNull Consumer<? super Sdk> callback) {
    doAdd(parent, null, type, callback);
  }

  public void doDownload(@NotNull SdkDownload downloadExtension,
                         @NotNull JComponent parent,
                         @Nullable Sdk selectedSdk,
                         @NotNull SdkType type,
                         @NotNull java.util.function.Consumer<? super Sdk> callback) {
    LOG.assertTrue(downloadExtension.supportsDownload(type));
    myModified = true;

    downloadExtension.showDownloadUI(type, this, parent, null, selectedSdk, null, sdk -> setupInstallableSdk(type, sdk, callback));
  }

  public void doAdd(@NotNull JComponent parent, final @Nullable Sdk selectedSdk, final @NotNull SdkType type, final @NotNull Consumer<? super Sdk> callback) {
    myModified = true;
    if (type.supportsCustomCreateUI()) {
      type.showCustomCreateUI(this, parent, selectedSdk, sdk -> setupSdk(sdk, callback));
    }
    else {
      SdkConfigurationUtil.selectSdkHome(type, home -> addSdk(type, home, callback));
    }
  }

  public void addSdk(@NotNull SdkType type, @NotNull String home, @Nullable Consumer<? super Sdk> callback) {
    final Sdk newJdk = createSdk(type, home);
    setupSdk(newJdk, callback);
  }

  public @NotNull Sdk createSdk(@NotNull SdkType type, @NotNull String home) {
    String newSdkName = SdkConfigurationUtil.createUniqueSdkName(type, home, myProjectSdks.values());
    return createSdkInternal(type, newSdkName, home);
  }

  public @NotNull Sdk createSdk(@NotNull SdkType type, @NotNull String suggestedName, @NotNull String home) {
    String newSdkName = SdkConfigurationUtil.createUniqueSdkName(suggestedName, myProjectSdks.values());
    return createSdkInternal(type, newSdkName, home);
  }

  private static @NotNull Sdk createSdkInternal(@NotNull SdkType type,
                                                @NotNull String newSdkName,
                                                @NotNull String home) {
    final Sdk newJdk = ProjectJdkTable.getInstance().createSdk(newSdkName, type);
    SdkModificator sdkModificator = newJdk.getSdkModificator();
    sdkModificator.setHomePath(home);
    sdkModificator.setVersionString(type.getVersionString(home));
    ApplicationManager.getApplication().runWriteAction(() -> {
      sdkModificator.commitChanges();
    });
    return newJdk;
  }

  @RequiresEdt
  public void setupInstallableSdk(@NotNull SdkType type,
                                  @NotNull SdkDownloadTask item,
                                  @Nullable java.util.function.Consumer<? super Sdk> callback) {
    final Sdk incompleteSdk = createIncompleteSdk(type, item, callback);
    downloadSdk(incompleteSdk);
  }

  public @NotNull Sdk createIncompleteSdk(@NotNull SdkType type,
                                          @NotNull SdkDownloadTask item,
                                          java.util.function.@Nullable Consumer<? super Sdk> callback) {
    // we do not ask the SdkType to set up the SDK for us, instead, we return an incomplete SDK to the
    // model with an expectation it would be updated later on
    String suggestedName = item.getSuggestedSdkName();
    String homeDir = FileUtil.toSystemIndependentName(item.getPlannedHomeDir());
    if (WslPath.isWslUncPath(homeDir)) {
      suggestedName += " (WSL)";
    }

    SdkDownloadTracker tracker = SdkDownloadTracker.getInstance();
    var tempSdk = createSdk(type, suggestedName, homeDir);
    tracker.registerSdkDownload(tempSdk, item);

    AtomicReference<Sdk> sdk = new AtomicReference<>();
    doAdd(tempSdk, (editableSdk) -> {
      tracker.registerEditableSdk(tempSdk, editableSdk);
      tracker.tryRegisterSdkDownloadFailureHandler(editableSdk, () -> removeSdk(editableSdk));
      sdk.set(editableSdk);
      if (callback != null) {
        callback.accept(editableSdk);
      }
    });

    return sdk.get();
  }

  @RequiresEdt
  public void downloadSdk(Sdk sdk) {
    SdkDownloadTracker tracker = SdkDownloadTracker.getInstance();
    tracker.startSdkDownloadIfNeeded(sdk);
  }

  private void setupSdk(@NotNull Sdk newJdk, @Nullable java.util.function.Consumer<? super Sdk> callback) {
    String home = newJdk.getHomePath();
    SdkType sdkType = (SdkType)newJdk.getSdkType();

    AtomicBoolean pathSetup = new AtomicBoolean(false);
    ProgressManager.getInstance()
      .runProcessWithProgressSynchronously(
        () -> { pathSetup.set(sdkType.setupSdkPaths(newJdk, this)); },
        ProjectBundle.message("progress.text.configuring.sdk"),
        true,
        null
      )
    ;
    if (!pathSetup.get()) return;

    if (newJdk.getVersionString() == null) {
      String message = ProjectBundle.message("sdk.java.corrupt.error", home);
      Messages.showMessageDialog(message, ProjectBundle.message("sdk.java.corrupt.title"), Messages.getErrorIcon());
    }

    doAdd(newJdk, callback);
  }

  @Override
  public void addSdk(@NotNull Sdk sdk) {
    doAdd(sdk, null);
  }

  public void doAdd(@NotNull Sdk newSdk, @Nullable java.util.function.Consumer<? super Sdk> updateTree) {
    myModified = true;
    try {
      Sdk editableCopy = newSdk.clone();
      myProjectSdks.put(newSdk, editableCopy);
      if (updateTree != null) {
        updateTree.accept(editableCopy);
      }
      mySdkEventsDispatcher.getMulticaster().sdkAdded(editableCopy);
    }
    catch (CloneNotSupportedException e) {
      LOG.error(e);
    }
  }

  public @Nullable Sdk findSdk(final @Nullable Sdk modelJdk) {
    for (Map.Entry<Sdk, Sdk> entry : myProjectSdks.entrySet()) {
      Sdk jdk = entry.getKey();
      if (Comparing.equal(entry.getValue(), modelJdk)) {
        return jdk;
      }
    }
    return null;
  }

  public @Nullable Sdk getProjectSdk() {
    if (!myProjectSdks.containsValue(myProjectSdk)) return null;
    return myProjectSdk;
  }

  public void setProjectSdk(final Sdk projectSdk) {
    myProjectSdk = projectSdk;
  }

  public boolean isInitialized() {
    return myInitialized;
  }
}
