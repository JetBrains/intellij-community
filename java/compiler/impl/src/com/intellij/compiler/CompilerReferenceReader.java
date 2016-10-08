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
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Queue;
import com.sun.tools.javac.util.Convert;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.backwardRefs.CompilerBackwardReferenceIndex;
import org.jetbrains.jps.backwardRefs.LightUsage;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.*;

class CompilerReferenceReader {
  private final static Logger LOG = Logger.getInstance(CompilerReferenceReader.class);

  private final CompilerBackwardReferenceIndex myIndex;

  private CompilerReferenceReader(File buildDir) throws IOException {
    myIndex = new CompilerBackwardReferenceIndex(buildDir);
  }

  @Nullable
  TIntHashSet findReferentFileIds(@NotNull CompilerElement element,
                                         @NotNull CompilerSearchAdapter adapter,
                                         boolean checkBaseClassAmbiguity) {
    LightUsage usage = asLightUsage(element);

    TIntHashSet set = new TIntHashSet();
    if (adapter.needOverrideElement()) {
      final LightUsage[] hierarchy = getWholeHierarchy(usage.getOwner(), checkBaseClassAmbiguity);
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

  @NotNull
  <T extends PsiNamedElement> Couple<Map<VirtualFile, T[]>> getDirectInheritors(@NotNull CompilerElement element,
                                                                                @NotNull PsiNamedElement psiElement,
                                                                                @NotNull CompilerDirectInheritorSearchAdapter<T> adapter,
                                                                                @NotNull GlobalSearchScope searchScope,
                                                                                @NotNull GlobalSearchScope dirtyScope,
                                                                                @NotNull Project project,
                                                                                FileType... fileTypes) {
    final LightUsage aClass = asLightUsage(element);
    Collection<CompilerBackwardReferenceIndex.LightDefinition> candidates = myIndex.getBackwardHierarchyMap().get(aClass);
    if (candidates == null) return Couple.of(Collections.emptyMap(), Collections.emptyMap());

    final Set<FileType> fileTypeSet = ContainerUtil.set(fileTypes);

    final Set<Class<? extends LightUsage>> suitableClasses = new THashSet<>();
    for (LanguageLightUsageConverter converter : LanguageLightUsageConverter.INSTANCES) {
      if (fileTypeSet.contains(converter.getFileSourceType())) {
        suitableClasses.addAll(converter.getLanguageLightUsageClasses());
      }
    }

    final GlobalSearchScope effectiveSearchScope = GlobalSearchScope.notScope(dirtyScope).intersectWith(searchScope);

    Map<VirtualFile, SmartList<String>> perFileCandidates = candidates
      .stream()
      .filter(def -> suitableClasses.contains(def.getUsage().getClass()))
      .map(definition -> {
        final VirtualFile file = findFile(definition.getFileId());
        return file != null && effectiveSearchScope.contains(file) ? new DecodedInheritorCandidate(getName(definition), file) : null;
      })
      .filter(Objects::nonNull)
      .collect(groupingBy(DecodedInheritorCandidate::getDeclarationFile, mapping(DecodedInheritorCandidate::getQName, toCollection(SmartList::new))));

    if (perFileCandidates.isEmpty()) return Couple.of(Collections.emptyMap(), Collections.emptyMap());

    Map<VirtualFile, T[]> inheritors = new THashMap<>(perFileCandidates.size());
    Map<VirtualFile, T[]> inheritorCandidates = new THashMap<>();

    perFileCandidates.forEach((file, directInheritors) -> {
      final T[] currInheritors = adapter.getCandidatesFromFile(directInheritors, psiElement, file, project);
      if (currInheritors.length == directInheritors.size()) {
        inheritors.put(file, currInheritors);
      } else {
        inheritorCandidates.put(file, currInheritors);
      }
    });

    return Couple.of(inheritors, inheritorCandidates);
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

  private void addUsages(LightUsage usage, TIntHashSet sink) {
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
  private LightUsage[] getWholeHierarchy(LightUsage aClass, boolean checkBaseClassAmbiguity) {
    Set<LightUsage> result = new THashSet<>();
    Queue<LightUsage> q = new Queue<>(10);
    q.addLast(aClass);
    while (!q.isEmpty()) {
      LightUsage curClass = q.pullFirst();
      if (result.add(curClass)) {
        if (checkBaseClassAmbiguity || curClass != aClass) {
          final Collection<Integer> definitionFiles = myIndex.getBackwardClassDefinitionMap().get(curClass);
          if (definitionFiles.size() != 1) {
            return null;
          }
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

  @NotNull
  private String getName(CompilerBackwardReferenceIndex.LightDefinition def) {
    try {
      return Convert.utf2string(ObjectUtils.notNull(myIndex.getByteSeqEum().valueOf(def.getUsage().getName())));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static class DecodedInheritorCandidate {
    private final String qName;
    private final VirtualFile declarationFile;

    private DecodedInheritorCandidate(String name, VirtualFile file) {
      qName = name;
      declarationFile = file;
    }

    public VirtualFile getDeclarationFile() {
      return declarationFile;
    }

    public String getQName() {
      return qName;
    }
  }
}