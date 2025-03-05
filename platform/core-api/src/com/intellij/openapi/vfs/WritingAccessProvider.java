// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs;

import com.intellij.core.CoreBundle;
import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.event.HyperlinkListener;
import java.util.Collection;

public abstract class WritingAccessProvider {
  public static final ProjectExtensionPointName<WritingAccessProvider> EP = new ProjectExtensionPointName<>("com.intellij.writingAccessProvider");

  /**
   * @param files files to be checked
   * @return set of files that cannot be accessed
   */
  public @Unmodifiable @NotNull Collection<VirtualFile> requestWriting(@NotNull Collection<? extends VirtualFile> files) {
    return requestWriting(files.toArray(VirtualFile.EMPTY_ARRAY));
  }

  public @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String getReadOnlyMessage() {
    return CoreBundle.message("editing.read.only.file.hint");
  }

  public @Nullable HyperlinkListener getHyperlinkListener() {
    return null;
  }

  /**
   * @deprecated Use {@link #requestWriting(Collection)}
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  public Collection<VirtualFile> requestWriting(VirtualFile @NotNull ... files) {
    throw new AbstractMethodError("requestWriting(List<VirtualFile>) not implemented");
  }

  public boolean isPotentiallyWritable(@NotNull VirtualFile file) {
    return true;
  }

  public static boolean isPotentiallyWritable(@NotNull VirtualFile file, @Nullable Project project) {
    return project == null || project.isDefault() || EP.findFirstSafe(project, provider -> !provider.isPotentiallyWritable(file)) == null;
  }
}
