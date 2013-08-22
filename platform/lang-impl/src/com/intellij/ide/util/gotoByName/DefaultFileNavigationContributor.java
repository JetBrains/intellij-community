/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ide.util.gotoByName;

import com.intellij.navigation.EfficientChooseByNameContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.IdFilter;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.BitSet;

public class DefaultFileNavigationContributor implements EfficientChooseByNameContributor, DumbAware {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.gotoByName.DefaultFileNavigationContributor");

  @Override
  @NotNull
  public String[] getNames(Project project, boolean includeNonProjectItems) {
    if (FileBasedIndex.ourEnableTracingOfKeyHashToVirtualFileMapping) {
      final THashSet<String> names = new THashSet<String>(1000);
      IdFilter filter = getFilter(project, includeNonProjectItems);
      processNames(new Processor<String>() {
        @Override
        public boolean process(String s) {
          names.add(s);
          return true;
        }
      }, getScope(project, includeNonProjectItems), filter);
      if (LOG.isDebugEnabled()) {
        LOG.debug("All names retrieved2:" + names.size());
      }
      return ArrayUtil.toStringArray(names);
    } else {
      return FilenameIndex.getAllFilenames(project);
    }
  }

  public static GlobalSearchScope getScope(Project project, boolean includeNonProjectItems) {
    return includeNonProjectItems ? GlobalSearchScope.projectScope(project) : GlobalSearchScope.allScope(project);
  }

  public static IdFilter getFilter(Project project, boolean includeNonProjectItems) {
    long started = System.currentTimeMillis();
    final BitSet idSet = new BitSet();

    ContentIterator iterator = new ContentIterator() {
      @Override
      public boolean processFile(VirtualFile fileOrDir) {
        idSet.set(
          ((VirtualFileWithId)fileOrDir).getId()
        );
        ProgressManager.checkCanceled();
        return true;
      }
    };

    if (!includeNonProjectItems) {
      ProjectRootManager.getInstance(project).getFileIndex().iterateContent(iterator);
    } else {
      FileBasedIndex.getInstance().iterateIndexableFiles(iterator, project, null);
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("Done filter " + (System.currentTimeMillis()  -started) + ":" + idSet.size());
    }
    return new IdFilter() {
      @Override
      public boolean contains(int id) {
        return idSet.get(id);
      }
    };
  }

  @Override
  @NotNull
  public NavigationItem[] getItemsByName(String name, final String pattern, Project project, boolean includeNonProjectItems) {
    final boolean includeDirs = pattern.endsWith("/") || pattern.endsWith("\\");
    GlobalSearchScope scope = includeNonProjectItems
                              ? ProjectScope.getAllScope(project)
                              : ProjectScope.getProjectScope(project);
    PsiFileSystemItem[] items = FilenameIndex.getFilesByName(project, name,
                                                             scope,
                                                             includeDirs);
    if(items.length == 0 && includeNonProjectItems && !includeDirs) {
      items = FilenameIndex.getFilesByName(project, name, scope, true);
    }
    return items;
  }

  @Override
  public void processNames(final Processor<String> processor, GlobalSearchScope scope, IdFilter filter) {
    long started = System.currentTimeMillis();
    FileBasedIndex.getInstance().processAllKeys(FilenameIndex.NAME, new Processor<String>() {
      @Override
      public boolean process(String s) {
        return processor.process(s);
      }
    }, scope, filter);
    if (LOG.isDebugEnabled()) {
      LOG.debug("All names retrieved:" + (System.currentTimeMillis() - started));
    }
  }
}
