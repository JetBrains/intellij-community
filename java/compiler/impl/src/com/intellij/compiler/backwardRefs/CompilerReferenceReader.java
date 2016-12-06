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
package com.intellij.compiler.backwardRefs;

import com.intellij.compiler.server.BuildManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.Queue;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.indexing.ValueContainer;
import gnu.trove.THashSet;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.backwardRefs.ByteArrayEnumerator;
import org.jetbrains.jps.backwardRefs.CompilerBackwardReferenceIndex;
import org.jetbrains.jps.backwardRefs.LightRef;
import org.jetbrains.jps.backwardRefs.index.CompilerIndices;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static java.util.stream.Collectors.toList;

class CompilerReferenceReader {
  private final static Logger LOG = Logger.getInstance(CompilerReferenceReader.class);

  private final CompilerBackwardReferenceIndex myIndex;

  private CompilerReferenceReader(File buildDir) throws IOException {
    myIndex = new CompilerBackwardReferenceIndex(buildDir);
  }

  @Nullable
  TIntHashSet findReferentFileIds(@NotNull LightRef ref, boolean checkBaseClassAmbiguity) throws StorageException {
    LightRef.LightClassHierarchyElementDef hierarchyElement = ref instanceof LightRef.LightClassHierarchyElementDef ?
                                                              (LightRef.LightClassHierarchyElementDef)ref :
                                                              ((LightRef.LightMember)ref).getOwner();
    TIntHashSet set = new TIntHashSet();
    final LightRef.NamedLightRef[] hierarchy = getWholeHierarchy(hierarchyElement, checkBaseClassAmbiguity);
    if (hierarchy == null) return null;
    for (LightRef.NamedLightRef aClass : hierarchy) {
      final LightRef overriderUsage = ref.override(aClass.getName());
      addUsages(overriderUsage, set);
    }
    return set;
  }

  /**
   * @return two maps of classes grouped per file
   *
   * 1st map: inheritors. Can be used without explicit inheritance verification
   * 2nd map: candidates. One need to check that these classes are really direct inheritors
   */
  @NotNull
  Map<VirtualFile, Object[]> getDirectInheritors(@NotNull LightRef searchElement,
                                                 @NotNull GlobalSearchScope searchScope,
                                                 @NotNull GlobalSearchScope dirtyScope,
                                                 @NotNull FileType fileType,
                                                 @NotNull CompilerHierarchySearchType searchType) throws StorageException {
    GlobalSearchScope effectiveSearchScope = GlobalSearchScope.notScope(dirtyScope).intersectWith(searchScope);
    LanguageLightRefAdapter adapter = CompilerReferenceServiceImpl.findAdapterForFileType(fileType);
    LOG.assertTrue(adapter != null, "adapter is null for file type: " + fileType);
    Class<? extends LightRef> requiredLightRefClass = searchType.getRequiredClass(adapter);

    Map<VirtualFile, Object[]> candidatesPerFile = new HashMap<>();
    myIndex.get(CompilerIndices.BACK_HIERARCHY).getData(searchElement).forEach((fileId, defs) -> {
        final List<LightRef> requiredCandidates = defs.stream().filter(requiredLightRefClass::isInstance).collect(toList());
        if (requiredCandidates.isEmpty()) return true;
        final VirtualFile file = findFile(fileId);
        if (file != null && effectiveSearchScope.contains(file)) {
          candidatesPerFile.put(file, searchType.convertToIds(requiredCandidates, myIndex.getByteSeqEum()));
        }
        return true;
      });
    return candidatesPerFile.isEmpty() ? Collections.emptyMap() : candidatesPerFile;
  }

  @NotNull
  ByteArrayEnumerator getNameEnumerator() {
    return myIndex.getByteSeqEum();
  }

  void close() {
    myIndex.close();
  }

  static CompilerReferenceReader create(Project project) {
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

  private void addUsages(LightRef usage, TIntHashSet sink) throws StorageException {
    myIndex.get(CompilerIndices.BACK_USAGES).getData(usage).forEach(
      new ValueContainer.ContainerAction<Void>() {
        @Override
        public boolean perform(int id, Void value) {
          final VirtualFile file = findFile(id);
          if (file != null) {
            sink.add(((VirtualFileWithId)file).getId());
          }
          return true;
        }
      });
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
  private LightRef.NamedLightRef[] getWholeHierarchy(LightRef.LightClassHierarchyElementDef hierarchyElement, boolean checkBaseClassAmbiguity)
    throws StorageException {
    Set<LightRef.NamedLightRef> result = new THashSet<>();
    Queue<LightRef.NamedLightRef> q = new Queue<>(10);
    q.addLast(hierarchyElement);
    while (!q.isEmpty()) {
      LightRef.NamedLightRef curClass = q.pullFirst();
      if (result.add(curClass)) {
        if (checkBaseClassAmbiguity || curClass != hierarchyElement) {
          DefCount count = getDefinitionCount(curClass);
          if (count == DefCount.NONE) {
            //diagnostic
            String baseHierarchyElement = getNameEnumerator().getName(hierarchyElement.getName());
            String curHierarchyElement = getNameEnumerator().getName(curClass.getName());
            LOG.error("Can't get definition files for :" + curHierarchyElement + " base class: " + baseHierarchyElement);
          }
          if (count != DefCount.ONE) {
            return null;
          }
        }
        myIndex.get(CompilerIndices.BACK_HIERARCHY).getData(curClass).forEach((id, children) -> {
          for (LightRef child : children) {
            if (child instanceof LightRef.LightClassHierarchyElementDef) {
              q.addLast((LightRef.LightClassHierarchyElementDef) child);
            }
          }
          return true;
        });
      }
    }
    return result.toArray(new LightRef.NamedLightRef[result.size()]);
  }

  private enum DefCount { NONE, ONE, MANY}
  @NotNull
  private DefCount getDefinitionCount(LightRef def) throws StorageException {
    DefCount[] result = new DefCount[]{DefCount.NONE};
    myIndex.get(CompilerIndices.BACK_CLASS_DEF).getData(def).forEach(new ValueContainer.ContainerAction<Void>() {
      @Override
      public boolean perform(int id, Void value) {
        if (result[0] == DefCount.NONE) {
          result[0] = DefCount.ONE;
          return true;
        }
        if (result[0] == DefCount.ONE) {
          result[0] = DefCount.MANY;
          return true;
        }
        return false;
      }
    });
    return result[0];
  }
}