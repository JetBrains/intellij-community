// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.actions;

import com.intellij.ide.SaveAndSyncHandler;
import com.intellij.ide.projectView.impl.ProjectRootsUtil;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;


public abstract class MarkRootActionBase extends DumbAwareAction {
  public MarkRootActionBase() {
  }

  public MarkRootActionBase(@Nullable @NlsActions.ActionText String text) {
    super(text);
  }

  public MarkRootActionBase(@Nullable @NlsActions.ActionText String text,
                            @Nullable @NlsActions.ActionDescription String description,
                            @Nullable Icon icon) {
    super(text, description, icon);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    final Module module = getModule(e, files);
    if (module == null) {
      return;
    }
    modifyRoots(e, module, files);
  }

  protected void modifyRoots(@NotNull AnActionEvent e, final @NotNull Module module, VirtualFile @NotNull [] files) {
    modifyRoots(module, files);
  }

  protected void modifyRoots(@NotNull Module module, VirtualFile @NotNull [] files) {
    final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
    for (VirtualFile file : files) {
      ContentEntry entry = findContentEntry(model, file);
      if (entry != null) {
        final SourceFolder[] sourceFolders = entry.getSourceFolders();
        for (SourceFolder sourceFolder : sourceFolders) {
          if (Comparing.equal(sourceFolder.getFile(), file)) {
            entry.removeSourceFolder(sourceFolder);
            break;
          }
        }
        modifyRoots(file, entry);
      }
    }
    commitModel(module, model);
  }

  static void commitModel(@NotNull Module module, ModifiableRootModel model) {
    ApplicationManager.getApplication().runWriteAction(model::commit);
    SaveAndSyncHandler.getInstance().scheduleProjectSave(module.getProject());
  }

  protected abstract void modifyRoots(VirtualFile file, ContentEntry entry);

  public static @Nullable ContentEntry findContentEntry(@NotNull ModuleRootModel model, @NotNull VirtualFile vFile) {
    final ContentEntry[] contentEntries = model.getContentEntries();
    for (ContentEntry contentEntry : contentEntries) {
      final VirtualFile contentEntryFile = contentEntry.getFile();
      if (contentEntryFile != null && VfsUtilCore.isAncestor(contentEntryFile, vFile, false)) {
        return contentEntry;
      }
    }
    return null;
  }

  @Override
  public final @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    RootsSelection selection = getSelection(e);
    doUpdate(e, selection.myModule, selection);
  }

  protected void doUpdate(@NotNull AnActionEvent e, @Nullable Module module, @NotNull RootsSelection selection) {
    boolean enabled = module != null && (!selection.mySelectedRoots.isEmpty() || !selection.mySelectedDirectories.isEmpty())
                      && selection.mySelectedExcludeRoots.isEmpty() && isEnabled(selection, module);
    e.getPresentation().setEnabledAndVisible(enabled);
  }

  protected abstract boolean isEnabled(@NotNull RootsSelection selection, @NotNull Module module);

  protected static RootsSelection getSelection(@NotNull AnActionEvent e) {
    VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    Module module = getModule(e, files);
    if (module == null) return RootsSelection.EMPTY;

    RootsSelection selection = new RootsSelection(module);
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(module.getProject()).getFileIndex();
    for (VirtualFile file : files) {
      if (!file.isDirectory()) {
        return RootsSelection.EMPTY;
      }
      ExcludeFolder excludeFolder = ProjectRootsUtil.findExcludeFolder(module, file);
      if (excludeFolder != null) {
        selection.mySelectedExcludeRoots.add(excludeFolder);
        continue;
      }
      SourceFolder folder = ProjectRootsUtil.getModuleSourceRoot(file, module.getProject());
      if (folder != null && folder.getContentEntry().getRootModel().getModule().equals(module)) {
        selection.mySelectedRoots.add(folder);
      }
      else {
        selection.mySelectedDirectories.add(file);
        if (fileIndex.isInSourceContent(file)) {
          selection.myHaveSelectedFilesUnderSourceRoots = true;
        }
      }
    }
    return selection;
  }

  static @Nullable Module getModule(@NotNull AnActionEvent e, VirtualFile @Nullable [] files) {
    if (files == null) return null;
    Module module = e.getData(PlatformCoreDataKeys.MODULE);
    if (module == null) {
      module = findParentModule(e.getProject(), files);
    }
    return module;
  }

  private static @Nullable Module findParentModule(@Nullable Project project, VirtualFile @NotNull [] files) {
    if (project == null) return null;
    Module result = null;
    ProjectFileIndex index = ProjectFileIndex.getInstance(project);
    for (VirtualFile file : files) {
      Module module = index.getModuleForFile(file, false);
      if (module == null) return null;
      if (result == null) {
        result = module;
      }
      else if (!result.equals(module)) {
        return null;
      }
    }
    return result;
  }

  public static final class RootsSelection {
    public static final RootsSelection EMPTY = new RootsSelection(null);
    public final Module myModule;

    public RootsSelection(Module module) {
      myModule = module;
    }

    public List<SourceFolder> mySelectedRoots = new ArrayList<>();
    public List<ExcludeFolder> mySelectedExcludeRoots = new ArrayList<>();
    public List<VirtualFile> mySelectedDirectories = new ArrayList<>();
    public boolean myHaveSelectedFilesUnderSourceRoots;
  }
}