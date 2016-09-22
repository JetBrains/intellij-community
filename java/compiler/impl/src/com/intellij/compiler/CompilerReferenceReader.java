/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.compiler;

import com.intellij.compiler.server.BuildManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.util.containers.Queue;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.backwardRefs.CompilerElement;
import org.jetbrains.jps.backwardRefs.CompilerBackwardReferenceIndex;
import org.jetbrains.jps.backwardRefs.LightUsage;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

public class CompilerReferenceReader {
  private final CompilerBackwardReferenceIndex myIndex;

  private CompilerReferenceReader(File buildDir) throws IOException {
    myIndex = new CompilerBackwardReferenceIndex(buildDir);
  }

  @NotNull
  public TIntHashSet findReferentFileIds(@NotNull CompilerElement element) {
    LightUsage usage = element.asUsage(myIndex.getByteSeqEum());

    TIntHashSet set = new TIntHashSet();
    for (int classId : getWholeHierarchy(usage.getOwner())) {
      final LightUsage overriderUsage = usage.override(classId);
      final Collection<Integer> usageFiles = myIndex.getBackwardReferenceMap().get(overriderUsage);
      if (usageFiles != null) {
        for (int fileId : usageFiles) {
          final VirtualFile file = findFile(fileId);
          if (file != null) {
            set.add(((VirtualFileWithId)file).getId());
          }
        }
      }
    }
    return set;
  }

  public void close() {
    myIndex.close();
  }

  public static CompilerReferenceReader create(Project project) {
    File buildDir = BuildManager.getInstance().getProjectSystemDirectory(project);
    if (buildDir == null || CompilerBackwardReferenceIndex.versionDiffers(buildDir)) {
      return null;
    }
    try {
      return new CompilerReferenceReader(buildDir);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private VirtualFile findFile(int id) {
    try {
      String path = myIndex.getFilePathEnumerator().valueOf(id);
      assert path != null;
      return VfsUtil.findFileByIoFile(new File(path), false);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private int[] getWholeHierarchy(int classId) {
    TIntHashSet result = new TIntHashSet();
    Queue<Integer> q = new Queue<>(10);
    q.addLast(classId);
    while (!q.isEmpty()) {
      int curId = q.pullFirst();
      if (result.add(curId)) {
        final TIntHashSet subclasses = myIndex.getBackwardHierarchyMap().get(curId);
        if (subclasses != null) {
          subclasses.forEach(new TIntProcedure() {
            @Override
            public boolean execute(int nextId) {
              q.addLast(nextId);
              return true;
            }
          });
        }
      }
    }
    return result.toArray();
  }
}