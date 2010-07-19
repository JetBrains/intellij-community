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
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModel;
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureElement;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.SdkProjectStructureElement;
import com.intellij.openapi.ui.MasterDetailsComponent;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

@State(
  name = "JdkListConfigurable.UI",
  storages = {
    @Storage(
      id ="other",
      file = "$WORKSPACE_FILE$"
    )}
)
public class JdkListConfigurable extends BaseStructureConfigurable {

  private final ProjectSdksModel myJdksTreeModel;


  SdkModel.Listener myListener = new SdkModel.Listener() {
    public void sdkAdded(Sdk sdk) {
    }

    public void beforeSdkRemove(Sdk sdk) {
    }

    public void sdkChanged(Sdk sdk, String previousName) {
      updateName();
    }

    public void sdkHomeSelected(Sdk sdk, String newSdkHome) {
      updateName();
    }

    private void updateName() {
      final TreePath path = myTree.getSelectionPath();
      if (path != null) {
        final NamedConfigurable configurable = ((MyNode)path.getLastPathComponent()).getConfigurable();
        if (configurable != null && configurable instanceof JdkConfigurable) {
          configurable.updateName();
        }
      }
    }
  };

  public JdkListConfigurable(final Project project, ProjectStructureConfigurable root) {
    super(project);
    myJdksTreeModel = root.getProjectJdksModel();
    myJdksTreeModel.addListener(myListener);
  }
  protected void processRemovedItems() {
  }

  protected boolean wasObjectStored(final Object editableObject) {
    return false;
  }

  @Nls
  public String getDisplayName() {
    return "SDKs";
  }

  @Nullable
  public Icon getIcon() {
    return null;
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return "reference.settingsdialog.project.structure.jdk";
  }

  @NonNls
  public String getId() {
    return "jdk.list";
  }

  @Nullable
  public Runnable enableSearch(final String option) {
    return null;
  }

  protected void loadTree() {
    final HashMap<Sdk,Sdk> sdks = myJdksTreeModel.getProjectSdks();
    for (Sdk sdk : sdks.keySet()) {
      final JdkConfigurable configurable = new JdkConfigurable((ProjectJdkImpl)sdks.get(sdk), myJdksTreeModel, TREE_UPDATER, myHistory,
                                                               myProject);
      addNode(new MyNode(configurable), myRoot);
    }
  }

  @NotNull
  @Override
  protected Collection<? extends ProjectStructureElement> getProjectStructureElements() {
    final List<ProjectStructureElement> result = new ArrayList<ProjectStructureElement>();
    for (Sdk sdk : myJdksTreeModel.getProjectSdks().values()) {
      result.add(new SdkProjectStructureElement(myContext, sdk));
    }
    return result;
  }

  public boolean addJdkNode(final Sdk jdk, final boolean selectInTree) {
    if (!myUiDisposed) {
      myContext.getDaemonAnalyzer().queueUpdate(new SdkProjectStructureElement(myContext, jdk));
      addNode(new MyNode(new JdkConfigurable((ProjectJdkImpl)jdk, myJdksTreeModel, TREE_UPDATER, myHistory, myProject)), myRoot);
      if (selectInTree) {
        selectNodeInTree(MasterDetailsComponent.findNodeByObject(myRoot, jdk));
      }
      return true;
    }
    return false;
  }

  public void dispose() {
    myJdksTreeModel.removeListener(myListener);
    myJdksTreeModel.disposeUIResources();
  }

  public ProjectSdksModel getJdksTreeModel() {
    return myJdksTreeModel;
  }

  public void reset() {
    super.reset();
    myTree.setRootVisible(false);
  }

  public void apply() throws ConfigurationException {
    boolean modifiedJdks = false;
    for (int i = 0; i < myRoot.getChildCount(); i++) {
      final NamedConfigurable configurable = ((MyNode)myRoot.getChildAt(i)).getConfigurable();
      if (configurable.isModified()) {
        configurable.apply();
        modifiedJdks = true;
      }
    }

    if (myJdksTreeModel.isModified() || modifiedJdks) myJdksTreeModel.apply(this);
    myJdksTreeModel.setProjectSdk(ProjectRootManager.getInstance(myProject).getProjectJdk());
  }

  public boolean isModified() {
    return super.isModified() || myJdksTreeModel.isModified();
  }

  public static JdkListConfigurable getInstance(Project project) {
    return ShowSettingsUtil.getInstance().findProjectConfigurable(project, JdkListConfigurable.class);
  }

  public AbstractAddGroup createAddAction() {
    return new AbstractAddGroup(ProjectBundle.message("add.new.jdk.text")) {
      public AnAction[] getChildren(@Nullable final AnActionEvent e) {
        DefaultActionGroup group = new DefaultActionGroup(ProjectBundle.message("add.new.jdk.text"), true);
        myJdksTreeModel.createAddActions(group, myTree, new Consumer<Sdk>() {
          public void consume(final Sdk projectJdk) {
            addJdkNode(projectJdk, true);
          }
        });
        return group.getChildren(null);
      }
    };
  }

  protected void removeJdk(final Sdk jdk) {
    myJdksTreeModel.removeSdk(jdk);
    myContext.getDaemonAnalyzer().removeElement(new SdkProjectStructureElement(myContext, jdk));
  }

  protected
  @Nullable
  String getEmptySelectionString() {
    return "Select a JDK to view or edit its details here";
  }
}
