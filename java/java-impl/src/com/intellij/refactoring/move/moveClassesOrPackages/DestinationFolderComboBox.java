// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.ide.util.DirectoryChooser;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.JavaProjectRootsUtil;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.ComboBoxWithWidePopup;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pass;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.MoveDestination;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.util.CommonMoveClassesOrPackagesUtil;
import com.intellij.ui.*;
import com.intellij.util.Alarm;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Consumer;

public abstract class DestinationFolderComboBox extends ComboboxWithBrowseButton {
  private static final DirectoryChooser.ItemWrapper NULL_WRAPPER = new DirectoryChooser.ItemWrapper(null, null);

  private PsiDirectory myInitialTargetDirectory;
  private List<VirtualFile> mySourceRoots;
  private Project myProject;
  private boolean myLeaveInTheSameRoot;
  private Consumer<String> myUpdateErrorMessage;

  private final Alarm myAlarm = new Alarm();
  public DestinationFolderComboBox() {
    super(new ComboBoxWithWidePopup());
  }

  public abstract String getTargetPackage();

  protected boolean reportBaseInTestSelectionInSource() {
    return false;
  }

  protected boolean reportBaseInSourceSelectionInTest() {
    return false;
  }

  @Override
  public void removeNotify() {
    super.removeNotify();
    Disposer.dispose(myAlarm);
  }

  public void setData(final Project project,
                    final PsiDirectory initialTargetDirectory,
                    final EditorComboBox editorComboBox) {
    setData(project, initialTargetDirectory, new Pass<>() {
      @Override
      public void pass(String s) {
      }
    }, editorComboBox);
  }

