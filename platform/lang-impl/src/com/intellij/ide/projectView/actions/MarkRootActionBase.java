/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.ide.projectView.actions;

import com.intellij.ide.projectView.impl.ProjectRootsUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public abstract class MarkRootActionBase extends DumbAwareAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    Module module = e.getData(LangDataKeys.MODULE);
    VirtualFile[] vFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (module == null || vFiles == null) {
      return;
    }
    final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
    for (VirtualFile vFile : vFiles) {
      ContentEntry entry = findContentEntry(model, vFile);
      if (entry != null) {
        final SourceFolder[] sourceFolders = entry.getSourceFolders();
        for (SourceFolder sourceFolder : sourceFolders) {
          if (Comparing.equal(sourceFolder.getFile(), vFile)) {
            entry.removeSourceFolder(sourceFolder);
            break;
          }
        }
        modifyRoots(vFile, entry);
      }
    }
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        model.commit();
      }
    });
  }

  protected abstract void modifyRoots(VirtualFile vFile, ContentEntry entry);

  @Nullable
  public static ContentEntry findContentEntry(@NotNull ModuleRootModel model, @NotNull VirtualFile vFile) {
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
  public void update(AnActionEvent e) {
    Module module = e.getData(LangDataKeys.MODULE);
    RootsSelection selection = getSelection(e);
    boolean enabled = module != null && (!selection.mySelectedRoots.isEmpty() || !selection.mySelectedDirectories.isEmpty()) && isEnabled(selection, module);
    e.getPresentation().setVisible(enabled);
    e.getPresentation().setEnabled(enabled);
  }

  protected abstract boolean isEnabled(@NotNull RootsSelection selection, @NotNull Module module);

  protected static RootsSelection getSelection(AnActionEvent e) {
    Module module = e.getData(LangDataKeys.MODULE);
    VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (module == null || files == null) {
      return RootsSelection.EMPTY;
    }

    RootsSelection selection = new RootsSelection();
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(module.getProject()).getFileIndex();
    for (VirtualFile file : files) {
      if (!file.isDirectory()) {
        return RootsSelection.EMPTY;
      }
      if (!fileIndex.isInContent(file)) {
        return RootsSelection.EMPTY;
      }
      SourceFolder folder;
      if (Comparing.equal(fileIndex.getSourceRootForFile(file), file) && ((folder = ProjectRootsUtil.findSourceFolder(module, file)) != null)) {
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

  protected static class RootsSelection {
    public static final RootsSelection EMPTY = new RootsSelection();

    public List<SourceFolder> mySelectedRoots = new ArrayList<SourceFolder>();
    public List<VirtualFile> mySelectedDirectories = new ArrayList<VirtualFile>();
    public boolean myHaveSelectedFilesUnderSourceRoots;
  }
}
