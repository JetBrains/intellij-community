/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi.stubsHierarchy.impl;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.impl.java.stubs.hierarchy.IndexTree;
import com.intellij.psi.stubsHierarchy.HierarchyService;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.CachedValueBase;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexImpl;
import org.jetbrains.annotations.NotNull;

import java.util.BitSet;
import java.util.stream.IntStream;

public class HierarchyServiceImpl extends HierarchyService {
  private static final SingleClassHierarchy EMPTY_HIERARCHY = new SingleClassHierarchy(Symbol.ClassSymbol.EMPTY_ARRAY, new AnchorRepository());
  private static final int KEY_COUNT = 20;
  static final int[] SOURCE_KEYS = IntStream.rangeClosed(-KEY_COUNT, -1).toArray();
  static final int[] BINARY_KEYS = IntStream.rangeClosed(1, KEY_COUNT).toArray();
  private final Project myProject;
  private final CachedValue<SingleClassHierarchy> myHierarchy;

  public HierarchyServiceImpl(Project project) {
    myProject = project;
    myHierarchy = CachedValuesManager.getManager(project).createCachedValue(
      () -> CachedValueProvider.Result.create(buildHierarchy(), PsiModificationTracker.MODIFICATION_COUNT),
      false);
  }

  @Override
  @NotNull
  public SingleClassHierarchy getHierarchy() {
    if (!IndexTree.STUB_HIERARCHY_ENABLED) {
      return EMPTY_HIERARCHY;
    }

    synchronized (myHierarchy) { //the calculation is memory-intensive, don't allow multiple threads to do it
      return myHierarchy.getValue();
    }
  }

  @Override
  public void clearHierarchy() {
    ((CachedValueBase)myHierarchy).clear();
  }

  private SingleClassHierarchy buildHierarchy() {
    Symbols symbols = new Symbols();
    StubEnter stubEnter = new StubEnter(symbols);
    IdSets idSets = IdSets.getIdSets(myProject);

    loadUnits(idSets.libraryFiles, BINARY_KEYS, stubEnter);
    stubEnter.connect1();

    loadUnits(idSets.sourceFiles, SOURCE_KEYS, stubEnter);
    stubEnter.connect2();

    return symbols.createHierarchy();
  }

  private void loadUnits(BitSet files, int[] indexKeys, StubEnter stubEnter) {
    FileBasedIndexImpl.IdValueProcessor<SerializedUnit> processor = new FileBasedIndexImpl.IdValueProcessor<SerializedUnit>() {
      final ProgressIndicator indicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
      int count = 0;
      @Override
      public boolean process(int fileId, SerializedUnit unit) {
        if (indicator != null && ++count % 128 == 0) indicator.checkCanceled();
        if (files.get(fileId)) {
          unit.readUnit(stubEnter, fileId);
        }
        return true;
      }
    };

    for (int indexKey : indexKeys) {
      ((FileBasedIndexImpl)FileBasedIndex.getInstance()).processAllValues(StubHierarchyIndex.INDEX_ID, indexKey, myProject, processor);
    }
  }

  private static class IdSets {
    final BitSet sourceFiles = new BitSet();
    final BitSet libraryFiles = new BitSet();

    static IdSets getIdSets(@NotNull Project project) {
      return CachedValuesManager.getManager(project).getCachedValue(project, () -> {
        IdSets answer = new IdSets();
        ProjectFileIndex index = ProjectFileIndex.SERVICE.getInstance(project);
        FileBasedIndex.getInstance().iterateIndexableFiles(file -> {
          if (!file.isDirectory() && file instanceof VirtualFileWithId) {
            if (index.isInSourceContent(file)) {
              answer.sourceFiles.set(((VirtualFileWithId) file).getId());
            }
            else if (index.isInLibraryClasses(file)) {
              answer.libraryFiles.set(((VirtualFileWithId) file).getId());
            }
          }
          return true;
        }, project, ProgressIndicatorProvider.getGlobalProgressIndicator());
        return CachedValueProvider.Result.create(answer, ProjectRootManager.getInstance(project), VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS);
      });
    }
  }
}
