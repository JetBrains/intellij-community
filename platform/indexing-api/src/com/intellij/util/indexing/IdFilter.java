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
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;

import java.util.BitSet;

/**
* Created by Maxim.Mossienko on 8/14/13.
*/
public abstract class IdFilter {
  public static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.gotoByName.DefaultFileNavigationContributor");

  public static IdFilter getProjectIdFilter(Project project, boolean includeNonProjectItems) {
    long started = System.currentTimeMillis();
    final BitSet idSet = new BitSet();

    ContentIterator iterator = new ContentIterator() {
      @Override
      public boolean processFile(VirtualFile fileOrDir) {
        int id = ((VirtualFileWithId)fileOrDir).getId();
        if (id < 0) id = -id; // workaround for encountering invalid files, see EA-49915, EA-50599
        idSet.set(id);
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
      public boolean containsFileId(int id) {
        return id >= 0 && idSet.get(id);
      }
    };
  }

  public abstract boolean containsFileId(int id);
}
