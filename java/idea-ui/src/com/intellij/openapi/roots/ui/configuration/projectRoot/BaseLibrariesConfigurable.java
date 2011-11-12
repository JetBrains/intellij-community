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

import com.intellij.CommonBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.impl.libraries.LibraryImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablePresentation;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.configuration.artifacts.UsageInArtifact;
import com.intellij.openapi.roots.ui.configuration.libraries.LibraryEditingUtil;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.CreateNewLibraryAction;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.*;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import java.util.*;

public abstract class BaseLibrariesConfigurable extends BaseStructureConfigurable  {
  protected String myLevel;

  protected BaseLibrariesConfigurable(final @NotNull Project project) {
    super(project);
  }

  public static BaseLibrariesConfigurable getInstance(@NotNull Project project, @NotNull String tableLevel) {
    if (tableLevel.equals(LibraryTablesRegistrar.PROJECT_LEVEL)) {
      return ProjectLibrariesConfigurable.getInstance(project);
    }
    else {
      return GlobalLibrariesConfigurable.getInstance(project);
    }
  }

  public abstract LibraryTablePresentation getLibraryTablePresentation();

  protected void processRemovedItems() {
  }

  protected boolean wasObjectStored(final Object editableObject) {
    return false;
  }

  @Nullable
  public Runnable enableSearch(final String option) {
    return null;
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return "reference.settingsdialog.project.structure.library";
  }

  public boolean isModified() {
    boolean isModified = false;
    for (final LibrariesModifiableModel provider : myContext.myLevel2Providers.values()) {
      isModified |= provider.isChanged();
    }
    return isModified || super.isModified();
  }

  @Override
  public void checkCanApply() throws ConfigurationException {
    super.checkCanApply();
    for (LibraryConfigurable configurable : getLibraryConfigurables()) {
      if (configurable.getDisplayName().isEmpty()) {
        ((LibraryProjectStructureElement)configurable.getProjectStructureElement()).navigate();
        throw new ConfigurationException("Library name is not specified");
      }
    }
  }

  public void reset() {
    super.reset();
    myTree.setRootVisible(false);
  }

  protected void loadTree() {
    createLibrariesNode(myContext.createModifiableModelProvider(myLevel));
  }

  @NotNull
  @Override
  protected Collection<? extends ProjectStructureElement> getProjectStructureElements() {
    final List<ProjectStructureElement> result = new ArrayList<ProjectStructureElement>();
    for (LibraryConfigurable libraryConfigurable : getLibraryConfigurables()) {
      result.add(new LibraryProjectStructureElement(myContext, libraryConfigurable.getEditableObject()));
    }
    return result;
  }

  private List<LibraryConfigurable> getLibraryConfigurables() {
    //todo[nik] improve
    List<LibraryConfigurable> libraryConfigurables = new ArrayList<LibraryConfigurable>();
    for (int i = 0; i < myRoot.getChildCount(); i++) {
      final TreeNode node = myRoot.getChildAt(i);
      if (node instanceof MyNode) {
        final NamedConfigurable configurable = ((MyNode)node).getConfigurable();
        if (configurable instanceof LibraryConfigurable) {
          libraryConfigurables.add((LibraryConfigurable)configurable);
        }
      }
    }
    return libraryConfigurables;
  }

  private void createLibrariesNode(final StructureLibraryTableModifiableModelProvider modelProvider) {
    final Library[] libraries = modelProvider.getModifiableModel().getLibraries();
    for (Library library : libraries) {
      myRoot.add(new MyNode(new LibraryConfigurable(modelProvider, library, myContext, TREE_UPDATER)));
    }
    TreeUtil.sort(myRoot, new Comparator() {
      public int compare(final Object o1, final Object o2) {
        MyNode node1 = (MyNode)o1;
        MyNode node2 = (MyNode)o2;
        return node1.getDisplayName().compareToIgnoreCase(node2.getDisplayName());
      }
    });
    ((DefaultTreeModel)myTree.getModel()).reload(myRoot);
  }

