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

import com.intellij.compiler.backwardRefs.LanguageLightUsageConverter;
import com.intellij.compiler.server.BuildManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.util.containers.Queue;
import gnu.trove.THashSet;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.backwardRefs.CompilerBackwardReferenceIndex;
import org.jetbrains.jps.backwardRefs.LightUsage;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;

public class CompilerReferenceReader {
  private final static Logger LOG = Logger.getInstance(CompilerReferenceReader.class);

  private final CompilerBackwardReferenceIndex myIndex;

  private CompilerReferenceReader(File buildDir) throws IOException {
    myIndex = new CompilerBackwardReferenceIndex(buildDir);
  }

  @Nullable
  public TIntHashSet findReferentFileIds(@NotNull CompilerElement element, @NotNull CompilerSearchAdapter adapter) {
    LightUsage usage = asLightUsage(element);

    TIntHashSet set = new TIntHashSet();
    if (adapter.needOverrideElement()) {
      final LightUsage[] hierarchy = getWholeHierarchy(usage.getOwner());
      if (hierarchy == null) return null;
      for (LightUsage aClass : hierarchy) {
        final LightUsage overriderUsage = usage.override(aClass);
        addUsages(overriderUsage, set);
      }
    } else {
      addUsages(usage, set);
    }
    return set;
  }

  public void addUsages(LightUsage usage, TIntHashSet sink) {
    final Collection<Integer> usageFiles = myIndex.getBackwardReferenceMap().get(usage);
    if (usageFiles != null) {
      for (int fileId : usageFiles) {
        final VirtualFile file = findFile(fileId);
        if (file != null) {
          sink.add(((VirtualFileWithId)file).getId());
        }
      }
    }
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

  @NotNull
  private LightUsage asLightUsage(@NotNull CompilerElement element) {
    LightUsage usage = null;
    for (LanguageLightUsageConverter converter : LanguageLightUsageConverter.INSTANCES) {
      usage = converter.asLightUsage(element, myIndex.getByteSeqEum());
      if (usage != null) {
        break;
      }
    }
    LOG.assertTrue(usage != null);
    return usage;
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

  @Nullable("return null if the class hierarchy contains ambiguous qualified names")
  private LightUsage[] getWholeHierarchy(LightUsage aClass) {
    Set<LightUsage> result = new THashSet<>();
    Queue<LightUsage> q = new Queue<>(10);
    q.addLast(aClass);
    while (!q.isEmpty()) {
      LightUsage curClass = q.pullFirst();
      if (result.add(curClass)) {
        final Collection<Integer> definitionFiles = myIndex.getBackwardClassDefinitionMap().get(curClass);
        if (definitionFiles.size() != 1) {
          return null;
        }
        final Collection<CompilerBackwardReferenceIndex.LightDefinition> subClassDefs = myIndex.getBackwardHierarchyMap().get(curClass);
        if (subClassDefs != null) {
          for (CompilerBackwardReferenceIndex.LightDefinition subclass : subClassDefs) {
            q.addLast(subclass.getUsage());
          }
        }
      }
    }
    return result.toArray(new LightUsage[result.size()]);
  }
}