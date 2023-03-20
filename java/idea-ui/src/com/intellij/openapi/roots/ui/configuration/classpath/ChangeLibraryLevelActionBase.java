// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration.classpath;

import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.impl.libraries.LibraryTableImplUtil;
import com.intellij.openapi.roots.impl.libraries.LibraryTypeServiceImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.configuration.LibraryTableModifiableModelProvider;
import com.intellij.openapi.roots.ui.configuration.libraries.LibraryEditingUtil;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.ChangeLibraryLevelDialog;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public abstract class ChangeLibraryLevelActionBase extends AnAction {
  private static final Logger LOG = Logger.getInstance(ChangeLibraryLevelActionBase.class);
  protected final Project myProject;
  protected final String myTargetTableLevel;
  protected final boolean myCopy;

  public ChangeLibraryLevelActionBase(@NotNull Project project, @NotNull String targetTableName, @NotNull String targetTableLevel, boolean copy) {
    myProject = project;
    myTargetTableLevel = targetTableLevel;
    myCopy = copy;
    getTemplatePresentation().setText(JavaUiBundle.message(myCopy ? "action.text.library.0.to.1.copy" : "action.text.library.0.to.1.move", targetTableName));
  }

  protected abstract LibraryTableModifiableModelProvider getModifiableTableModelProvider();

  protected abstract JComponent getParentComponent();

  @Nullable
  protected Library doCopy(LibraryEx library) {
    final VirtualFile baseDir = getBaseDir();
    final String libPath = baseDir != null ? baseDir.getPath() + "/lib" : "";
    final VirtualFile[] classesRoots = library.getFiles(OrderRootType.CLASSES);
    boolean allowEmptyName = isConvertingToModuleLibrary() && classesRoots.length == 1;
    final String libraryName =
      allowEmptyName ? "" : StringUtil.notNullize(library.getName(), LibraryTypeServiceImpl.suggestLibraryName(classesRoots));
    final LibraryTableModifiableModelProvider provider = getModifiableTableModelProvider();
    final ChangeLibraryLevelDialog dialog = new ChangeLibraryLevelDialog(getParentComponent(), myProject, myCopy,
                                                                         libraryName, libPath, allowEmptyName, provider);
    if (!dialog.showAndGet()) {
      return null;
    }

    final Set<File> fileToCopy = new LinkedHashSet<>();
    final Map<String, String> copiedFiles = new HashMap<>();
    final String targetDirectoryPath = dialog.getDirectoryForFilesPath();
    if (targetDirectoryPath != null) {
      for (OrderRootType type : OrderRootType.getAllTypes()) {
        for (VirtualFile root : library.getFiles(type)) {
          if (root.isInLocalFileSystem() || root.getFileSystem() instanceof ArchiveFileSystem) {
            fileToCopy.add(VfsUtilCore.virtualToIoFile(VfsUtil.getLocalFile(root)));
          }
        }
      }
      if (!copyOrMoveFiles(fileToCopy, targetDirectoryPath, copiedFiles)) {
        return null;
      }
    }

    final Library copied = provider.getModifiableModel().createLibrary(StringUtil.nullize(dialog.getLibraryName()), library.getKind());
    final LibraryEx.ModifiableModelEx model = (LibraryEx.ModifiableModelEx)copied.getModifiableModel();
    LibraryEditingUtil.copyLibrary(library, copiedFiles, model);

    /* Verification and bind JAR repositories are prohibited for global libraries */
    if (isConvertingToApplicationLibrary() && model.getProperties() instanceof RepositoryLibraryProperties repositoryLibraryProperties) {
      model.setProperties(repositoryLibraryProperties.cloneAndChange(properties -> {
        properties.disableVerification();
        properties.unbindRemoteRepository();
      }));
    }

    WriteAction.run(() -> model.commit());
    return copied;
  }

  private boolean copyOrMoveFiles(final Set<File> filesToProcess,
                                    @NotNull final String targetDirPath,
                                    final Map<String, String> copiedFiles) {
    final Ref<Boolean> finished = Ref.create(false);
    new Task.Modal(myProject, JavaUiBundle.message("progress.title.0.library.files", myCopy ? "Copying" : "Moving"), true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        final File targetDir = new File(FileUtil.toSystemDependentName(targetDirPath));
        for (final File from : filesToProcess) {
          indicator.checkCanceled();
          final File to = FileUtil.findSequentNonexistentFile(targetDir, FileUtilRt.getNameWithoutExtension(from.getName()),
                                                              FileUtilRt.getExtension(from.getName()));
          try {
            if (from.isDirectory()) {
              if (myCopy) {
                FileUtil.copyDir(from, to);
              }
              else {
                FileUtil.moveDirWithContent(from, to);
              }
            }
            else {
              if (myCopy) {
                FileUtil.copy(from, to);
              }
              else {
                FileUtil.rename(from, to);
              }
            }
          }
          catch (IOException e) {
            String message = JavaUiBundle.message(myCopy ? "dialog.message.cannot.file.copy" : "dialog.message.cannot.file.move",
                                                  from.getAbsolutePath(), e.getMessage());
            String title = JavaUiBundle.message(
              myCopy ? "dialog.title.cannot.change.library.0.copy" : "dialog.title.cannot.change.library.0.move");
            Messages.showErrorDialog(ChangeLibraryLevelActionBase.this.myProject, message, title);
            LOG.info(e);
            return;
          }

          copiedFiles.put(FileUtil.toSystemIndependentName(from.getAbsolutePath()), FileUtil.toSystemIndependentName(to.getAbsolutePath()));
        }
        finished.set(true);
      }
    }.queue();
    if (!finished.get()) {
      return false;
    }

    WriteAction.run(() -> {
      for (Map.Entry<String, String> entry : copiedFiles.entrySet()) {
        String fromPath = entry.getKey();
        String toPath = entry.getValue();
        LocalFileSystem.getInstance().refreshAndFindFileByPath(toPath);
        if (!myCopy) {
          final VirtualFile parent = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(fromPath).getParentFile());
          if (parent != null) {
            parent.refresh(false, false);
          }
        }
      }
    });
    return true;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    boolean enabled = isEnabled();
    presentation.setEnabledAndVisible(enabled);
  }

  @Nullable
  protected VirtualFile getBaseDir() {
    return myProject.getBaseDir();
  }

  protected boolean isConvertingToModuleLibrary() {
    return myTargetTableLevel.equals(LibraryTableImplUtil.MODULE_LEVEL);
  }

  private boolean isConvertingToApplicationLibrary() {
    return myTargetTableLevel.equals(LibraryTablesRegistrar.APPLICATION_LEVEL);
  }

  protected abstract boolean isEnabled();
}
