// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.util.Collection;
import java.util.List;

public final class JpsFileTargetContextUtils {
  private JpsFileTargetContextUtils() {
  }

  /**
   * Sorts and filters out contexts depending on their {@link JpsModuleSourceRootType} for Create File quick fixes.
   *
   * @see com.intellij.codeInsight.daemon.quickFix.CreateFilePathFix
   * @see com.intellij.codeInsight.daemon.quickFix.FileReferenceQuickFixProvider
   */
  @NotNull
  public static Collection<FileTargetContext> prepareTargetContexts(@NotNull Project project,
                                                                    @NotNull VirtualFile file,
                                                                    @NotNull Collection<FileTargetContext> targetContexts) {
    // here we try to sort target locations depending on src/test origin
    if (targetContexts.size() <= 1) {
      return targetContexts;
    }

    ProjectFileIndex projectFileIndex = ProjectFileIndex.getInstance(project);
    boolean isInSources = projectFileIndex.isInSourceContent(file)
                          && !projectFileIndex.isInTestSourceContent(file);

    List<FileTargetContextWrapper> targetContextWrappers = ContainerUtil.filter(findSourceRootTypes(targetContexts), tc -> {
      if (isInSources) {
        // exclude test directories for sources options
        if (tc.getSourceRootType() != null
            && tc.getSourceRootType().isForTests()) {
          return false;
        }
      }

      return !tc.isInGeneratedSource();
    });

    // sort only if we have different source root types
    if (!hasEqualSourceRootTypes(targetContextWrappers)) {
      // if file is under sources root then src/resources directories at the top
      // if file is under test sources root then test/resources directories at the top
      if (projectFileIndex.isInTestSourceContent(file)) {
        targetContextWrappers = ContainerUtil.sorted(targetContextWrappers, JpsFileTargetContextUtils::compareTargetsForTests);
      }
      else {
        // it could be a file from web resource root, it is not in source content, thus we do not check isInSourceContent(file)
        targetContextWrappers = ContainerUtil.sorted(targetContextWrappers, JpsFileTargetContextUtils::compareTargetsForProduction);
      }
    }

    return ContainerUtil.map(targetContextWrappers, FileTargetContextWrapper::getTargetContext);
  }

  private static boolean hasEqualSourceRootTypes(@NotNull List<FileTargetContextWrapper> wrappers) {
    if (wrappers.size() <= 1) {
      return true;
    }

    JpsModuleSourceRootType<?> sourceRootType = null;
    for (FileTargetContextWrapper item : wrappers) {
      JpsModuleSourceRootType<?> itemSourceRootType = item.getSourceRootType();
      if (sourceRootType == null) {
        if (itemSourceRootType != null) {
          sourceRootType = itemSourceRootType;
        }
      }
      else if (sourceRootType != itemSourceRootType) {
        return false;
      }
    }

    return true;
  }

  private static List<FileTargetContextWrapper> findSourceRootTypes(Collection<? extends FileTargetContext> targetContexts) {
    return ContainerUtil.map(targetContexts, c -> {
      Project project = c.getFileSystemItem().getProject();

      VirtualFile file = c.getFileSystemItem().getVirtualFile();
      if (file != null) {
        ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);
        return new FileTargetContextWrapper(c, fileIndex.getContainingSourceRootType(file), fileIndex.isInGeneratedSources(file));
      }
      else {
        return new FileTargetContextWrapper(c, null, false);
      }
    });
  }

  private static int compareTargetsForTests(@NotNull FileTargetContextWrapper d1, @NotNull FileTargetContextWrapper d2) {
    int o1 = getTestsTargetOrdinal(d1);
    int o2 = getTestsTargetOrdinal(d2);

    if (o1 > 0 || o2 > 0) {
      return Integer.compare(o1, o2);
    }

    return compareDirectoryPaths(d1, d2);
  }

  private static int compareTargetsForProduction(@NotNull FileTargetContextWrapper d1, @NotNull FileTargetContextWrapper d2) {
    int o1 = getSourcesTargetOrdinal(d1);
    int o2 = getSourcesTargetOrdinal(d2);

    if (o1 > 0 || o2 > 0) {
      return Integer.compare(o1, o2);
    }

    return compareDirectoryPaths(d1, d2);
  }

  private static int getTestsTargetOrdinal(@NotNull FileTargetContextWrapper item) {
    JpsModuleSourceRootType<?> type = item.getSourceRootType();

    if (isSourceItem(type)) return 4;
    if (isTestSourceItem(type)) return 3;
    if (isResourceItem(type)) return 2;
    if (isTestResourceItem(type)) return 1;

    return 0;
  }

  private static int getSourcesTargetOrdinal(@NotNull FileTargetContextWrapper item) {
    JpsModuleSourceRootType<?> type = item.getSourceRootType();

    if (isTestSourceItem(type)) return 4;
    if (isSourceItem(type)) return 3;
    if (isTestResourceItem(type)) return 2;
    if (isResourceItem(type)) return 1;

    return 0;
  }

  private static int compareDirectoryPaths(@NotNull FileTargetContextWrapper d1, @NotNull FileTargetContextWrapper d2) {
    PsiFileSystemItem directory1 = d1.getTargetContext().getFileSystemItem();
    PsiFileSystemItem directory2 = d2.getTargetContext().getFileSystemItem();

    assert directory1 != null : "Invalid PsiFileSystemItem instances found";
    assert directory2 != null : "Invalid PsiFileSystemItem instances found";

    VirtualFile f1 = directory1.getVirtualFile();
    VirtualFile f2 = directory2.getVirtualFile();
    return f1.getPath().compareTo(f2.getPath());
  }

  private static boolean isTestResourceItem(@Nullable JpsModuleSourceRootType<?> type) {
    return type == JavaResourceRootType.TEST_RESOURCE;
  }

  private static boolean isResourceItem(@Nullable JpsModuleSourceRootType<?> type) {
    return type == JavaResourceRootType.RESOURCE;
  }

  private static boolean isTestSourceItem(@Nullable JpsModuleSourceRootType<?> type) {
    return type == JavaSourceRootType.TEST_SOURCE;
  }

  private static boolean isSourceItem(@Nullable JpsModuleSourceRootType<?> type) {
    return type == JavaSourceRootType.SOURCE;
  }

  private static final class FileTargetContextWrapper {
    private final FileTargetContext myTargetContext;
    private final JpsModuleSourceRootType<?> mySourceRootType;
    private final boolean myInGeneratedSource;

    private FileTargetContextWrapper(@NotNull FileTargetContext context, @Nullable JpsModuleSourceRootType<?> sourceRootType, boolean inGeneratedSource) {
      myTargetContext = context;
      mySourceRootType = sourceRootType;
      myInGeneratedSource = inGeneratedSource;
    }

    public FileTargetContext getTargetContext() {
      return myTargetContext;
    }

    @Nullable
    public JpsModuleSourceRootType<?> getSourceRootType() {
      return mySourceRootType;
    }

    private boolean isInGeneratedSource() {
      return myInGeneratedSource;
    }
  }
}
