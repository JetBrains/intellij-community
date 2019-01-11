// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author Dmitry Avdeev
 */
public abstract class WritingAccessProvider {
  public static final ExtensionPointName<WritingAccessProvider> EP_NAME = ExtensionPointName.create("com.intellij.writingAccessProvider");

  /**
   * @param files files to be checked
   * @return set of files that cannot be accessed
   */
  @NotNull
  public Collection<VirtualFile> requestWriting(@NotNull Collection<? extends VirtualFile> files) {
    //noinspection deprecation
    return requestWriting(files.toArray(VirtualFile.EMPTY_ARRAY));
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

  @NotNull
  public static WritingAccessProvider[] getProvidersForProject(@Nullable Project project) {
    return project == null || project.isDefault() ? new WritingAccessProvider[0] : EP_NAME.getExtensions(project);
  }

  public static boolean isPotentiallyWritable(@NotNull VirtualFile file, @Nullable Project project) {
    WritingAccessProvider[] providers = getProvidersForProject(project);
    for (WritingAccessProvider provider : providers) {
      if (!provider.isPotentiallyWritable(file)) {
        return false;
      }
    }
    return true;
  }
}
