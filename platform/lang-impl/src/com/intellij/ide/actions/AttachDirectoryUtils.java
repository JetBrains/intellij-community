// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.impl.ProjectViewSelectInTarget;
import com.intellij.ide.projectView.impl.ProjectViewPane;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.GlobalUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.components.impl.stores.IProjectStore;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.project.ProjectKt;
import com.intellij.util.Consumer;
import com.intellij.util.ModalityUiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class AttachDirectoryUtils {
  public static void chooseAndAddDirectoriesWithUndo(@NotNull Project project, @Nullable Consumer<? super List<VirtualFile>> callback) {
    Module module = getAttachTargetModule(project);
    FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createAllButJarContentsDescriptor();
    FileChooser.chooseFiles(descriptor, project, null, files -> {
      addAndSelectDirectoriesWithUndo(project, module, files);
      if (callback != null) callback.consume(files);
    });
  }

  public static void addDirectoriesWithUndo(@NotNull Project project, @NotNull List<? extends VirtualFile> roots) {
    Module module = getAttachTargetModule(project);
    if (module == null) return;
    addAndSelectDirectoriesWithUndo(project, module, roots);
  }

  private static void addAndSelectDirectoriesWithUndo(@NotNull Project project, @Nullable Module module, @NotNull List<? extends VirtualFile> roots) {
    addRemoveEntriesWithUndo(project, module, roots, true);
    VirtualFile file = ContainerUtil.getFirstItem(roots);
    if (file != null) {
      ModalityUiUtil.invokeLaterIfNeeded(ModalityState.defaultModalityState(), () ->
        ProjectViewSelectInTarget.select(project, file, ProjectViewPane.ID, null, file, true)
      );
    }
  }

  public static void addRemoveEntriesWithUndo(@NotNull Project project, @Nullable Module module, @NotNull List<? extends VirtualFile> roots, boolean add) {
    List<VirtualFile> adjustedRoots = ContainerUtil.newArrayList(JBIterable.from(roots)
      .filter(o -> add == (ModuleUtilCore.findModuleForFile(o, project) == null))
      .filterMap(o -> o.isDirectory() ? o : o.getParent())
      .unique());
    adjustedRoots.removeIf(r -> ContainerUtil.exists(adjustedRoots, r2 -> VfsUtilCore.isAncestor(r2, r, true)));

    if (adjustedRoots.isEmpty()) return;

    final class MyUndoable extends GlobalUndoableAction {
      @Override
      public void undo() {
        addRemoveEntriesInner(project, module, adjustedRoots, !add);
      }

      @Override
      public void redo() {
        addRemoveEntriesInner(project, module, adjustedRoots, add);
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

  public static void excludeEntriesWithUndo(@NotNull Project project, @NotNull List<? extends VirtualFile> roots, boolean exclude) {
    Module module = getAttachTargetModule(project);
    if (module != null) {
      excludeEntriesWithUndo(module, roots, exclude);
    }
  }
  public static void excludeEntriesWithUndo(@NotNull Module module, @NotNull List<? extends VirtualFile> roots, boolean exclude) {
    if (roots.isEmpty()) return;
    Project project = module.getProject();

    final class MyUndoable extends GlobalUndoableAction {
      @Override
      public void undo() {
        excludeEntriesInner(module, roots, !exclude);
      }

      @Override
      public void redo() {
        excludeEntriesInner(module, roots, exclude);
      }
    }
    String title = IdeBundle.message("command.name.choice.exclude.include", exclude ? 0 : 1, getDisplayName(roots));
    WriteCommandAction.writeCommandAction(project)
      .withName(title)
      .withUndoConfirmationPolicy(UndoConfirmationPolicy.REQUEST_CONFIRMATION).run(()-> {
        MyUndoable undoable = new MyUndoable();
        undoable.redo();
        UndoManager.getInstance(project).undoableActionPerformed(undoable);
    });
  }

  public static String getDisplayName(@NotNull List<? extends VirtualFile> roots) {
    return roots.size() == 1 ? "directory '" + roots.get(0).getName() + "'" :
           roots.size() + " " + StringUtil.pluralize("directory", roots.size());
  }

  private static void addRemoveEntriesInner(@NotNull Project project, @Nullable Module module, @NotNull List<? extends VirtualFile> files, boolean add) {
    if (module == null) module = getAttachTargetModule(project);
    if (module == null) module = createDefaultModule(project);
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

  private static void excludeEntriesInner(@NotNull Module module, @NotNull List<? extends VirtualFile> files, boolean exclude) {
    ModuleRootModificationUtil.updateModel(module, model -> {
      for (ContentEntry entry : model.getContentEntries()) {
        VirtualFile root = entry.getFile();
        if (root == null) continue;
        for (VirtualFile file : files) {
          if (VfsUtilCore.isAncestor(root, file, true)) {
            if (exclude) {
              entry.addExcludeFolder(file);
            }
            else {
              entry.removeExcludeFolder(file.getUrl());
            }
          }
        }
      }
    });
  }



  private static @Nullable Module getAttachTargetModule(@NotNull Project project) {
    Module[] modules = ModuleManager.getInstance(project).getModules();
    return modules.length == 0 ? null : modules[0];
  }

  private static @NotNull Module createDefaultModule(@NotNull Project project) {
    IProjectStore store = ProjectKt.getStateStore(project);
    Path proDir = store.getProjectFilePath().getParent();
    Path modulePath = proDir.resolve(project.getName() + ".iml");
    ModuleType<?> defType = ModuleTypeManager.getInstance().getDefaultModuleType();
    return ModuleManager.getInstance(project).newModule(modulePath, defType.getId());
  }

  public static @NotNull List<VirtualFile> getAttachedDirectories(@NotNull Project project) {
    Module module = getAttachTargetModule(project);
    return module != null ? Arrays.asList(ModuleRootManager.getInstance(module).getContentRoots()) : Collections.emptyList();
  }
}
