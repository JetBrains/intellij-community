// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

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
  public abstract Collection<VirtualFile> requestWriting(VirtualFile... files);

  public abstract boolean isPotentiallyWritable(@NotNull VirtualFile file);

  @NotNull
  public static WritingAccessProvider[] getProvidersForProject(Project project) {
    return project == null || project.isDefault() ? new WritingAccessProvider[0] : EP_NAME.getExtensions(project);
  }

  public static boolean isPotentiallyWritable(VirtualFile file, Project project) {
    WritingAccessProvider[] providers = getProvidersForProject(project);
    for (WritingAccessProvider provider : providers) {
      if (!provider.isPotentiallyWritable(file)) {
        return false;
      }
    }
    return true;
  }
}
