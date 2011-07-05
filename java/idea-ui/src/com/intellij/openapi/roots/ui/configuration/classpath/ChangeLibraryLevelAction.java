/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.configuration.classpath;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.impl.libraries.LibraryTableBase;
import com.intellij.openapi.roots.impl.libraries.LibraryTableImplUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.LibraryTableModifiableModelProvider;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.ChangeLibraryLevelDialog;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author nik
 */
class ChangeLibraryLevelAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.ui.configuration.classpath.ChangeLibraryLevelAction");
  private final ClasspathPanel myPanel;
  private final String myTargetTableLevel;

  public ChangeLibraryLevelAction(ClasspathPanel panel, final String targetTableName, String targetTableLevel) {
    myPanel = panel;
    myTargetTableLevel = targetTableLevel;
    getTemplatePresentation().setText(getActionName(isConvertingToModuleLibrary()) + " to " + targetTableName + "...");
  }

  @Override
  public void actionPerformed(AnActionEvent event) {
    final OrderEntry entry = myPanel.getSelectedEntry();
    if (!(entry instanceof LibraryOrderEntry)) return;
    final LibraryEx library = (LibraryEx)((LibraryOrderEntry)entry).getLibrary();
    if (library == null) return;

    final boolean copy = isConvertingToModuleLibrary();
    final Project project = myPanel.getProject();
    final VirtualFile baseDir = getBaseDir();
    final String libPath = baseDir != null ? baseDir.getPath() + "/lib" : "";
    boolean allowEmptyName = isConvertingToModuleLibrary() && library.getFiles(OrderRootType.CLASSES).length == 1;
    final String libraryName = allowEmptyName ? "" : StringUtil.notNullize(library.getName(), "Unnamed");
    final LibraryTableModifiableModelProvider provider = myPanel.getModifiableModelProvider(myTargetTableLevel);
    final ChangeLibraryLevelDialog dialog = new ChangeLibraryLevelDialog(myPanel.getComponent(), project, copy,
                                                                         libraryName, libPath, allowEmptyName, provider);
    dialog.show();
    if (!dialog.isOK()) {
      return;
    }

    final Set<File> fileToCopy = new LinkedHashSet<File>();
    final Map<String, String> copiedFiles = new HashMap<String, String>();
    final String targetDirectoryPath = dialog.getDirectoryForFilesPath();
    if (targetDirectoryPath != null) {
      for (OrderRootType type : OrderRootType.getAllTypes()) {
        for (VirtualFile root : library.getFiles(type)) {
          fileToCopy.add(VfsUtil.virtualToIoFile(PathUtil.getLocalFile(root)));
        }
      }
      if (!copyOrMoveFiles(copy, project, fileToCopy, targetDirectoryPath, copiedFiles)) {
        return;
      }
    }

    final Library copied = ((LibraryTableBase.ModifiableModelEx)provider.getModifiableModel()).createLibrary(dialog.getLibraryName(), library.getType());
    final LibraryEx.ModifiableModelEx model = (LibraryEx.ModifiableModelEx)copied.getModifiableModel();
    model.setProperties(library.getProperties());
    for (OrderRootType type : OrderRootType.getAllTypes()) {
      final String[] urls = library.getUrls(type);
      for (String url : urls) {
        final String protocol = VirtualFileManager.extractProtocol(url);
        if (protocol == null) continue;
        final String fullPath = VirtualFileManager.extractPath(url);
        final int sep = fullPath.indexOf(JarFileSystem.JAR_SEPARATOR);
        String localPath;
        String pathInJar;
        if (sep != -1) {
          localPath = fullPath.substring(0, sep);
          pathInJar = fullPath.substring(sep);
        }
        else {
          localPath = fullPath;
          pathInJar = "";
        }
        final String targetPath = copiedFiles.get(localPath);
        String targetUrl = targetPath != null ? VirtualFileManager.constructUrl(protocol, targetPath + pathInJar) : url;

        if (library.isJarDirectory(url, type)) {
          model.addJarDirectory(targetUrl, false, type);
        }
        else {
          model.addRoot(targetUrl, type);
        }
      }
    }

    AccessToken token = WriteAction.start();
    try {
      model.commit();
    }
    finally {
      token.finish();
    }
    myPanel.getRootModel().removeOrderEntry(entry);
    if (!isConvertingToModuleLibrary()) {
      myPanel.getRootModel().addLibraryEntry(copied);
    }
  }

  private static boolean copyOrMoveFiles(final boolean copy,
                                         final Project project,
                                         final Set<File> fileToCopy,
                                         @NotNull final String targetDirPath,
                                         final Map<String, String> copiedFiles) {
    final Ref<Boolean> finished = Ref.create(false);
    new Task.Modal(project, (copy ? "Copying" : "Moving") + " Library Files", true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        final File targetDir = new File(FileUtil.toSystemDependentName(targetDirPath));
        for (final File from : fileToCopy) {
          indicator.checkCanceled();
          final File to = FileUtil.findSequentNonexistentFile(targetDir, FileUtil.getNameWithoutExtension(from),
                                                              FileUtil.getExtension(from.getName()));
          try {
            if (from.isDirectory()) {
              if (copy) {
                FileUtil.copyDir(from, to);
              }
              else {
                FileUtil.moveDirWithContent(from, to);
              }
            }
            else {
              if (copy) {
                FileUtil.copy(from, to);
              }
              else {
                FileUtil.rename(from, to);
              }
            }
          }
          catch (IOException e) {
            final String actionName = getActionName(copy);
            final String message = "Cannot " + actionName.toLowerCase() + " file " + from.getAbsolutePath() + ": " + e.getMessage();
            Messages.showErrorDialog(project, message, "Cannot " + actionName);
            LOG.info(e);
            return;
          }

          new WriteAction() {
            @Override
            protected void run(Result result) throws Throwable {
              final VirtualFile virtualTo = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(to);
              if (virtualTo != null) {
                copiedFiles.put(FileUtil.toSystemIndependentName(from.getAbsolutePath()), virtualTo.getPath());
              }
              if (!copy) {
                final VirtualFile parent = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(from.getParentFile());
                if (parent != null) {
                  parent.refresh(false, false);
                }
              }
            }
          }.execute();
        }
        finished.set(true);
      }
    }.queue();
    if (!finished.get()) {
      return false;
    }
    return true;
  }

  private static String getActionName(boolean copy) {
    return copy ? "Copy" : "Move";
  }

  private boolean isConvertingToModuleLibrary() {
    return myTargetTableLevel.equals(LibraryTableImplUtil.MODULE_LEVEL);
  }

  @Nullable
  private VirtualFile getBaseDir() {
    if (isConvertingToModuleLibrary()) {
      final VirtualFile[] roots = myPanel.getRootModel().getContentRoots();
      if (roots.length > 0) {
        return roots[0];
      }
      final VirtualFile moduleFile = myPanel.getRootModel().getModule().getModuleFile();
      if (moduleFile != null) {
        return moduleFile.getParent();
      }
    }
    return myPanel.getProject().getBaseDir();
  }

  @Override
  public void update(AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    final OrderEntry entry = myPanel.getSelectedEntry();
    boolean enabled = false;
    if (entry instanceof LibraryOrderEntry) {
      final LibraryOrderEntry libraryOrderEntry = (LibraryOrderEntry)entry;
      if (libraryOrderEntry.getLibrary() != null) {
        boolean isFromModuleLibrary = libraryOrderEntry.isModuleLevel();
        boolean isToModuleLibrary = isConvertingToModuleLibrary();
        enabled = isFromModuleLibrary != isToModuleLibrary;
      }
    }
    presentation.setVisible(enabled);
    presentation.setEnabled(enabled);
  }
}
