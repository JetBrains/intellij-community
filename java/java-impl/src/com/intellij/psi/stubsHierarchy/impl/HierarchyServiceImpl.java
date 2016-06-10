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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.impl.java.stubs.hierarchy.IndexTree;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.EverythingGlobalScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubsHierarchy.HierarchyService;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.CachedValueBase;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NotNull;

public class HierarchyServiceImpl extends HierarchyService {
  private static final SingleClassHierarchy EMPTY_HIERARCHY = new SingleClassHierarchy(Symbol.ClassSymbol.EMPTY_ARRAY);
  private final Project myProject;
  private final ProjectFileIndex myFileIndex;
  private final CachedValue<SingleClassHierarchy> myHierarchy;

  public HierarchyServiceImpl(Project project) {
    myProject = project;
    myFileIndex = ProjectFileIndex.SERVICE.getInstance(project);
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

    loadUnits(false, symbols.myNameEnvironment, stubEnter);
    stubEnter.connect1();

    loadUnits(true, symbols.myNameEnvironment, stubEnter);
    stubEnter.connect2();

    return symbols.createHierarchy();
  }

  private void loadUnits(boolean sourceMode, NameEnvironment names, StubEnter stubEnter) {
    GlobalSearchScope scope = new DelegatingGlobalSearchScope(new EverythingGlobalScope(myProject)) {
      @Override
      public boolean contains(@NotNull VirtualFile file) {
        return sourceMode ? myFileIndex.isInSourceContent(file) : myFileIndex.isInLibraryClasses(file);
      }
    };
    ProgressIndicator indicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
    FileBasedIndex index = FileBasedIndex.getInstance();
    for (String packageName : index.getAllKeys(StubHierarchyIndex.INDEX_ID, myProject)) {
      QualifiedName pkg = StringUtil.isEmpty(packageName) ? null : names.fromString(packageName, true);
      index.processValues(StubHierarchyIndex.INDEX_ID, packageName, null, new FileBasedIndex.ValueProcessor<IndexTree.Unit>() {
        int count = 0;

        @Override
        public boolean process(VirtualFile file, IndexTree.Unit unit) {
          if (indicator != null && ++count % 128 == 0) indicator.checkCanceled();
          stubEnter.unitEnter(Translator.internNames(names, unit, ((VirtualFileWithId)file).getId(), pkg));
          return true;
        }
      }, scope);
    }
  }

}
