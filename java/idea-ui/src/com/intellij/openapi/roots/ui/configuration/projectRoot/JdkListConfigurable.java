/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.options.ConfigurationException;
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
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreePath;
import java.util.*;

import static com.intellij.openapi.projectRoots.SimpleJavaSdkType.notSimpleJavaSdkType;

public class JdkListConfigurable extends BaseStructureConfigurable {
  @NotNull
  private final ProjectSdksModel myJdksTreeModel;
  private final SdkModel.Listener myListener = new SdkModel.Listener() {
    @Override
    public void sdkChanged(Sdk sdk, String previousName) {
      updateName();
    }

    @Override
    public void sdkHomeSelected(Sdk sdk, String newSdkHome) {
      updateName();
    }

    private void updateName() {
      final TreePath path = myTree.getSelectionPath();
      if (path != null) {
        final NamedConfigurable configurable = ((MyNode)path.getLastPathComponent()).getConfigurable();
        if (configurable instanceof JdkConfigurable) {
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

  @Override
  protected String getComponentStateKey() {
    return "JdkListConfigurable.UI";
  }

  @Override
  @Nls
  public String getDisplayName() {
    return "SDKs";
  }

  @Override
  @Nullable
  @NonNls
  public String getHelpTopic() {
    return myCurrentConfigurable != null ? myCurrentConfigurable.getHelpTopic() : "reference.settingsdialog.project.structure.jdk";
  }

  @Override
  @NotNull
  @NonNls
  public String getId() {
    return "jdk.list";
  }

  @Override
  protected void loadTree() {
    final Map<Sdk,Sdk> sdks = myJdksTreeModel.getProjectSdks();
    for (Sdk sdk : sdks.keySet()) {
      final JdkConfigurable configurable = new JdkConfigurable((ProjectJdkImpl)sdks.get(sdk), myJdksTreeModel, TREE_UPDATER, myHistory,
                                                               myProject);
      addNode(new MyNode(configurable), myRoot);
    }
  }

  @NotNull
  @Override
  protected Collection<? extends ProjectStructureElement> getProjectStructureElements() {
    final List<ProjectStructureElement> result = new ArrayList<>();
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

  @Override
  public void dispose() {
    myJdksTreeModel.removeListener(myListener);
    myJdksTreeModel.disposeUIResources();
  }

  @NotNull
  public ProjectSdksModel getJdksTreeModel() {
    return myJdksTreeModel;
  }

  @Override
  public void reset() {
    super.reset();
    myTree.setRootVisible(false);
  }

  @Override
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
    myJdksTreeModel.setProjectSdk(ProjectRootManager.getInstance(myProject).getProjectSdk());
  }

  @Override
  public boolean isModified() {
    return super.isModified() || myJdksTreeModel.isModified();
  }

  public static JdkListConfigurable getInstance(Project project) {
    return ServiceManager.getService(project, JdkListConfigurable.class);
  }

  @Override
  public AbstractAddGroup createAddAction() {
    return new AbstractAddGroup(ProjectBundle.message("add.new.jdk.text")) {
      @NotNull
      @Override
      public AnAction[] getChildren(@Nullable final AnActionEvent e) {
        DefaultActionGroup group = new DefaultActionGroup(ProjectBundle.message("add.new.jdk.text"), true);
        myJdksTreeModel.createAddActions(group, myTree, projectJdk -> addJdkNode(projectJdk, true), notSimpleJavaSdkType());
        return group.getChildren(null);
      }
    };
  }

  @Override
  protected List<? extends RemoveConfigurableHandler<?>> getRemoveHandlers() {
    return Collections.singletonList(new SdkRemoveHandler());
  }

  @Override
  protected
  @Nullable
  String getEmptySelectionString() {
    return "Select an SDK to view or edit its details here";
  }

  private class SdkRemoveHandler extends RemoveConfigurableHandler<Sdk> {
    public SdkRemoveHandler() {
      super(JdkConfigurable.class);
    }

    @Override
    public boolean remove(@NotNull Collection<Sdk> sdks) {
      for (Sdk sdk : sdks) {
        myJdksTreeModel.removeSdk(sdk);
        myContext.getDaemonAnalyzer().removeElement(new SdkProjectStructureElement(myContext, sdk));
      }
      return true;
    }
  }
}
