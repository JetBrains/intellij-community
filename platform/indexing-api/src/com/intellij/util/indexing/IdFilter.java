// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.diagnostic.Logger;
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
    if (includeNonProjectItems) {
      IdFilter filter = FileBasedIndex.getInstance().projectIndexableFiles(project);
      return filter != null ? filter : new IdFilter() {
        @Override
        public boolean containsFileId(int id) {
          return false;
        }
      };
    }
    CachedValueProvider<IdFilter> provider = () -> CachedValueProvider.Result.create(buildProjectIdFilterForContentFiles(project),
                                                                                     ProjectRootManager.getInstance(project), VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS);
    return CachedValuesManager.getManager(project).getCachedValue(project, INSIDE_PROJECT, provider, false);
  }

  private static @NotNull IdFilter buildProjectIdFilterForContentFiles(@NotNull Project project) {
    long started = System.currentTimeMillis();
    final BitSet idSet = new BitSet();

    ContentIterator iterator = fileOrDir -> {
      idSet.set(((VirtualFileWithId)fileOrDir).getId());
      ProgressManager.checkCanceled();
      return true;
    };

    ProjectRootManager.getInstance(project).getFileIndex().iterateContent(iterator);

    if (LOG.isDebugEnabled()) {
      long elapsed = System.currentTimeMillis() - started;
      LOG.debug("Done filter (includeNonProjectItems=" + false+") "+
                "in " + elapsed + "ms. Total files in set: " + idSet.cardinality());
    }
    return new IdFilter() {
      @Override
      public boolean containsFileId(int id) {
        return id >= 0 && idSet.get(id);
      }

      @Override
      public @NotNull FilterScopeType getFilteringScopeType() {
        return FilterScopeType.PROJECT;
      }
    };
  }

  public abstract boolean containsFileId(int id);

  @Internal
  public @NotNull FilterScopeType getFilteringScopeType() {
    return FilterScopeType.OTHER;
  }

  public static final IdFilter ACCEPT_ALL = new IdFilter(){
    @Override
    public boolean containsFileId(int id) {
      return true;
    }

    @Override
    public String toString() {
      return "ACCEPT_ALL";
    }
  };
}
