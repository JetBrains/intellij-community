/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package com.intellij.ide.projectView.impl;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.SelectInTarget;
import com.intellij.ide.impl.ProjectPaneSelectInTarget;
import com.intellij.ide.projectView.BaseProjectTreeBuilder;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.ProjectViewSettings;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.ProjectViewDirectoryHelper;
import com.intellij.ide.projectView.impl.nodes.ProjectViewProjectNode;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeUpdater;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiDirectory;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;

public class ProjectViewPane extends AbstractProjectViewPSIPane {
  @NonNls public static final String ID = "ProjectPane";
  public static final String SHOW_EXCLUDED_FILES_OPTION = "show-excluded-files";
  private static final String USE_FILE_NESTING_RULES = "use-file-nesting-rules";

  private boolean myShowExcludedFiles = true;
  private boolean myUseFileNestingRules = true;

  public ProjectViewPane(Project project) {
    super(project);
  }

  @Override
  public String getTitle() {
    return IdeBundle.message("title.project");
  }

  @Override
  @NotNull
  public String getId() {
    return ID;
  }

  @Override
  public Icon getIcon() {
    return AllIcons.General.ProjectTab;
  }


  @Override
  public SelectInTarget createSelectInTarget() {
    return new ProjectPaneSelectInTarget(myProject);
  }

  @Override
  protected AbstractTreeUpdater createTreeUpdater(AbstractTreeBuilder treeBuilder) {
    return new ProjectViewTreeUpdater(treeBuilder);
  }

  @Override
  protected ProjectAbstractTreeStructureBase createStructure() {
    return new ProjectViewPaneTreeStructure();
  }

  @Override
  protected ProjectViewTree createTree(DefaultTreeModel treeModel) {
    return new ProjectViewTree(myProject, treeModel) {
      public String toString() {
        return getTitle() + " " + super.toString();
      }

      @Override
      public void setFont(Font font) {
        if (Registry.is("bigger.font.in.project.view")) {
          font = font.deriveFont(font.getSize() + 1.0f);
        }
        super.setFont(font);
      }
    };
  }

  @NotNull
  public String getComponentName() {
    return "ProjectPane";
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    String showExcludedOption = JDOMExternalizerUtil.readField(element, SHOW_EXCLUDED_FILES_OPTION);
    myShowExcludedFiles = showExcludedOption == null || Boolean.parseBoolean(showExcludedOption);
    String useFileNestingRules = JDOMExternalizerUtil.readField(element, USE_FILE_NESTING_RULES);
    myUseFileNestingRules = useFileNestingRules == null || Boolean.parseBoolean(useFileNestingRules);
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    if (!myShowExcludedFiles) {
      JDOMExternalizerUtil.writeField(element, SHOW_EXCLUDED_FILES_OPTION, String.valueOf(false));
    }
    if (!myUseFileNestingRules) {
      JDOMExternalizerUtil.writeField(element, USE_FILE_NESTING_RULES, String.valueOf(false));
    }
  }

  @Override
  public void addToolbarActions(DefaultActionGroup actionGroup) {
    actionGroup.addAction(new ShowExcludedFilesAction()).setAsSecondary(true);
    actionGroup.addAction(new ConfigureFilesNestingAction()).setAsSecondary(true);
  }

  public boolean isUseFileNestingRules() {
    return myUseFileNestingRules;
  }

  // should be first
  @Override
  public int getWeight() {
    return 0;
  }

  private final class ProjectViewTreeUpdater extends AbstractTreeUpdater {
    private ProjectViewTreeUpdater(final AbstractTreeBuilder treeBuilder) {
      super(treeBuilder);
    }

