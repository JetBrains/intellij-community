/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.BitSet;

public abstract class IdFilter {
  private static final Logger LOG = Logger.getInstance(IdFilter.class);
  private static final Key<CachedValue<IdFilter>> INSIDE_PROJECT = Key.create("INSIDE_PROJECT");
  private static final Key<CachedValue<IdFilter>> OUTSIDE_PROJECT = Key.create("OUTSIDE_PROJECT");

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

    @NonNls
    @NotNull
    public abstract String getId();
  }

  @NotNull
  public static IdFilter getProjectIdFilter(@NotNull Project project, final boolean includeNonProjectItems) {
    Key<CachedValue<IdFilter>> key = includeNonProjectItems ? OUTSIDE_PROJECT : INSIDE_PROJECT;
    CachedValueProvider<IdFilter> provider = () -> CachedValueProvider.Result.create(buildProjectIdFilter(project, includeNonProjectItems),
              ProjectRootManager.getInstance(project), VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS);
    return CachedValuesManager.getManager(project).getCachedValue(project, key,
                                                                  provider, false);
  }

  @NotNull
  private static IdFilter buildProjectIdFilter(Project project, boolean includeNonProjectItems) {
    long started = System.currentTimeMillis();
    final BitSet idSet = new BitSet();

    ContentIterator iterator = fileOrDir -> {
      idSet.set(((VirtualFileWithId)fileOrDir).getId());
      ProgressManager.checkCanceled();
      return true;
    };

    if (!includeNonProjectItems) {
      ProjectRootManager.getInstance(project).getFileIndex().iterateContent(iterator);
    }
    else {
      FileBasedIndex.getInstance().iterateIndexableFiles(iterator, project, ProgressIndicatorProvider.getGlobalProgressIndicator());
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

      @NotNull
      @Override
      public FilterScopeType getFilteringScopeType() {
        return includeNonProjectItems ? FilterScopeType.PROJECT_AND_LIBRARIES : FilterScopeType.PROJECT;
      }
    };
  }

  public abstract boolean containsFileId(int id);

  @NotNull
  public FilterScopeType getFilteringScopeType() {
    return FilterScopeType.OTHER;
  }
}
