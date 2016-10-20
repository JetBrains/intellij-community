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
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.impl.source.PsiFileWithStubSupport;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.Queue;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.backwardRefs.ByteArrayEnumerator;
import org.jetbrains.jps.backwardRefs.CompilerBackwardReferenceIndex;
import org.jetbrains.jps.backwardRefs.LightRef;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static java.util.stream.Collectors.*;

class CompilerReferenceReader {
  private final static Logger LOG = Logger.getInstance(CompilerReferenceReader.class);

  private final CompilerBackwardReferenceIndex myIndex;

  private final Object myHierarchyLock = new Object(); //access to hierarchy & definition maps
  private final Object myReferenceLock = new Object(); //access to reference & file enumerator maps

  private CompilerReferenceReader(File buildDir) throws IOException {
    myIndex = new CompilerBackwardReferenceIndex(buildDir);
  }

  @Nullable
  TIntHashSet findReferentFileIds(@NotNull LightRef ref, boolean checkBaseClassAmbiguity) {
    LightRef.LightClassHierarchyElementDef hierarchyElement = ref instanceof LightRef.LightClassHierarchyElementDef ?
                                                              (LightRef.LightClassHierarchyElementDef)ref :
                                                              ((LightRef.LightMember)ref).getOwner();
    TIntHashSet set = new TIntHashSet();
    final LightRef.NamedLightRef[] hierarchy = getWholeHierarchy(hierarchyElement, checkBaseClassAmbiguity);
    if (hierarchy == null) return null;
    for (LightRef.NamedLightRef aClass : hierarchy) {
      final LightRef overriderUsage = aClass.override(aClass.getName());
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
  @Nullable
  <T extends PsiElement> Couple<Map<VirtualFile, T[]>> getDirectInheritors(@NotNull PsiNamedElement baseClass,
                                                                           @NotNull LightRef searchElement,
                                                                           @NotNull GlobalSearchScope searchScope,
                                                                           @NotNull GlobalSearchScope dirtyScope,
                                                                           @NotNull Project project,
                                                                           @NotNull FileType fileType,
                                                                           @NotNull CompilerHierarchySearchType searchType) {
    Collection<CompilerBackwardReferenceIndex.LightDefinition> candidates;
    synchronized (myHierarchyLock) {
      candidates = myIndex.getBackwardHierarchyMap().get(searchElement);
    }
    if (candidates == null) return Couple.of(Collections.emptyMap(), Collections.emptyMap());

    GlobalSearchScope effectiveSearchScope = GlobalSearchScope.notScope(dirtyScope).intersectWith(searchScope);
    LanguageLightRefAdapter adapter = CompilerReferenceServiceImpl.findAdapterForFileType(fileType);
    LOG.assertTrue(adapter != null, "adapter is null for file type: " + fileType);
    Class<? extends LightRef> requiredLightRefClass = searchType.getRequiredClass(adapter);
    Map<VirtualFile, List<LightRef>> candidatesPerFile;
    synchronized (myReferenceLock) {
      candidatesPerFile = candidates
        .stream()
        .filter(def -> requiredLightRefClass.isInstance(def.getRef()))
        .map(definition -> {
          final VirtualFile file = findFile(definition.getFileId());
          if (file != null && effectiveSearchScope.contains(file)) {
            return new Object() {
              final VirtualFile containingFile = file;
              final LightRef def = definition.getRef();
            };
          }
          else {
            return null;
          }
        })
        .filter(Objects::nonNull)
        .collect(groupingBy(x -> x.containingFile, mapping(x -> x.def, toList())));
    }

    if (candidatesPerFile.isEmpty()) return Couple.of(Collections.emptyMap(), Collections.emptyMap());

    Map<VirtualFile, T[]> inheritors = new THashMap<>(candidatesPerFile.size());
    Map<VirtualFile, T[]> inheritorCandidates = new THashMap<>();

    final PsiManager psiManager = ReadAction.compute(() -> PsiManager.getInstance(project));

    candidatesPerFile.forEach((file, directInheritors) -> ReadAction.run(() -> {
      final PsiFileWithStubSupport psiFile = (PsiFileWithStubSupport) psiManager.findFile(file);
      final T[] currInheritors = searchType.performSearchInFile(directInheritors, baseClass, myIndex.getByteSeqEum(), psiFile, adapter);
      if (currInheritors.length == directInheritors.size()) {
        inheritors.put(file, currInheritors);
      }
      else {
        inheritorCandidates.put(file, currInheritors);
      }
    }));

    return Couple.of(inheritors, inheritorCandidates);
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

  private void addUsages(LightRef usage, TIntHashSet sink) {
    final Collection<Integer> usageFiles;
    synchronized (myReferenceLock) {
      usageFiles = myIndex.getBackwardReferenceMap().get(usage);
      if (usageFiles != null) {
        for (int fileId : usageFiles) {
          final VirtualFile file = findFile(fileId);
          if (file != null) {
            sink.add(((VirtualFileWithId)file).getId());
          }
        }
      }
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

  @Nullable("return null if the class hierarchy contains ambiguous qualified names")
  private LightRef.NamedLightRef[] getWholeHierarchy(LightRef.LightClassHierarchyElementDef hierarchyElement, boolean checkBaseClassAmbiguity) {
    Set<LightRef.NamedLightRef> result = new THashSet<>();
    Queue<LightRef.NamedLightRef> q = new Queue<>(10);
    q.addLast(hierarchyElement);
    synchronized (myHierarchyLock) {
      while (!q.isEmpty()) {
        LightRef.NamedLightRef curClass = q.pullFirst();
        if (result.add(curClass)) {
          if (checkBaseClassAmbiguity || curClass != hierarchyElement) {
            final Collection<Integer> definitionFiles = myIndex.getBackwardClassDefinitionMap().get(curClass);
            if (definitionFiles.size() != 1) {
              return null;
            }
          }
          final Collection<CompilerBackwardReferenceIndex.LightDefinition> subClassDefs = myIndex.getBackwardHierarchyMap().get(curClass);
          if (subClassDefs != null) {
            for (CompilerBackwardReferenceIndex.LightDefinition subclass : subClassDefs) {
              final LightRef ref = subclass.getRef();
              if (ref instanceof LightRef.LightClassHierarchyElementDef) {
                q.addLast((LightRef.LightClassHierarchyElementDef) ref);
              }
            }
          }
        }
      }
    }
    return result.toArray(new LightRef.NamedLightRef[result.size()]);
  }
}