// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.BitSet;

public abstract class IdFilter {
  private static final Logger LOG = Logger.getInstance(IdFilter.class);
  private static final Key<CachedValue<IdFilter>> INSIDE_PROJECT = Key.create("INSIDE_PROJECT");
  private static final Key<CachedValue<IdFilter>> OUTSIDE_PROJECT = Key.create("OUTSIDE_PROJECT");

  @Internal
  public enum FilterScopeType {
    OTHER {
      @Override
      public @NonNls @NotNull String getId() {
        throw new UnsupportedOperationException();
      }
    },
    PROJECT {
      @Override
      public @NonNls @NotNull String getId() {
        return "false";
      }
    },
    PROJECT_AND_LIBRARIES {
      @Override
      public @NonNls @NotNull String getId() {
        return "true";
      }
    };

    public abstract @NonNls @NotNull String getId();
  }

  public static @NotNull IdFilter getProjectIdFilter(@NotNull Project project, final boolean includeNonProjectItems) {
    Key<CachedValue<IdFilter>> key = includeNonProjectItems ? OUTSIDE_PROJECT : INSIDE_PROJECT;
    CachedValueProvider<IdFilter> provider = () -> CachedValueProvider.Result.create(buildProjectIdFilter(project, includeNonProjectItems),
              ProjectRootManager.getInstance(project), VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS);
    return CachedValuesManager.getManager(project).getCachedValue(project, key, provider, false);
  }

  private static @NotNull IdFilter buildProjectIdFilter(@NotNull Project project, boolean includeNonProjectItems) {
    long started = System.currentTimeMillis();
    final BitSet idSet = new BitSet();

    ContentIterator iterator = fileOrDir -> {
      idSet.set(((VirtualFileWithId)fileOrDir).getId());
      ProgressManager.checkCanceled();
      return true;
    };

    if (includeNonProjectItems) {
      FileBasedIndex.getInstance().iterateIndexableFiles(iterator, project, ProgressIndicatorProvider.getGlobalProgressIndicator());
    }
    else {
      ProjectRootManager.getInstance(project).getFileIndex().iterateContent(iterator);
    }

    if (LOG.isDebugEnabled()) {
      long elapsed = System.currentTimeMillis() - started;
      LOG.debug("Done filter (includeNonProjectItems=" + includeNonProjectItems+") "+
                "in " + elapsed + "ms. Total files in set: " + idSet.cardinality());
    }
    return new IdFilter() {
      @Override
      public boolean containsFileId(int id) {
        return id >= 0 && idSet.get(id);
      }

      @Override
      public @NotNull FilterScopeType getFilteringScopeType() {
        return includeNonProjectItems ? FilterScopeType.PROJECT_AND_LIBRARIES : FilterScopeType.PROJECT;
      }
    };
  }

  public abstract boolean containsFileId(int id);

  @Internal
  public @NotNull FilterScopeType getFilteringScopeType() {
    return FilterScopeType.OTHER;
  }
}
