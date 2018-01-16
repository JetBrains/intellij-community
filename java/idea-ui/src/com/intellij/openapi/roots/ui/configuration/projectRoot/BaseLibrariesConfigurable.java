/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.CommonBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.impl.libraries.LibraryImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablePresentation;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.configuration.libraries.LibraryEditingUtil;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.CreateNewLibraryAction;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.LibraryProjectStructureElement;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureDaemonAnalyzer;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureElement;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureElementUsage;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.ui.NonEmptyInputValidator;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public abstract class BaseLibrariesConfigurable extends BaseStructureConfigurable  {
  protected final String myLevel;

  protected BaseLibrariesConfigurable(final @NotNull Project project, @NotNull String libraryTableLevel) {
    super(project);
    myLevel = libraryTableLevel;
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

  @Override
  @Nullable
  @NonNls
  public String getHelpTopic() {
    return "reference.settingsdialog.project.structure.library";
  }

  @Override
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
    checkForEmptyAndDuplicatedNames("Library", CommonBundle.getErrorTitle(), LibraryConfigurable.class);
    for (LibraryConfigurable configurable : getLibraryConfigurables()) {
      if (configurable.getDisplayName().isEmpty()) {
        ((LibraryProjectStructureElement)configurable.getProjectStructureElement()).navigate();
        throw new ConfigurationException("Library name is not specified");
      }
    }
  }

  @Override
  public void reset() {
    super.reset();
    myTree.setRootVisible(false);
  }

  @Override
  protected void loadTree() {
    createLibrariesNode(myContext.createModifiableModelProvider(myLevel));
  }

  @NotNull
  @Override
  protected Collection<? extends ProjectStructureElement> getProjectStructureElements() {
    final List<ProjectStructureElement> result = new ArrayList<>();
    for (LibraryConfigurable libraryConfigurable : getLibraryConfigurables()) {
      result.add(new LibraryProjectStructureElement(myContext, libraryConfigurable.getEditableObject()));
    }
    return result;
  }

  private List<LibraryConfigurable> getLibraryConfigurables() {
    //todo[nik] improve
    List<LibraryConfigurable> libraryConfigurables = new ArrayList<>();
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
    TreeUtil.sortRecursively(myRoot, (o1, o2) -> o1.getDisplayName().compareToIgnoreCase(o2.getDisplayName()));
    ((DefaultTreeModel)myTree.getModel()).reload(myRoot);
  }

  @Override
  public void apply() throws ConfigurationException {
    super.apply();
    ApplicationManager.getApplication().runWriteAction(() -> {
      for (final LibrariesModifiableModel provider : myContext.myLevel2Providers.values()) {
        provider.deferredCommit();
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

  public void removeLibraryNode(@NotNull final Library library) {
    removeLibrary(new LibraryProjectStructureElement(myContext, library));
  }

  @Override
  public void dispose() {
    if (myContext != null) {
      for (final LibrariesModifiableModel provider : myContext.myLevel2Providers.values()) {
        Disposer.dispose(provider);
      }
    }
  }

  @Override
  @NotNull
  protected List<? extends AnAction> createCopyActions(boolean fromPopup) {
    final ArrayList<AnAction> actions = new ArrayList<>();
    actions.add(new CopyLibraryAction());
    if (fromPopup) {
      final BaseLibrariesConfigurable targetGroup = getOppositeGroup();
      actions.add(new ChangeLibraryLevelAction(myProject, myTree, this, targetGroup));
      actions.add(new AddLibraryToModuleDependenciesAction(myProject, this));
    }
    return actions;
  }

  @Override
  protected AbstractAddGroup createAddAction() {
    return new AbstractAddGroup(getAddText()) {
      @Override
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
    if (myCurrentConfigurable instanceof LibraryConfigurable && selectionChanged) {
      ((LibraryConfigurable)myCurrentConfigurable).onUnselected();
    }
    super.updateSelection(configurable);
    if (myCurrentConfigurable instanceof LibraryConfigurable && selectionChanged) {
      ((LibraryConfigurable)myCurrentConfigurable).onSelected();
    }
  }

  @Override
  public void onStructureUnselected() {
    if (myCurrentConfigurable instanceof LibraryConfigurable) {
      ((LibraryConfigurable)myCurrentConfigurable).onUnselected();
    }
  }

  @Override
  public void onStructureSelected() {
    if (myCurrentConfigurable instanceof LibraryConfigurable) {
      ((LibraryConfigurable)myCurrentConfigurable).onSelected();
    }
  }

  public void removeLibrary(@NotNull LibraryProjectStructureElement element) {
    removeLibraries(Collections.singletonList(element));
  }

  public void removeLibraries(@NotNull List<LibraryProjectStructureElement> libraries) {
    List<TreePath> pathsToRemove = new ArrayList<>();
    for (LibraryProjectStructureElement element : libraries) {
      getModelProvider().getModifiableModel().removeLibrary(element.getLibrary());
      MyNode node = findNodeByObject(myRoot, element.getLibrary());
      if (node != null) {
        pathsToRemove.add(TreeUtil.getPathFromRoot(node));
      }
    }
    myContext.getDaemonAnalyzer().removeElements(libraries);
    removePaths(pathsToRemove.toArray(new TreePath[pathsToRemove.size()]));
  }

  @Override
  protected List<? extends RemoveConfigurableHandler<?>> getRemoveHandlers() {
    return Collections.singletonList(new RemoveConfigurableHandler<Library>(LibraryConfigurable.class) {
      @Override
      public boolean remove(@NotNull Collection<Library> libraries) {
        List<Pair<LibraryProjectStructureElement, Collection<ProjectStructureElementUsage>>> toRemove = new ArrayList<>();

        String firstLibraryUsageDescription = null;
        String firstLibraryWithUsageName = null;
        int librariesWithUsages = 0;
        for (Library library : libraries) {
          final LibraryTable table = library.getTable();
          if (table == null) continue;

          final LibraryProjectStructureElement libraryElement = new LibraryProjectStructureElement(myContext, library);
          final Collection<ProjectStructureElementUsage> usages =
            new ArrayList<>(myContext.getDaemonAnalyzer().getUsages(libraryElement));
          if (usages.size() > 0) {
            if (librariesWithUsages == 0) {
              final MultiMap<String, ProjectStructureElementUsage> containerType2Usage = new MultiMap<>();
              for (final ProjectStructureElementUsage usage : usages) {
                containerType2Usage.putValue(usage.getContainingElement().getTypeName(), usage);
              }

              List<String> types = new ArrayList<>(containerType2Usage.keySet());
              Collections.sort(types);

              final StringBuilder sb = new StringBuilder("Library '");
              Library libraryModel = myContext.getLibraryModel(library);
              sb.append(libraryModel != null ? libraryModel.getName() : library.getName()).append("' is used in ");
              for (int i = 0; i < types.size(); i++) {
                if (i > 0 && i == types.size() - 1) {
                  sb.append(" and in ");
                }
                else if (i > 0) {
                  sb.append(", in ");
                }
                String type = types.get(i);
                Collection<ProjectStructureElementUsage> usagesOfType = containerType2Usage.get(type);
                if (usagesOfType.size() > 1) {
                  sb.append(usagesOfType.size()).append(" ").append(StringUtil.decapitalize(StringUtil.pluralize(type)));
                }
                else {
                  sb.append(StringUtil.decapitalize(usagesOfType.iterator().next().getContainingElement().getPresentableText()));
                }
              }
              firstLibraryWithUsageName = library.getName();
              firstLibraryUsageDescription = sb.toString();
            }
            librariesWithUsages++;
          }
          toRemove.add(Pair.create(libraryElement, usages));
        }
        if (librariesWithUsages > 0) {
          String message;
          if (librariesWithUsages == 1) {
            message = firstLibraryUsageDescription + ".\nAre you sure you want to delete this library?";
          }
          else {
            message = ProjectBundle.message("libraries.remove.confirmation.text", firstLibraryWithUsageName, librariesWithUsages-1);
          }

          if (Messages.OK != Messages.showOkCancelDialog(myProject, message,
                                                         ProjectBundle.message("libraries.remove.confirmation.title", librariesWithUsages), Messages.getQuestionIcon())) {
            return false;
          }
        }

        for (Pair<LibraryProjectStructureElement, Collection<ProjectStructureElementUsage>> pair : toRemove) {
          for (ProjectStructureElementUsage usage : pair.getSecond()) {
            usage.removeSourceElement();
          }
          getModelProvider().getModifiableModel().removeLibrary(pair.getFirst().getLibrary());
          myContext.getDaemonAnalyzer().removeElement(pair.getFirst());
        }
        return true;
      }

      @Override
      public boolean canBeRemoved(@NotNull Collection<Library> libraries) {
        for (Library library : libraries) {
          LibraryTable table = library.getTable();
          if (table != null && !table.isEditable()) {
            return false;
          }
        }
        return true;
      }
    });
  }

  @Override
  @Nullable
  protected String getEmptySelectionString() {
    return "Select a library to view or edit its details here";
  }

  private class CopyLibraryAction extends AnAction {
    private CopyLibraryAction() {
      super(CommonBundle.message("button.copy"), CommonBundle.message("button.copy"), COPY_ICON);
    }

    @Override
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
        final Library lib = libsModel.createLibrary(newName, library.getKind());
        final LibraryEx.ModifiableModelEx model = libsModel.getLibraryEditor(lib).getModel();
        LibraryEditingUtil.copyLibrary(library, Collections.emptyMap(), model);
      }
    }

    @Override
    public void update(final AnActionEvent e) {
      if (myTree.getSelectionPaths() == null || myTree.getSelectionPaths().length != 1) {
        e.getPresentation().setEnabled(false);
      } else {
        e.getPresentation().setEnabled(getSelectedObject() instanceof LibraryImpl);
      }
    }
  }
}
