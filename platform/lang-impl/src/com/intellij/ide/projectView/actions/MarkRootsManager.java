// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.actions;

import com.intellij.ide.SaveAndSyncHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

@ApiStatus.Experimental
public final class MarkRootsManager {

  private MarkRootsManager() {
  }

  /**
   * Applies modifier to each passed file while maintaining consistent state of source folder mappings.
   */
  @RequiresEdt
  public static void modifyRoots(@NotNull Module module,
                                 VirtualFile @NotNull [] files,
                                 @NotNull BiConsumer<VirtualFile, ContentEntry> modifier) {
    ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
    for (VirtualFile file : files) {
      ContentEntry entry = findContentEntry(model, file);
      if (entry != null) {
        SourceFolder[] sourceFolders = entry.getSourceFolders();
        for (SourceFolder sourceFolder : sourceFolders) {
          if (Comparing.equal(sourceFolder.getFile(), file)) {
            entry.removeSourceFolder(sourceFolder);
            break;
          }
        }
        modifier.accept(file, entry);
      }
    }
    commitModel(module, model);
  }

  /**
   * @return content entry corresponding to the passed file in the model
   */
  public static @Nullable ContentEntry findContentEntry(@NotNull ModuleRootModel model, @NotNull VirtualFile vFile) {
    ContentEntry[] contentEntries = model.getContentEntries();
    for (ContentEntry contentEntry : contentEntries) {
      VirtualFile contentEntryFile = contentEntry.getFile();
      if (contentEntryFile != null && VfsUtilCore.isAncestor(contentEntryFile, vFile, false)) {
        return contentEntry;
      }
    }
    return null;
  }

  /**
   * Saves state of modified roots.
   */
  public static void commitModel(@NotNull Module module, ModifiableRootModel model) {
    ApplicationManager.getApplication().runWriteAction(model::commit);
    SaveAndSyncHandler.getInstance().scheduleProjectSave(module.getProject());
  }
}
