// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.impl.ProjectViewSelectInTarget;
import com.intellij.ide.projectView.impl.ProjectViewPane;
import com.intellij.internal.InternalActionsBundle;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.GlobalUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AttachDirectoryUtils {
  public static void chooseAndAddDirectoriesWithUndo(@NotNull Project project, @Nullable Consumer<? super List<VirtualFile>> callback) {
    Module[] modules = ModuleManager.getInstance(project).getModules();
    if (modules.length == 0) return;
    Module module = modules[0];

    FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createAllButJarContentsDescriptor();
    FileChooser.chooseFiles(descriptor, project, null, files -> {
      addAndSelectDirectoriesWithUndo(module, files);
      if (callback != null) callback.consume(files);
    });
  }

  public static void addDirectoriesWithUndo(@NotNull Project project, @NotNull List<VirtualFile> roots) {
    Module[] modules = ModuleManager.getInstance(project).getModules();
    if (modules.length == 0) return;
    Module module = modules[0];
    addAndSelectDirectoriesWithUndo(module, roots);
  }

  private static void addAndSelectDirectoriesWithUndo(@NotNull Module module, @NotNull List<VirtualFile> roots) {
    addRemoveEntriesWithUndo(module, roots, true);
    VirtualFile file = ContainerUtil.getFirstItem(roots);
    if (file != null) {
      ProjectViewSelectInTarget.select(module.getProject(), file, ProjectViewPane.ID, null, file, true);
    }
  }

  public static void addRemoveEntriesWithUndo(@NotNull Module module, @NotNull List<VirtualFile> roots, boolean add) {
    Project project = module.getProject();
    List<VirtualFile> adjustedRoots = ContainerUtil.newArrayList(JBIterable.from(roots)
      .filter(o -> add == (ModuleUtilCore.findModuleForFile(o, project) == null))
      .filterMap(o -> o.isDirectory() ? o : o.getParent())
      .unique());
    adjustedRoots.removeIf(r -> ContainerUtil.exists(adjustedRoots, r2 -> VfsUtilCore.isAncestor(r2, r, true)));

    if (adjustedRoots.isEmpty()) return;

    class MyUndoable extends GlobalUndoableAction {
      @Override
      public void undo() {
        addRemoveEntriesInner(module, adjustedRoots, !add);
      }

      @Override
      public void redo() {
        addRemoveEntriesInner(module, adjustedRoots, add);
      }
    }
    String title = IdeBundle.message("command.name.choice.attach.detach", add ? 0 : 1, getDisplayName(adjustedRoots));
    WriteCommandAction.writeCommandAction(project)
      .withName(title)
      .withUndoConfirmationPolicy(UndoConfirmationPolicy.REQUEST_CONFIRMATION).run(()-> {
        MyUndoable undoable = new MyUndoable();
        undoable.redo();
        UndoManager.getInstance(project).undoableActionPerformed(undoable);
    });
  }

  public static String getDisplayName(@NotNull List<VirtualFile> roots) {
    return roots.size() == 1 ? "directory '" + roots.get(0).getName() + "'" :
           roots.size() + " " + StringUtil.pluralize("directory", roots.size());
  }

  private static void addRemoveEntriesInner(@NotNull Module module, @NotNull List<VirtualFile> files, boolean add) {
    ModuleRootModificationUtil.updateModel(module, model -> {
      if (add) {
        for (VirtualFile file : files) {
          model.addContentEntry(file);
        }
      }
      else {
        for (ContentEntry entry : model.getContentEntries()) {
          if (files.contains(entry.getFile())) {
            model.removeContentEntry(entry);
          }
        }
      }
    });
  }

  @NotNull
  public static List<VirtualFile> getAttachedDirectories(@NotNull Project project) {
    Module[] modules = ModuleManager.getInstance(project).getModules();
    return modules.length != 0 ? Arrays.asList(ModuleRootManager.getInstance(modules[0]).getContentRoots()) : Collections.emptyList();
  }
}
