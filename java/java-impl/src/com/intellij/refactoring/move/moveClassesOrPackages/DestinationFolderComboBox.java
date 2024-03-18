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
import com.intellij.openapi.util.NlsSafe;
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
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public abstract class DestinationFolderComboBox extends ComboboxWithBrowseButton {
  private PsiDirectory myInitialTargetDirectory;
  private List<VirtualFile> mySourceRoots;
  private Project myProject;
  private boolean myLeaveInTheSameRoot;
  private Consumer<? super @NlsContexts.DialogMessage String> myUpdateErrorMessage;

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

  public void setData(@NotNull Project project, @Nullable PsiDirectory initialTargetDirectory, @NotNull EditorComboBox editorComboBox) {
    setData(project, initialTargetDirectory, __ -> {}, editorComboBox);
  }

  public void setData(@NotNull Project project,
                      @Nullable PsiDirectory targetDirectory,
                      @NotNull Consumer<? super @NlsContexts.DialogMessage String> errorMessageUpdater,
                      @NotNull EditorComboBox editorComboBox) {
    myProject = project;
    myInitialTargetDirectory = targetDirectory;
    mySourceRoots = getSourceRoots(project, targetDirectory);
    myUpdateErrorMessage = errorMessageUpdater;
    String leaveInSameSourceRoot = JavaBundle.message("leave.in.same.source.root.item");
    ComboboxSpeedSearch search = new ComboboxSpeedSearch(getComboBox(), null) {
      @Override
      protected String getElementText(Object element) {
        if (element == DirectoryChooser.ItemWrapper.NULL) return leaveInSameSourceRoot;
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
    search.setupListeners();
    getComboBox().setRenderer(SimpleListCellRenderer.<DirectoryChooser.ItemWrapper>create(
      (label, itemWrapper, index) -> {
      if (itemWrapper != DirectoryChooser.ItemWrapper.NULL && itemWrapper != null) {
        label.setIcon(itemWrapper.getIcon());
        label.setText(itemWrapper.getRelativeToProjectPath());
      }
      else {
        label.setText(leaveInSameSourceRoot);
      }
    }));
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final VirtualFile sourceRoot = targetDirectory != null ? fileIndex.getSourceRootForFile(targetDirectory.getVirtualFile()) : null;
    myLeaveInTheSameRoot = targetDirectory == null || sourceRoot != null && !fileIndex.isInLibrarySource(sourceRoot);
    addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        VirtualFile root = CommonMoveClassesOrPackagesUtil.chooseSourceRoot(
          new PackageWrapper(PsiManager.getInstance(project), getTargetPackage()),
          mySourceRoots,
          targetDirectory
        );
        if (root == null) return;
        selectRoot(project, root);
      }
    });
    final AtomicReference<VirtualFile> selection = new AtomicReference<>(sourceRoot);
    editorComboBox.addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(@NotNull DocumentEvent e) {
        JComboBox comboBox = getComboBox();
        DirectoryChooser.ItemWrapper selectedItem = (DirectoryChooser.ItemWrapper)comboBox.getSelectedItem();
        VirtualFile initialTargetDirectorySourceRoot = selectedItem != null && selectedItem != DirectoryChooser.ItemWrapper.NULL
                                                       ? fileIndex.getSourceRootForFile(selectedItem.getDirectory().getVirtualFile())
                                                       : sourceRoot;
        setComboboxModel(initialTargetDirectorySourceRoot, selection.get(), false);
      }
    });
    setComboboxModel(sourceRoot, selection.get(), false);
    getComboBox().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        Object selectedItem = getComboBox().getSelectedItem();
        record NonBlockingResult(@Nullable String error, @NlsSafe @Nullable String relativeSrcPath) {
        }
        ReadAction
          .nonBlocking(() -> {
            if (selectedItem instanceof DirectoryChooser.ItemWrapper && selectedItem != DirectoryChooser.ItemWrapper.NULL) {
              PsiDirectory directory = ((DirectoryChooser.ItemWrapper)selectedItem).getDirectory();
              if (directory != null) {
                selection.set(fileIndex.getSourceRootForFile(directory.getVirtualFile()));
              }
            }
            String relativeSrcPath = null;
            if (sourceRoot != null) {
              relativeSrcPath = ProjectUtil.calcRelativeToProjectPath(sourceRoot, myProject, true, false, true);
            }
            return new NonBlockingResult(getUpdateErrorMessage(fileIndex, selectedItem), relativeSrcPath);
          })
          .expireWith(DestinationFolderComboBox.this)
          .finishOnUiThread(ModalityState.stateForComponent(getComboBox()), result -> {
            myUpdateErrorMessage.accept(result.error);
            updateTooltipText(result.relativeSrcPath);
          }).submit(AppExecutorUtil.getAppExecutorService());
      }
    });
  }

  public void selectRoot(Project project, VirtualFile root) {
    final ComboBoxModel<DirectoryChooser.ItemWrapper> model = getComboBox().getModel();
    List<DirectoryChooser.ItemWrapper> items = new ArrayList<>(model.getSize());
    for (int i = 0; i < model.getSize(); i++) items.add(model.getElementAt(i));
    record NonBlockingResult(@NotNull VirtualFile root, @Nullable DirectoryChooser.ItemWrapper item) {
    }
    ReadAction
      .nonBlocking(() -> new NonBlockingResult(
        root,
        ContainerUtil.find(items, item ->
          item != DirectoryChooser.ItemWrapper.NULL
          && Comparing.equal(ProjectRootManager.getInstance(project).getFileIndex().getSourceRootForFile(item.getDirectory().getVirtualFile()), root)
        )
      ))
      .expireWith(this)
      .finishOnUiThread(ModalityState.current(), result -> {
        if (result.item != null) {
          getComboBox().setSelectedItem(result.item);
          getComboBox().repaint();
        }
        setComboboxModel(result.root, result.root, true);
      })
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  @NotNull
  protected List<VirtualFile> getSourceRoots(@NotNull Project project, @Nullable PsiDirectory initialTargetDirectory) {
    return JavaProjectRootsUtil.getSuitableDestinationSourceRoots(project);
  }

  private void updateTooltipText(@NlsSafe @Nullable String relativeSourceRootPath) {
    JComboBox<?> comboBox = getComboBox();
    if (relativeSourceRootPath != null && comboBox.getSelectedItem() == DirectoryChooser.ItemWrapper.NULL) {
      comboBox.setToolTipText(relativeSourceRootPath);
    }
    else {
      comboBox.setToolTipText(null);
    }
  }

  @Nullable
  public MoveDestination selectDirectory(final PackageWrapper targetPackage, final boolean showChooserWhenDefault) {
    final DirectoryChooser.ItemWrapper selectedItem = (DirectoryChooser.ItemWrapper)getComboBox().getSelectedItem();
    if (selectedItem == null || selectedItem == DirectoryChooser.ItemWrapper.NULL) {
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

  @Nls
  @Nullable
  private String getUpdateErrorMessage(ProjectFileIndex fileIndex, Object selectedItem) {
    if (myInitialTargetDirectory != null && selectedItem instanceof DirectoryChooser.ItemWrapper && selectedItem !=
                                                                                                    DirectoryChooser.ItemWrapper.NULL) {
      final PsiDirectory directory = ((DirectoryChooser.ItemWrapper)selectedItem).getDirectory();
      final boolean isSelectionInTestSourceContent = fileIndex.isInTestSourceContent(directory.getVirtualFile());
      final boolean inTestSourceContent = fileIndex.isInTestSourceContent(myInitialTargetDirectory.getVirtualFile());
      if (isSelectionInTestSourceContent != inTestSourceContent) {
        if (inTestSourceContent && reportBaseInTestSelectionInSource()) {
          return JavaBundle.message("destination.combo.source.root.not.expected.conflict");
        }
        if (isSelectionInTestSourceContent && reportBaseInSourceSelectionInTest()) {
          return JavaBundle.message("destination.combo.test.root.not.expected.conflict");
        }
      }
    }
    return null;
  }

  private void setComboboxModel(@Nullable VirtualFile sourceRoot, @Nullable VirtualFile oldSelection, boolean forceIncludeAll) {
    myAlarm.cancelAllRequests();
    myAlarm.addRequest(() -> setComboboxModelInternal(sourceRoot, oldSelection, forceIncludeAll), 300, ModalityState.stateForComponent(this));
  }

  private static final DirectoryChooser.ItemWrapper NO_UPDATE_REQUIRED = DirectoryChooser.ItemWrapper.NULL;

  private void setComboboxModelInternal(@Nullable VirtualFile sourceRoot, @Nullable VirtualFile oldSelection, boolean forceIncludeAll) {
    if (myProject.isDisposed()) return;
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    JComboBox<DirectoryChooser.ItemWrapper> comboBox = getComboBox();
    final ComboBoxModel<DirectoryChooser.ItemWrapper> model = comboBox.getModel();
    DirectoryChooser.ItemWrapper selectedItem = (DirectoryChooser.ItemWrapper)comboBox.getSelectedItem();
    final ArrayList<DirectoryChooser.ItemWrapper> items = new ArrayList<>();

    record NonBlockingSelectionResult(
      @Nullable DirectoryChooser.ItemWrapper selection,
      @Nullable String message,
      @NlsSafe @Nullable String relativeSrcPath
    ) {
    }
    ReadAction
      .nonBlocking(() -> {
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
          if (Comparing.equal(sourceRootForFile, sourceRoot)) {
            initial = itemWrapper;
          }
          else if (Comparing.equal(sourceRootForFile, oldSelection)) {
            oldOne = itemWrapper;
          }
        }
        if (myLeaveInTheSameRoot) {
          items.add(DirectoryChooser.ItemWrapper.NULL);
        }
        final DirectoryChooser.ItemWrapper selection = chooseSelection(sourceRoot, fileIndex, items, initial, oldOne);

        String relativeSrcPath = sourceRoot != null
                                 ? ProjectUtil.calcRelativeToProjectPath(sourceRoot, myProject, true, false, true)
                                 : null;
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
            return new NonBlockingSelectionResult(NO_UPDATE_REQUIRED, null, relativeSrcPath);
          }
        }
        return new NonBlockingSelectionResult(selection, getUpdateErrorMessage(fileIndex, selection), relativeSrcPath);
      })
      .expireWith(this)
      .finishOnUiThread(ModalityState.stateForComponent(this), result -> {
        DirectoryChooser.ItemWrapper selection = result.selection;
        if (selection == NO_UPDATE_REQUIRED) return;
        myUpdateErrorMessage.accept(result.message);
        items.sort((o1, o2) -> {
          if (o1 == o2) return 0;
          if (o1 == DirectoryChooser.ItemWrapper.NULL) return -1;
          if (o2 == DirectoryChooser.ItemWrapper.NULL) return 1;
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
        updateTooltipText(result.relativeSrcPath);
      })
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  @Nullable
  private static DirectoryChooser.ItemWrapper chooseSelection(final VirtualFile initialTargetDirectorySourceRoot,
                                                              final ProjectFileIndex fileIndex,
                                                              final ArrayList<DirectoryChooser.ItemWrapper> items,
                                                              final DirectoryChooser.ItemWrapper initial,
                                                              final DirectoryChooser.ItemWrapper oldOne) {
    if (initial != null || ((initialTargetDirectorySourceRoot == null || items.size() > 2) && items.contains(
      DirectoryChooser.ItemWrapper.NULL)) || items.isEmpty()) {
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
        if (items.contains(DirectoryChooser.ItemWrapper.NULL)) return DirectoryChooser.ItemWrapper.NULL;
      }
    }
    return items.get(0);
  }

  private static boolean areItemsEquivalent(DirectoryChooser.ItemWrapper oItem, DirectoryChooser.ItemWrapper itemWrapper) {
    if (oItem == DirectoryChooser.ItemWrapper.NULL || itemWrapper == DirectoryChooser.ItemWrapper.NULL) {
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
