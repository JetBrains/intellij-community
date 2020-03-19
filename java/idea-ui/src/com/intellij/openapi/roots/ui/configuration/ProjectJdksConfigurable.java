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

package com.intellij.openapi.roots.ui.configuration;

import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl;
import com.intellij.openapi.roots.ui.configuration.projectRoot.JdkConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.ui.MasterDetailsComponent;
import com.intellij.openapi.ui.MasterDetailsStateService;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Ref;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.util.IconUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.util.*;

import static com.intellij.openapi.projectRoots.SimpleJavaSdkType.notSimpleJavaSdkType;

public class ProjectJdksConfigurable extends MasterDetailsComponent {
  private final ProjectSdksModel myProjectJdksModel;
  private final Project myProject;

  public ProjectJdksConfigurable(@NotNull Project project) {
    this(project, ProjectStructureConfigurable.getInstance(project).getProjectJdksModel());
  }

  public ProjectJdksConfigurable(@NotNull Project project, @NotNull ProjectSdksModel sdksModel) {
    myProject = project;
    myProjectJdksModel = sdksModel;
    initTree();
    myToReInitWholePanel = true;
    reInitWholePanelIfNeeded();
  }

  @Override
  protected String getComponentStateKey() {
    return "ProjectJDKs.UI";
  }

  @Override
  protected MasterDetailsStateService getStateService() {
    return MasterDetailsStateService.getInstance(myProject);
  }

  @Override
  protected void initTree() {
    super.initTree();
    new TreeSpeedSearch(myTree, treePath -> ((MyNode)treePath.getLastPathComponent()).getDisplayName(), true);

    myTree.setRootVisible(false);
  }

  @Override
  public void reset() {
    super.reset();

    myProjectJdksModel.reset(myProject);

    myRoot.removeAllChildren();
    final Map<Sdk, Sdk> sdks = myProjectJdksModel.getProjectSdks();
    for (Sdk sdk : sdks.keySet()) {
      if (!(sdk instanceof ProjectJdkImpl)) continue;

      final JdkConfigurable configurable = new JdkConfigurable((ProjectJdkImpl)sdks.get(sdk), myProjectJdksModel, TREE_UPDATER, myHistory, myProject);
      addNode(new MyNode(configurable), myRoot);
    }
    selectJdk(myProjectJdksModel.getProjectSdk()); //restore selection

    JBSplitter splitter = extractSplitter();
    if (splitter != null) {
      splitter.setAndLoadSplitterProportionKey("project.jdk.splitter");
    }
  }

  @Nullable
  private JBSplitter extractSplitter() {
    final Component[] components = myWholePanel.getComponents();
    return components.length == 1 && components[0] instanceof JBSplitter ? (JBSplitter)components[0] : null;
  }

  @Override
  public void apply() throws ConfigurationException {
    final Ref<ConfigurationException> exceptionRef = Ref.create();
    try {
      super.apply();
      boolean modifiedJdks = false;
      for (int i = 0; i < myRoot.getChildCount(); i++) {
        final NamedConfigurable configurable = ((MyNode)myRoot.getChildAt(i)).getConfigurable();
        if (configurable.isModified()) {
          configurable.apply();
          modifiedJdks = true;
        }
      }

      if (myProjectJdksModel.isModified() || modifiedJdks) {
        myProjectJdksModel.apply(this);
      }
      myProjectJdksModel.setProjectSdk(getSelectedJdk());
    }
    catch (ConfigurationException e) {
      exceptionRef.set(e);
    }
    if (!exceptionRef.isNull()) {
      throw exceptionRef.get();
    }
  }


  @Override
  public boolean isModified() {
    return super.isModified() || myProjectJdksModel.isModified();
  }


  @Override
  public void disposeUIResources() {
    myProjectJdksModel.disposeUIResources();
    super.disposeUIResources();
  }

  @Override
  @Nullable
  protected ArrayList<AnAction> createActions(final boolean fromPopup) {
    if (myProjectJdksModel == null) {
      return null;
    }
    final ArrayList<AnAction> actions = new ArrayList<>();
    DefaultActionGroup group = DefaultActionGroup.createPopupGroup(JavaUiBundle.messagePointer("add.new.jdk.text"));
    group.getTemplatePresentation().setIcon(IconUtil.getAddIcon());
    myProjectJdksModel.createAddActions(group, myTree, projectJdk -> {
      addNode(new MyNode(new JdkConfigurable(((ProjectJdkImpl)projectJdk), myProjectJdksModel, TREE_UPDATER, myHistory, myProject), false), myRoot);
      selectNodeInTree(findNodeByObject(myRoot, projectJdk));
    }, notSimpleJavaSdkType());
    actions.add(new MyActionGroupWrapper(group));
    actions.add(new MyDeleteAction(Conditions.alwaysTrue()));
    return actions;
  }

  @Override
  protected void processRemovedItems() {
    final Set<Sdk> jdks = new HashSet<>();
    for(int i = 0; i < myRoot.getChildCount(); i++){
      final DefaultMutableTreeNode node = (DefaultMutableTreeNode)myRoot.getChildAt(i);
      final NamedConfigurable namedConfigurable = (NamedConfigurable)node.getUserObject();
      jdks.add(((JdkConfigurable)namedConfigurable).getEditableObject());
    }
    final HashMap<Sdk, Sdk> sdks = new HashMap<>(myProjectJdksModel.getProjectSdks());
    for (Sdk sdk : sdks.values()) {
      if (!jdks.contains(sdk)) {
        myProjectJdksModel.removeSdk(sdk);
      }
    }
  }

  @Override
  protected boolean wasObjectStored(Object editableObject) {
    return myProjectJdksModel.getProjectSdks().containsKey((Sdk)editableObject);
  }

  @Nullable
  public Sdk getSelectedJdk() {
    return (Sdk)getSelectedObject();
  }

  public void selectJdk(final Sdk projectJdk) {
    selectNodeInTree(projectJdk);
  }

  @Override
  @Nullable
  public String getDisplayName() {
    return null;
  }

  @Override
  @Nullable
  @NonNls
  public String getHelpTopic() {
    return null;
  }

  @Override
  protected
  @Nullable
  String getEmptySelectionString() {
    return "Select an SDK to view or edit its details here";
  }

  public void selectJdkVersion(JavaSdkVersion requiredJdkVersion) {
    for (Sdk sdk : myProjectJdksModel.getSdks()) {
      if (JavaSdk.getInstance().isOfVersionOrHigher(sdk, requiredJdkVersion)) {
        selectJdk(sdk);
        break;
      }
    }
  }
}
