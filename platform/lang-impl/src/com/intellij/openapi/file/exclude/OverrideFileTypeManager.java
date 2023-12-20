// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.file.exclude;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.fileTypes.ex.FakeFileType;
import com.intellij.openapi.fileTypes.ex.FileTypeIdentifiableByVirtualFile;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Storage for file types user selected in "Override File Type" action
 */
@State(name = "OverrideFileTypeManager",
  category = SettingsCategory.TOOLS,
  exportable = true,
  storages = @Storage(value = "overrideFileTypes.xml", roamingType = RoamingType.DISABLED))
@Service
public final class OverrideFileTypeManager extends PersistentFileSetManager {
  public boolean isMarkedPlainText(@NotNull VirtualFile file) {
    return PlainTextFileType.INSTANCE.getName().equals(getFileValue(file));
  }

  public static OverrideFileTypeManager getInstance() {
    return ApplicationManager.getApplication().getService(OverrideFileTypeManager.class);
  }

  /**
   * Explicitly associates a virtual file with a particular file type.
   *
   * @param file a virtual file
   * @param type a file type to associate with
   * @return {@code true} if the association has been successfully added
   */
  @RequiresEdt
  @ApiStatus.Internal
  @Override
  public boolean addFile(@NotNull VirtualFile file, @NotNull FileType type) {
    if (!isOverridable(file.getFileType()) || !isOverridable(type) || !(file instanceof VirtualFileWithId)) {
      //@formatter:off
      throw new IllegalArgumentException("Cannot override filetype for file " + file + " from " + file.getFileType() + " to " + type + " because the " + (isOverridable(type) ? "former" : "latter") + " is not overridable");
    }
    return super.addFile(file, type);
  }

  /**
   * Removes explicit association with a file type.
   *
   * @param file a virtual file
   * @return {@code true} if the association has been successfully removed
   */
  @RequiresEdt
  @ApiStatus.Internal
  @Override
  public boolean removeFile(@NotNull VirtualFile file) {
    return super.removeFile(file);
  }

  static boolean isOverridable(@NotNull FileType type) {
    if (type instanceof InternalFileType) return false;
    if (type instanceof DirectoryFileType) return false;
    if (type instanceof UnknownFileType) return false;
    if (type instanceof FakeFileType) return false;
    // FileTypeIdentifiableByVirtualFile has hard-coded isMyFileType() which we can't change, so we shouldn't override this,
    // or we will risk creating an inconsistency otherwise (see com.intellij.openapi.fileTypes.impl.FileTypesUltimateTest.testFileTypesIdentifiableByFileHaveConsistentIsMyFile)
    if (type instanceof FileTypeIdentifiableByVirtualFile) return false;
    return true;
  }
}
