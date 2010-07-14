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

package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.MasterDetailsComponent;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * User: anna
 * Date: 05-Jun-2006
 */
public class ProjectSdksModel implements SdkModel {
  private static final Logger LOG = Logger.getInstance("com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel");

  private final HashMap<Sdk, Sdk> myProjectSdks = new HashMap<Sdk, Sdk>();
  private final EventDispatcher<Listener> mySdkEventsDispatcher = EventDispatcher.create(Listener.class);

  private boolean myModified = false;

  private Sdk myProjectSdk;
  private boolean myInitialized = false;

  public Listener getMulticaster() {
    return mySdkEventsDispatcher.getMulticaster();
  }

  public Sdk[] getSdks() {
    return myProjectSdks.values().toArray(new Sdk[myProjectSdks.size()]);
  }

  @Nullable
  public Sdk findSdk(String sdkName) {
    for (Sdk projectJdk : myProjectSdks.values()) {
      if (Comparing.strEqual(projectJdk.getName(), sdkName)) return projectJdk;
    }
    return null;
  }

  public void addListener(Listener listener) {
    mySdkEventsDispatcher.addListener(listener);
  }

  public void removeListener(Listener listener) {
    mySdkEventsDispatcher.removeListener(listener);
  }

  public void reset(Project project) {
    myProjectSdks.clear();
    final Sdk[] projectJdks = ProjectJdkTable.getInstance().getAllJdks();
    for (Sdk jdk : projectJdks) {
      try {
        myProjectSdks.put(jdk, (Sdk)jdk.clone());
      }
      catch (CloneNotSupportedException e) {
        //can't be
      }
    }
    myProjectSdk = findSdk(ProjectRootManager.getInstance(project).getProjectJdkName());
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

  public boolean isModified(){
    return myModified;
  }

  public void apply() throws ConfigurationException {
    apply(null);
  }

  public void apply(@Nullable MasterDetailsComponent configurable) throws ConfigurationException {
    String[] errorString = new String[1];
    if (!canApply(errorString, configurable)) {
      throw new ConfigurationException(errorString[0]);
    }
    final Sdk[] allFromTable = ProjectJdkTable.getInstance().getAllJdks();
    final ArrayList<Sdk> itemsInTable = new ArrayList<Sdk>();
    // Delete removed and fill itemsInTable
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        final ProjectJdkTable jdkTable = ProjectJdkTable.getInstance();
        for (final Sdk tableItem : allFromTable) {
          if (myProjectSdks.containsKey(tableItem)) {
            itemsInTable.add(tableItem);
          }
          else {
            jdkTable.removeJdk(tableItem);
          }
        }
      }
    });
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        // Now all removed items are deleted from table, itemsInTable contains all items in table
        final ProjectJdkTable jdkTable = ProjectJdkTable.getInstance();
        for (Sdk originalJdk : itemsInTable) {
          final Sdk modifiedJdk = myProjectSdks.get(originalJdk);
          LOG.assertTrue(modifiedJdk != null);
          jdkTable.updateJdk(originalJdk, modifiedJdk);
        }
        // Add new items to table
        final Sdk[] allJdks = jdkTable.getAllJdks();
        for (final Sdk projectJdk : myProjectSdks.keySet()) {
          LOG.assertTrue(projectJdk != null);
          if (ArrayUtil.find(allJdks, projectJdk) == -1) {
            jdkTable.addJdk(projectJdk);
          }
        }
      }
    });
    myModified = false;
  }

  private boolean canApply(String[] errorString, @Nullable MasterDetailsComponent rootConfigurable) throws ConfigurationException {
    ArrayList<String> allNames = new ArrayList<String>();
    Sdk itemWithError = null;
    for (Sdk currItem : myProjectSdks.values()) {
      String currName = currItem.getName();
      if (currName.length() == 0) {
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
      if (sdkAdditionalData != null) {
        try {
          sdkAdditionalData.checkValid(this);
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

  public void createAddActions(DefaultActionGroup group, final JComponent parent, final Consumer<Sdk> updateTree) {
    final SdkType[] types = SdkType.getAllTypes();
    for (final SdkType type : types) {
      final AnAction addAction = new DumbAwareAction(type.getPresentableName(),
                                              null,
                                              type.getIconForAddAction()) {
          public void actionPerformed(AnActionEvent e) {
            doAdd(type, parent, updateTree);
          }
        };
      group.add(addAction);
    }
  }

  public void doAdd(final SdkType type, JComponent parent, final Consumer<Sdk> updateTree) {
    myModified = true;
    final String home = SdkConfigurationUtil.selectSdkHome(parent, type);
    if (home == null) {
      return;
    }
    String newSdkName = SdkConfigurationUtil.createUniqueSdkName(type, home, myProjectSdks.values());
    final ProjectJdkImpl newJdk = new ProjectJdkImpl(newSdkName, type);
    newJdk.setHomePath(home);

    if (!type.setupSdkPaths(newJdk, this)) return;

    if (newJdk.getVersionString() == null) {
       Messages.showMessageDialog(ProjectBundle.message("sdk.java.corrupt.error", home),
                                  ProjectBundle.message("sdk.java.corrupt.title"), Messages.getErrorIcon());
    }

    doAdd(newJdk, updateTree);
  }

  public void addSdk(Sdk sdk) {
    doAdd((ProjectJdkImpl) sdk, null);
  }

  private void doAdd(ProjectJdkImpl newSdk, @Nullable Consumer<Sdk> updateTree) {
    myProjectSdks.put(newSdk, newSdk);
    if (updateTree != null) {
      updateTree.consume(newSdk);
    }
    mySdkEventsDispatcher.getMulticaster().sdkAdded(newSdk);
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
