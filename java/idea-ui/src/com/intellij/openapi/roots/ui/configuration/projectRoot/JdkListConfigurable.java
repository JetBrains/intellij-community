// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModel;
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.SdkPopupFactory;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureElement;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.SdkProjectStructureElement;
import com.intellij.openapi.ui.MasterDetailsComponent;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.util.IconUtil;
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
  private boolean hasListenerRegistered = false;
  private final SdkModel.Listener myListener = new SdkModel.Listener() {
    @Override
    public void sdkAdded(@NotNull Sdk sdk) {
      addJdkNode(sdk, true);
    }

    @Override
    public void sdkChanged(@NotNull Sdk sdk, String previousName) {
      updateName();
    }

    @Override
    public void sdkHomeSelected(@NotNull Sdk sdk, @NotNull String newSdkHome) {
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

  public JdkListConfigurable(ProjectStructureConfigurable projectStructureConfigurable) {
    super(projectStructureConfigurable);
    myJdksTreeModel = projectStructureConfigurable.getProjectJdksModel();
  }

  @Override
  protected String getComponentStateKey() {
    return "JdkListConfigurable.UI";
  }

  @Override
  @Nls
  public String getDisplayName() {
    return JavaUiBundle.message("configurable.JdkListConfigurable.display.name");
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
    hasListenerRegistered = false;
    myJdksTreeModel.disposeUIResources();
  }

  @NotNull
  public ProjectSdksModel getJdksTreeModel() {
    return myJdksTreeModel;
  }

  @Override
  public void reset() {
    super.reset();
    if (!hasListenerRegistered) {
      hasListenerRegistered = true;
      myJdksTreeModel.addListener(myListener);
    }
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

  @NotNull
  @Override
  protected ArrayList<AnAction> createActions(boolean fromPopup) {
    ArrayList<AnAction> defaultActions = super.createActions(fromPopup);

    AnAction addNewAction = new AddSdkAction();

    defaultActions.add(0, addNewAction);
    return defaultActions;
  }

  @Override
  public AbstractAddGroup createAddAction() {
    return null;
  }

  @Override
  protected List<? extends RemoveConfigurableHandler<?>> getRemoveHandlers() {
    return Collections.singletonList(new SdkRemoveHandler());
  }

  @Override
  protected
  @Nullable
  String getEmptySelectionString() {
    return JavaUiBundle.message("project.jdks.configurable.empty.selection.string");
  }

  private class SdkRemoveHandler extends RemoveConfigurableHandler<Sdk> {
    SdkRemoveHandler() {
      super(JdkConfigurable.class);
    }

    @Override
    public boolean remove(@NotNull Collection<? extends Sdk> sdks) {
      for (Sdk sdk : sdks) {
        myJdksTreeModel.removeSdk(sdk);
        myContext.getDaemonAnalyzer().removeElement(new SdkProjectStructureElement(myContext, sdk));
      }
      return true;
    }
  }

  private class AddSdkAction extends AnAction implements DumbAware {
    AddSdkAction() {
      super(JavaUiBundle.message("add.new.jdk.text"), null, IconUtil.getAddIcon());

      AbstractAddGroup replacedAction = new AbstractAddGroup(JavaUiBundle.message("action.name.text")) {
        @Override
        public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
          return AnAction.EMPTY_ARRAY;
        }
      };
      this.setShortcutSet(replacedAction.getShortcutSet());
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabledAndVisible(true);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      SdkPopupFactory
        .newBuilder()
        .withProject(myProject)
        .withProjectSdksModel(getJdksTreeModel())
        .withSdkTypeFilter(notSimpleJavaSdkType())
        .withSdkFilter(sdk -> false)
        .buildPopup()
        .showPopup(e);
    }
  }
}