  public void apply() throws ConfigurationException {
    super.apply();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        for (final LibrariesModifiableModel provider : myContext.myLevel2Providers.values()) {
          provider.deferredCommit();
        }
      }
    });
  }

  public String getLevel() {
    return myLevel;
  }

  public void createLibraryNode(Library library) {
    final LibraryTable table = library.getTable();
    if (table != null) {
      final String level = table.getTableLevel();
      final LibraryConfigurable configurable =
        new LibraryConfigurable(myContext.createModifiableModelProvider(level), library, myContext, TREE_UPDATER);
      final MyNode node = new MyNode(configurable);
      addNode(node, myRoot);
      final ProjectStructureDaemonAnalyzer daemonAnalyzer = myContext.getDaemonAnalyzer();
      daemonAnalyzer.queueUpdate(new LibraryProjectStructureElement(myContext, library));
      daemonAnalyzer.queueUpdateForAllElementsWithErrors();
    }
  }

  public void dispose() {
    if (myContext != null) {
      for (final LibrariesModifiableModel provider : myContext.myLevel2Providers.values()) {
        provider.disposeUncommittedLibraries();
      }
    }
  }

  @NotNull
  protected List<? extends AnAction> createCopyActions(boolean fromPopup) {
    final ArrayList<AnAction> actions = new ArrayList<AnAction>();
    actions.add(new CopyLibraryAction());
    if (fromPopup) {
      final BaseLibrariesConfigurable targetGroup = getOppositeGroup();
      actions.add(new ChangeLibraryLevelAction(myProject, myTree, this, targetGroup));
      actions.add(new AddLibraryToModuleDependenciesAction(myProject, this));
    }
    return actions;
  }

  protected AbstractAddGroup createAddAction() {
    return new AbstractAddGroup(getAddText()) {
      @NotNull
      public AnAction[] getChildren(@Nullable final AnActionEvent e) {
        return CreateNewLibraryAction.createActionOrGroup(getAddText(), BaseLibrariesConfigurable.this, myProject);
      }
    };
  }

  protected abstract String getAddText();

  public abstract StructureLibraryTableModifiableModelProvider getModelProvider();

  public abstract BaseLibrariesConfigurable getOppositeGroup();

  @Override
  protected void updateSelection(@Nullable NamedConfigurable configurable) {
    boolean selectionChanged = !Comparing.equal(myCurrentConfigurable, configurable);
    if (myCurrentConfigurable != null && selectionChanged) {
      ((LibraryConfigurable)myCurrentConfigurable).onUnselected();
    }
    super.updateSelection(configurable);
    if (myCurrentConfigurable != null && selectionChanged) {
      ((LibraryConfigurable)myCurrentConfigurable).onSelected();
    }
  }

  @Override
  public void onStructureUnselected() {
    if (myCurrentConfigurable != null) {
      ((LibraryConfigurable)myCurrentConfigurable).onUnselected();
    }
  }

  @Override
  public void onStructureSelected() {
    if (myCurrentConfigurable != null) {
      ((LibraryConfigurable)myCurrentConfigurable).onSelected();
    }
  }

  public void removeLibrary(@NotNull LibraryProjectStructureElement element) {
    getModelProvider().getModifiableModel().removeLibrary(element.getLibrary());
    myContext.getDaemonAnalyzer().removeElement(element);
    final MyNode node = findNodeByObject(myRoot, element.getLibrary());
    if (node != null) {
      removePaths(TreeUtil.getPathFromRoot(node));
    }
  }

  protected boolean removeLibrary(final Library library) {
    final LibraryTable table = library.getTable();
    if (table != null) {
      final Collection<ProjectStructureElementUsage> usages = myContext.getDaemonAnalyzer().getUsages(getSelectedElement());
      if (usages.size() > 0) {
        final List<String> modules = new ArrayList<String>();
        final List<String> artifacts = new ArrayList<String>();
        for (final ProjectStructureElementUsage usage : usages) {
          if (usage instanceof UsageInModuleClasspath) {
            modules.add(usage.getPresentableName());
          } else if (usage instanceof UsageInArtifact) {
            artifacts.add(usage.getPresentableName());
          } else {
            LOG.error("Unknown usage: " + usage.getClass().getName());
          }
        }

        final StringBuilder sb = new StringBuilder("Library \"");
        sb.append(library.getName()).append("\" is used in ");
        if (modules.size() > 0) {
          if (modules.size() == 1) {
            sb.append("module ").append("\"").append(modules.get(0)).append("\"");
          } else {
            sb.append(modules.size()).append(" modules");
          }
        }

        if (artifacts.size() > 0) {
          sb.append(modules.size() > 0 ? " and in " : "");

          if (artifacts.size() == 1) {
            sb.append("artifact ").append("\"").append(artifacts.get(0)).append("\".");
          } else {
            sb.append(artifacts.size()).append(" artifacts.");
          }
        }

        sb.append("\n\nAre you sure you want to delete this library?");

        if (DialogWrapper.OK_EXIT_CODE == Messages.showOkCancelDialog(myProject, sb.toString(),
                                    "Confirm Library Deletion", Messages.getQuestionIcon())) {

          final ModuleStructureConfigurable rootConfigurable = ModuleStructureConfigurable.getInstance(myProject);
          for (final ProjectStructureElementUsage usage : usages) {
            if (usage instanceof UsageInModuleClasspath) {
              rootConfigurable.removeLibraryOrderEntry(((ModuleProjectStructureElement)usage.getContainingElement()).getModule(), library);
            } else if (usage instanceof UsageInArtifact) {
              ((UsageInArtifact)usage).removeElement();
            }
          }

          getModelProvider().getModifiableModel().removeLibrary(library);
          myContext.getDaemonAnalyzer().removeElement(new LibraryProjectStructureElement(myContext, library));
          return true;
        }
      } else {
        getModelProvider().getModifiableModel().removeLibrary(library);
        myContext.getDaemonAnalyzer().removeElement(new LibraryProjectStructureElement(myContext, library));
        return true;
      }
    }

    return false;
  }

  @Nullable
  protected String getEmptySelectionString() {
    return "Select a library to view or edit its details here";
  }

  private class CopyLibraryAction extends AnAction {
    private CopyLibraryAction() {
      super(CommonBundle.message("button.copy"), CommonBundle.message("button.copy"), COPY_ICON);
    }

    public void actionPerformed(final AnActionEvent e) {
      final Object o = getSelectedObject();
      if (o instanceof LibraryEx) {
        final LibraryEx selected = (LibraryEx)o;
        final String newName = Messages.showInputDialog("Enter library name:", "Copy Library", null, selected.getName() + "2", new NonEmptyInputValidator());
        if (newName == null) return;

        BaseLibrariesConfigurable configurable = BaseLibrariesConfigurable.this;
        final LibraryEx library = (LibraryEx)myContext.getLibrary(selected.getName(), myLevel);
        LOG.assertTrue(library != null);

        final LibrariesModifiableModel libsModel = configurable.getModelProvider().getModifiableModel();
        final Library lib = libsModel.createLibrary(newName, library.getType());
        final LibraryEx.ModifiableModelEx model = (LibraryEx.ModifiableModelEx)libsModel.getLibraryEditor(lib).getModel();
        LibraryEditingUtil.copyLibrary(library, Collections.<String, String>emptyMap(), model);
      }
    }

    public void update(final AnActionEvent e) {
      if (myTree.getSelectionPaths() == null || myTree.getSelectionPaths().length != 1) {
        e.getPresentation().setEnabled(false);
      } else {
        e.getPresentation().setEnabled(getSelectedObject() instanceof LibraryImpl);
      }
    }
  }
}
