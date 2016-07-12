/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.*;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.MasterDetailsComponent;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Consumer;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;

/**
 * @author anna
 * @since 05-Jun-2006
 */
public class ProjectSdksModel implements SdkModel {
  private static final Logger LOG = Logger.getInstance("com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel");

  private final HashMap<Sdk, Sdk> myProjectSdks = new HashMap<Sdk, Sdk>();
  private final EventDispatcher<Listener> mySdkEventsDispatcher = EventDispatcher.create(Listener.class);

  private boolean myModified = false;

  private Sdk myProjectSdk;
  private boolean myInitialized = false;

  @Override
  public Listener getMulticaster() {
    return mySdkEventsDispatcher.getMulticaster();
  }

  @Override
  public Sdk[] getSdks() {
    return myProjectSdks.values().toArray(new Sdk[myProjectSdks.size()]);
  }

  @Override
  @Nullable
  public Sdk findSdk(String sdkName) {
    for (Sdk projectJdk : myProjectSdks.values()) {
      if (Comparing.strEqual(projectJdk.getName(), sdkName)) return projectJdk;
    }
    return null;
  }

  @Override
  public void addListener(Listener listener) {
    mySdkEventsDispatcher.addListener(listener);
  }

  @Override
  public void removeListener(Listener listener) {
    mySdkEventsDispatcher.removeListener(listener);
  }

  public void reset(@Nullable Project project) {
    myProjectSdks.clear();
    final Sdk[] projectSdks = ProjectJdkTable.getInstance().getAllJdks();
    for (Sdk sdk : projectSdks) {
      try {
        myProjectSdks.put(sdk, (Sdk)sdk.clone());
      }
      catch (CloneNotSupportedException e) {
        LOG.error(e);
      }
    }
    if (project != null) {
      myProjectSdk = findSdk(ProjectRootManager.getInstance(project).getProjectSdkName());
    }
    myModified = false;
    myInitialized = true;
  }

  public void disposeUIResources() {
    myProjectSdks.clear();
    myInitialized = false;
  }

  public HashMap<Sdk, Sdk> getProjectSdks() {
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

    DumbService.allowStartingDumbModeInside(DumbModePermission.MAY_START_BACKGROUND, () -> doApply());
    myModified = false;
  }

  private void doApply() {
    ApplicationManager.getApplication().runWriteAction(() -> {
      final ArrayList<Sdk> itemsInTable = new ArrayList<Sdk>();
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
        }
      }
    });
  }

  private boolean canApply(String[] errorString, @Nullable MasterDetailsComponent rootConfigurable, boolean addedOnly) throws ConfigurationException {

    LinkedHashMap<Sdk, Sdk> sdks = new LinkedHashMap<Sdk, Sdk>(myProjectSdks);
    if (addedOnly) {
      Sdk[] allJdks = ProjectJdkTable.getInstance().getAllJdks();
      for (Sdk jdk : allJdks) {
        sdks.remove(jdk);
      }
    }
    ArrayList<String> allNames = new ArrayList<String>();
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

  public void removeSdk(final Sdk editableObject) {
    Sdk projectJdk = null;
    for (Sdk jdk : myProjectSdks.keySet()) {
      if (myProjectSdks.get(jdk) == editableObject) {
        projectJdk = jdk;
        break;
      }
    }
    if (projectJdk != null) {
      myProjectSdks.remove(projectJdk);
      mySdkEventsDispatcher.getMulticaster().beforeSdkRemove(projectJdk);
      myModified = true;
    }
  }

  public void createAddActions(@NotNull DefaultActionGroup group, @NotNull JComponent parent, @NotNull Consumer<Sdk> updateTree) {
    createAddActions(group, parent, updateTree, null);
  }

  public void createAddActions(@NotNull DefaultActionGroup group,
                               @NotNull final JComponent parent,
                               @NotNull final Consumer<Sdk> updateTree,
                               @Nullable Condition<SdkTypeId> filter) {
    final SdkType[] types = SdkType.getAllTypes();
    for (final SdkType type : types) {
      if (filter != null && !filter.value(type)) continue;
      final AnAction addAction = new DumbAwareAction(type.getPresentableName(), null, type.getIconForAddAction()) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          doAdd(parent, type, updateTree);
        }
      };
      group.add(addAction);
    }
  }

  public void doAdd(@NotNull JComponent parent, @NotNull final SdkType type, @NotNull final Consumer<Sdk> callback) {
    myModified = true;
    if (type.supportsCustomCreateUI()) {
      type.showCustomCreateUI(this, parent, sdk -> setupSdk(sdk, callback));
    }
    else {
      SdkConfigurationUtil.selectSdkHome(type, home -> addSdk(type, home, callback));
    }
  }

  public void addSdk(@NotNull SdkType type, @NotNull String home, @Nullable Consumer<Sdk> callback) {
    String newSdkName = SdkConfigurationUtil.createUniqueSdkName(type, home, myProjectSdks.values());
    final ProjectJdkImpl newJdk = new ProjectJdkImpl(newSdkName, type);
    newJdk.setHomePath(home);
    setupSdk(newJdk, callback);
  }

  private void setupSdk(Sdk newJdk, @Nullable Consumer<Sdk> callback) {
    String home = newJdk.getHomePath();
    SdkType sdkType = (SdkType)newJdk.getSdkType();
    if (!sdkType.setupSdkPaths(newJdk, this)) return;

    if (newJdk.getVersionString() == null) {
      String message = ProjectBundle.message("sdk.java.corrupt.error", home);
      Messages.showMessageDialog(message, ProjectBundle.message("sdk.java.corrupt.title"), Messages.getErrorIcon());
    }

    doAdd(newJdk, callback);
  }

  @Override
  public void addSdk(Sdk sdk) {
    doAdd(sdk, null);
  }

  public void doAdd(Sdk newSdk, @Nullable Consumer<Sdk> updateTree) {
    myModified = true;
    try {
      Sdk editableCopy = (Sdk)newSdk.clone();
      myProjectSdks.put(newSdk, editableCopy);
      if (updateTree != null) {
        updateTree.consume(editableCopy);
      }
      mySdkEventsDispatcher.getMulticaster().sdkAdded(editableCopy);
    }
    catch (CloneNotSupportedException e) {
      LOG.error(e);
    }
  }

  @Nullable
  public Sdk findSdk(@Nullable final Sdk modelJdk) {
    for (Sdk jdk : myProjectSdks.keySet()) {
      if (Comparing.equal(myProjectSdks.get(jdk), modelJdk)) return jdk;
    }
    return null;
  }

  @Nullable
  public Sdk getProjectSdk() {
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
