// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs;

import com.intellij.openapi.editor.EditorBundle;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public abstract class WritingAccessProvider {
  public static final ExtensionPointName<WritingAccessProvider> EP_NAME = ExtensionPointName.create("com.intellij.writingAccessProvider");

  /**
   * @param files files to be checked
   * @return set of files that cannot be accessed
   */
  @NotNull
  public Collection<VirtualFile> requestWriting(@NotNull Collection<? extends VirtualFile> files) {
    return requestWriting(files.toArray(VirtualFile.EMPTY_ARRAY));
  }

  @NotNull
  @Nls(capitalization = Nls.Capitalization.Sentence)
  public String getReadOnlyMessage() {
    return EditorBundle.message("editing.read.only.file.hint");
  }

  /**
   * @deprecated Use {@link #requestWriting(Collection)}
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  public Collection<VirtualFile> requestWriting(@NotNull VirtualFile... files) {
    throw new AbstractMethodError("requestWriting(List<VirtualFile>) not implemented");
  }

  public boolean isPotentiallyWritable(@NotNull VirtualFile file) {
    return true;
  }

  public static boolean isPotentiallyWritable(@NotNull VirtualFile file, @Nullable Project project) {
    if (project == null || project.isDefault()) {
      return true;
    }

    for (WritingAccessProvider provider : EP_NAME.getIterable(project)) {
      if (!provider.isPotentiallyWritable(file)) {
        return false;
      }
    }
    return true;
  }
}