  public void setData(final Project project,
                      final PsiDirectory initialTargetDirectory,
                      final Consumer<@NlsContexts.DialogMessage String> errorMessageUpdater,
                      final EditorComboBox editorComboBox) {
    myInitialTargetDirectory = initialTargetDirectory;
    mySourceRoots = getSourceRoots(project, initialTargetDirectory);
    myProject = project;
    myUpdateErrorMessage = errorMessageUpdater;
    String leaveInSameSourceRoot = JavaBundle.message("leave.in.same.source.root.item");
    new ComboboxSpeedSearch(getComboBox()) {
      @Override
      protected String getElementText(Object element) {
        if (element == NULL_WRAPPER) return leaveInSameSourceRoot;
        if (element instanceof DirectoryChooser.ItemWrapper) {
          final VirtualFile virtualFile = ((DirectoryChooser.ItemWrapper)element).getDirectory().getVirtualFile();
          final Module module = ModuleUtilCore.findModuleForFile(virtualFile, project);
          if (module != null) {
            return module.getName();
          }
        }
        return super.getElementText(element);
      }
    };
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    getComboBox().setRenderer(SimpleListCellRenderer.<DirectoryChooser.ItemWrapper>create(
      (label, itemWrapper, index) -> {
      if (itemWrapper != NULL_WRAPPER && itemWrapper != null) {
        label.setIcon(itemWrapper.getIcon(fileIndex));

        label.setText(itemWrapper.getRelativeToProjectPath());
      }
      else {
        label.setText(leaveInSameSourceRoot);
      }
    }));
    final VirtualFile initialSourceRoot =
      initialTargetDirectory != null ? fileIndex.getSourceRootForFile(initialTargetDirectory.getVirtualFile()) : null;
    final VirtualFile[] selection = new VirtualFile[]{initialSourceRoot};
    myLeaveInTheSameRoot = initialTargetDirectory == null ||
                           initialSourceRoot != null && !fileIndex.isInLibrarySource(initialSourceRoot);
    addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        VirtualFile root = CommonMoveClassesOrPackagesUtil
          .chooseSourceRoot(new PackageWrapper(PsiManager.getInstance(project), getTargetPackage()), mySourceRoots, initialTargetDirectory);
        if (root == null) return;
        final ComboBoxModel model = getComboBox().getModel();
        for (int i = 0; i < model.getSize(); i++) {
          DirectoryChooser.ItemWrapper item = (DirectoryChooser.ItemWrapper)model.getElementAt(i);
          if (item != NULL_WRAPPER && Comparing.equal(fileIndex.getSourceRootForFile(item.getDirectory().getVirtualFile()), root)) {
            getComboBox().setSelectedItem(item);
            getComboBox().repaint();
            return;
          }
        }
        setComboboxModel(root, root, true);
      }
    });

    editorComboBox.addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(@NotNull DocumentEvent e) {
        JComboBox comboBox = getComboBox();
        DirectoryChooser.ItemWrapper selectedItem = (DirectoryChooser.ItemWrapper)comboBox.getSelectedItem();
        VirtualFile initialTargetDirectorySourceRoot = selectedItem != null && selectedItem != NULL_WRAPPER
                                                       ? fileIndex.getSourceRootForFile(selectedItem.getDirectory().getVirtualFile())
                                                       : initialSourceRoot;
        setComboboxModel(initialTargetDirectorySourceRoot, selection[0], false);
      }
    });
    setComboboxModel(initialSourceRoot, selection[0], false);
    getComboBox().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        Object selectedItem = getComboBox().getSelectedItem();
        updateErrorMessage(fileIndex, selectedItem);
        if (selectedItem instanceof DirectoryChooser.ItemWrapper && selectedItem != NULL_WRAPPER) {
          PsiDirectory directory = ((DirectoryChooser.ItemWrapper)selectedItem).getDirectory();
          if (directory != null) {
            selection[0] = fileIndex.getSourceRootForFile(directory.getVirtualFile());
          }
        }
        updateTooltipText(initialSourceRoot);
      }
    });
  }

  @NotNull
  protected List<VirtualFile> getSourceRoots(Project project, PsiDirectory initialTargetDirectory) {
    return JavaProjectRootsUtil.getSuitableDestinationSourceRoots(project);
  }

  private void updateTooltipText(VirtualFile initialSourceRoot) {
    JComboBox<?> comboBox = getComboBox();
    if (initialSourceRoot != null && comboBox.getSelectedItem() == NULL_WRAPPER) {
      comboBox.setToolTipText(ProjectUtil.calcRelativeToProjectPath(initialSourceRoot, myProject, true, false, true));
    }
    else {
      comboBox.setToolTipText(null);
    }
  }

  @Nullable
  public MoveDestination selectDirectory(final PackageWrapper targetPackage, final boolean showChooserWhenDefault) {
    final DirectoryChooser.ItemWrapper selectedItem = (DirectoryChooser.ItemWrapper)getComboBox().getSelectedItem();
    if (selectedItem == null || selectedItem == NULL_WRAPPER) {
      return new MultipleRootsMoveDestination(targetPackage);
    }
    final PsiDirectory selectedPsiDirectory = selectedItem.getDirectory();
    Project project = targetPackage.getManager().getProject();
    VirtualFile selectedDestination = ProjectRootManager.getInstance(project).getFileIndex().getSourceRootForFile(selectedPsiDirectory.getVirtualFile());
    if (showChooserWhenDefault &&
        myInitialTargetDirectory != null && Comparing.equal(selectedDestination, myInitialTargetDirectory.getVirtualFile()) &&
        mySourceRoots.size() > 1) {
      selectedDestination = CommonMoveClassesOrPackagesUtil.chooseSourceRoot(targetPackage, mySourceRoots, myInitialTargetDirectory);
    }
    if (selectedDestination == null) return null;
    return new AutocreatingSingleSourceRootMoveDestination(targetPackage, selectedDestination);
  }

  private void updateErrorMessage(ProjectFileIndex fileIndex, Object selectedItem) {
    myUpdateErrorMessage.accept(null);
    if (myInitialTargetDirectory != null && selectedItem instanceof DirectoryChooser.ItemWrapper && selectedItem != NULL_WRAPPER) {
      final PsiDirectory directory = ((DirectoryChooser.ItemWrapper)selectedItem).getDirectory();
      final boolean isSelectionInTestSourceContent = fileIndex.isInTestSourceContent(directory.getVirtualFile());
      final boolean inTestSourceContent = fileIndex.isInTestSourceContent(myInitialTargetDirectory.getVirtualFile());
      if (isSelectionInTestSourceContent != inTestSourceContent) {
        if (inTestSourceContent && reportBaseInTestSelectionInSource()) {
          myUpdateErrorMessage.accept(JavaBundle.message("destination.combo.source.root.not.expected.conflict"));
        }

        if (isSelectionInTestSourceContent && reportBaseInSourceSelectionInTest()) {
          myUpdateErrorMessage.accept(JavaBundle.message("destination.combo.test.root.not.expected.conflict"));
        }
      }
    }
  }

  private void setComboboxModel(final VirtualFile initialTargetDirectorySourceRoot,
                                final VirtualFile oldSelection,
                                final boolean forceIncludeAll) {
    myAlarm.cancelAllRequests();
    myAlarm.addRequest(() -> setComboboxModelInternal(initialTargetDirectorySourceRoot, oldSelection, forceIncludeAll), 300, ModalityState.stateForComponent(this));
  }

  private static final DirectoryChooser.ItemWrapper NO_UPDATE_REQUIRED = new DirectoryChooser.ItemWrapper(null, null);
  private void setComboboxModelInternal(final VirtualFile initialTargetDirectorySourceRoot,
                                        final VirtualFile oldSelection,
                                        final boolean forceIncludeAll) {
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    JComboBox<DirectoryChooser.ItemWrapper> comboBox = getComboBox();
    final ComboBoxModel<DirectoryChooser.ItemWrapper> model = comboBox.getModel();
    DirectoryChooser.ItemWrapper selectedItem = (DirectoryChooser.ItemWrapper)comboBox.getSelectedItem();
    final ArrayList<DirectoryChooser.ItemWrapper> items = new ArrayList<>();

    ReadAction.nonBlocking(() -> {
        final LinkedHashSet<PsiDirectory> targetDirectories = new LinkedHashSet<>();
        final HashMap<PsiDirectory, String> pathsToCreate = new HashMap<>();
        CommonMoveClassesOrPackagesUtil
          .buildDirectoryList(new PackageWrapper(PsiManager.getInstance(myProject), getTargetPackage()), mySourceRoots, targetDirectories,
                              pathsToCreate);
        if (!forceIncludeAll && targetDirectories.size() > pathsToCreate.size()) {
          targetDirectories.removeAll(pathsToCreate.keySet());
        }

        DirectoryChooser.ItemWrapper initial = null;
        DirectoryChooser.ItemWrapper oldOne = null;
        for (PsiDirectory targetDirectory : targetDirectories) {
          DirectoryChooser.ItemWrapper itemWrapper = new DirectoryChooser.ItemWrapper(targetDirectory, pathsToCreate.get(targetDirectory));
          items.add(itemWrapper);
          final VirtualFile sourceRootForFile = fileIndex.getSourceRootForFile(targetDirectory.getVirtualFile());
          if (Comparing.equal(sourceRootForFile, initialTargetDirectorySourceRoot)) {
            initial = itemWrapper;
          }
          else if (Comparing.equal(sourceRootForFile, oldSelection)) {
            oldOne = itemWrapper;
          }
        }
        if (myLeaveInTheSameRoot) {
          items.add(NULL_WRAPPER);
        }
        final DirectoryChooser.ItemWrapper selection = chooseSelection(initialTargetDirectorySourceRoot, fileIndex, items, initial, oldOne);

        if (model instanceof CollectionComboBoxModel) {
          boolean sameModel = model.getSize() == items.size();
          if (sameModel) {
            for (int i = 0; i < items.size(); i++) {
              final DirectoryChooser.ItemWrapper oldItem = model.getElementAt(i);
              final DirectoryChooser.ItemWrapper itemWrapper = items.get(i);
              if (!areItemsEquivalent(oldItem, itemWrapper)) {
                sameModel = false;
                break;
              }
            }
          }
          if (sameModel && areItemsEquivalent(selectedItem, selection)) {
            return NO_UPDATE_REQUIRED;
          }
        }
        return selection;
      })
      .finishOnUiThread(ModalityState.stateForComponent(this), selection -> {
        if (selection == NO_UPDATE_REQUIRED) return;
        updateErrorMessage(fileIndex, selection);
        items.sort((o1, o2) -> {
          if (o1 == NULL_WRAPPER) return -1;
          if (o2 == NULL_WRAPPER) return 1;
          return o1.getRelativeToProjectPath().compareToIgnoreCase(o2.getRelativeToProjectPath());
        });
        comboBox.setModel(new CollectionComboBoxModel<>(items, selection));

        final Component root = SwingUtilities.getRoot(comboBox);
        if (root instanceof Window) {
          final Dimension preferredSize = root.getPreferredSize();
          if (preferredSize.getWidth() > root.getSize().getWidth()) {
            root.setSize(preferredSize);
          }
        }
        updateTooltipText(initialTargetDirectorySourceRoot);
      })
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  @Nullable
  private static DirectoryChooser.ItemWrapper chooseSelection(final VirtualFile initialTargetDirectorySourceRoot,
                                                              final ProjectFileIndex fileIndex,
                                                              final ArrayList<DirectoryChooser.ItemWrapper> items,
                                                              final DirectoryChooser.ItemWrapper initial,
                                                              final DirectoryChooser.ItemWrapper oldOne) {
    if (initial != null || ((initialTargetDirectorySourceRoot == null || items.size() > 2) && items.contains(NULL_WRAPPER)) || items.isEmpty()) {
      return initial;
    }
    else {
      if (oldOne != null) {
        return oldOne;
      }
      else if (initialTargetDirectorySourceRoot != null) {
        final boolean inTest = fileIndex.isInTestSourceContent(initialTargetDirectorySourceRoot);
        for (DirectoryChooser.ItemWrapper item : items) {
          PsiDirectory directory = item.getDirectory();
          if (directory != null) {
            final VirtualFile virtualFile = directory.getVirtualFile();
            if (fileIndex.isInTestSourceContent(virtualFile) == inTest) {
              return item;
            }
          }
        }
        if (items.contains(NULL_WRAPPER)) return NULL_WRAPPER;
      }
    }
    return items.get(0);
  }

  private static boolean areItemsEquivalent(DirectoryChooser.ItemWrapper oItem, DirectoryChooser.ItemWrapper itemWrapper) {
    if (oItem == NULL_WRAPPER || itemWrapper == NULL_WRAPPER) {
      if (oItem != itemWrapper) {
        return false;
      }
      return true;
    }
    if (oItem == null) return itemWrapper == null;
    if (itemWrapper == null) return false;
    if (oItem.getDirectory() != itemWrapper.getDirectory()) {
      return false;
    }
    return true;
  }

  public static boolean isAccessible(final Project project,
                                     final VirtualFile virtualFile,
                                     final VirtualFile targetVirtualFile) {
    final boolean inTestSourceContent = ProjectRootManager.getInstance(project).getFileIndex().isInTestSourceContent(virtualFile);
    final Module module = ModuleUtilCore.findModuleForFile(virtualFile, project);
    if (targetVirtualFile != null &&
        module != null &&
        !GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, inTestSourceContent).contains(targetVirtualFile)) {
      return false;
    }
    return true;
  }
}
