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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class MarkRootAction extends DumbAwareAction {
  private final boolean myMarkAsTestSources;
  private final boolean myMarkAsExcluded;
  private final boolean myUnmark;

  public MarkRootAction() {
    myMarkAsTestSources = false;
    myMarkAsExcluded = false;
    myUnmark = false;
  }

  protected MarkRootAction(boolean markAsTestSources, boolean markAsExcluded) {
    myMarkAsTestSources = markAsTestSources;
    myMarkAsExcluded = markAsExcluded;
    myUnmark = false;
  }

  protected MarkRootAction(boolean unmark) {
    myMarkAsTestSources = false;
    myMarkAsExcluded = false;
    myUnmark = true;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Module module = e.getData(LangDataKeys.MODULE);
    VirtualFile[] vFiles = e.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY);
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
        if (!myUnmark) {
          if (myMarkAsExcluded) {
            entry.addExcludeFolder(vFile);
          }
          else {
            entry.addSourceFolder(vFile, myMarkAsTestSources);
          }
        }
      }
    }
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        model.commit();
      }
    });
  }

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
    boolean enabled = canMark(e, myMarkAsTestSources || myMarkAsExcluded || myUnmark, !myMarkAsTestSources || myMarkAsExcluded || myUnmark,
                              myMarkAsExcluded, null);
    e.getPresentation().setVisible(enabled);
    e.getPresentation().setEnabled(enabled);
  }

  public static boolean canMark(AnActionEvent e,
                                boolean acceptSourceRoot,
                                boolean acceptTestSourceRoot,
                                boolean acceptInSourceContent,
                                @Nullable Ref<Boolean> rootType) {
    Module module = e.getData(LangDataKeys.MODULE);
    VirtualFile[] vFiles = e.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY);
    if (module == null || vFiles == null) {
      return false;
    }
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(module.getProject()).getFileIndex();
    for (VirtualFile vFile : vFiles) {
      if (!vFile.isDirectory()) {
        return false;
      }
      if (!fileIndex.isInContent(vFile)) {
        return false;
      }
      if (Comparing.equal(fileIndex.getSourceRootForFile(vFile), vFile)) {
        boolean isTestSourceRoot = fileIndex.isInTestSourceContent(vFile);
        if (acceptSourceRoot && !isTestSourceRoot) {
          if (rootType != null) rootType.set(true);
          return true;
        }
        if (acceptTestSourceRoot && isTestSourceRoot) {
          if (rootType != null) rootType.set(false);
          return true;
        }
      }
      if (fileIndex.isInSourceContent(vFile) && !acceptInSourceContent) {
        return false;
      }
    }
    return true;
  }
}