    @Override
    public boolean addSubtreeToUpdateByElement(Object element) {
      if (element instanceof PsiDirectory && !myProject.isDisposed()) {
        final PsiDirectory dir = (PsiDirectory)element;
        final ProjectTreeStructure treeStructure = (ProjectTreeStructure)myTreeStructure;
        PsiDirectory dirToUpdateFrom = dir;

        // optimization
        // isEmptyMiddleDirectory can be slow when project VFS is not fully loaded (initial dumb mode).
        // It's easiest to disable the optimization in any dumb mode
        if (!treeStructure.isFlattenPackages() && treeStructure.isHideEmptyMiddlePackages() && !DumbService.isDumb(myProject)) {
          while (dirToUpdateFrom != null && ProjectViewDirectoryHelper.getInstance(myProject).isEmptyMiddleDirectory(dirToUpdateFrom, true)) {
            dirToUpdateFrom = dirToUpdateFrom.getParentDirectory();
          }
        }
        boolean addedOk;
        while (!(addedOk = super.addSubtreeToUpdateByElement(dirToUpdateFrom == null? myTreeStructure.getRootElement() : dirToUpdateFrom))) {
          if (dirToUpdateFrom == null) {
            break;
          }
          dirToUpdateFrom = dirToUpdateFrom.getParentDirectory();
        }
        return addedOk;
      }

      return super.addSubtreeToUpdateByElement(element);
    }
  }

  private class ProjectViewPaneTreeStructure extends ProjectTreeStructure implements ProjectViewSettings {
    public ProjectViewPaneTreeStructure() {
      super(ProjectViewPane.this.myProject, ID);
    }

    @Override
    protected AbstractTreeNode createRoot(final Project project, ViewSettings settings) {
      return new ProjectViewProjectNode(project, settings);
    }

    @Override
    public boolean isShowExcludedFiles() {
      return myShowExcludedFiles;
    }

    @Override
    public boolean isUseFileNestingRules() {
      return myUseFileNestingRules;
    }

    @Override
    public boolean isToBuildChildrenInBackground(Object element) {
      return Registry.is("ide.projectView.ProjectViewPaneTreeStructure.BuildChildrenInBackground");
    }
  }

  private final class ShowExcludedFilesAction extends ToggleAction implements DumbAware {
    private ShowExcludedFilesAction() {
      super(IdeBundle.message("action.show.excluded.files"), IdeBundle.message("action.show.hide.excluded.files"), null);
    }

    @Override
    public boolean isSelected(AnActionEvent event) {
      return myShowExcludedFiles;
    }

    @Override
    public void setSelected(AnActionEvent event, boolean flag) {
      if (myShowExcludedFiles != flag) {
        myShowExcludedFiles = flag;
        updateFromRoot(true);
      }
    }

    @Override
    public void update(AnActionEvent e) {
      super.update(e);
      final Presentation presentation = e.getPresentation();
      final ProjectView projectView = ProjectView.getInstance(myProject);
      presentation.setEnabledAndVisible(projectView.getCurrentProjectViewPane() == ProjectViewPane.this);
    }
  }

  private class ConfigureFilesNestingAction extends DumbAwareAction {
    private ConfigureFilesNestingAction() {
      super(IdeBundle.message("action.file.nesting.in.project.view"));
    }

    @Override
    public void update(@NotNull final AnActionEvent e) {
      final ProjectView projectView = ProjectView.getInstance(myProject);
      e.getPresentation().setEnabledAndVisible(projectView.getCurrentProjectViewPane() == ProjectViewPane.this);
    }

    @Override
    public void actionPerformed(@NotNull final AnActionEvent e) {
      final FileNestingInProjectViewDialog dialog = new FileNestingInProjectViewDialog(myProject);
      dialog.reset(myUseFileNestingRules);
      dialog.show();
      if (dialog.isOK()) {
        dialog.apply(useFileNestingRules -> myUseFileNestingRules = useFileNestingRules);
        updateFromRoot(true);
      }
    }
  }

  @Override
  protected BaseProjectTreeBuilder createBuilder(DefaultTreeModel model) {
    return Registry.is("project.view.async.tree.model") ? null : super.createBuilder(model);
  }
}
