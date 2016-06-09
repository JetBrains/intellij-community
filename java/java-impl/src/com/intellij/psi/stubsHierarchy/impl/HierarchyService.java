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

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.psi.impl.java.stubs.index.JavaStubIndexKeys;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubsHierarchy.stubs.Unit;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.reference.SoftReference;
import com.intellij.util.CachedValueBase;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class HierarchyService {
  private static final TObjectHashingStrategy<Unit> UNIT_HASHING_STRATEGY = new TObjectHashingStrategy<Unit>() {
    @Override
    public int computeHashCode(Unit object) {
      return object.myClasses[0].myClassAnchor.myFileId;
    }

    @Override
    public boolean equals(Unit o1, Unit o2) {
      return computeHashCode(o1) == computeHashCode(o2);
    }
  };
  private final Project myProject;
  private final ProjectFileIndex myFileIndex;

  private volatile SoftReference<NameEnvironment> myNamesCache = null;
  private final CachedValue<SingleClassHierarchy> myHierarchy ;

  public static HierarchyService instance(@NotNull Project project) {
    return ServiceManager.getService(project, HierarchyService.class);
  }

  public HierarchyService(Project project) {
    myProject = project;
    myFileIndex = ProjectFileIndex.SERVICE.getInstance(project);
    myHierarchy = CachedValuesManager.getManager(project).createCachedValue(
      () -> CachedValueProvider.Result.create(buildHierarchy(), PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT),
      false);
  }

  public SingleClassHierarchy getSingleClassHierarchy() {
    synchronized (myHierarchy) { //the calculation is memory-intensive, don't allow multiple threads to do it
      return myHierarchy.getValue();
    }
  }

  public void clearHierarchy() {
    ((CachedValueBase)myHierarchy).clear();
  }

  private SingleClassHierarchy buildHierarchy() {
    NameEnvironment names = obtainNames();
    Symbols symbols = new Symbols(names);
    loadSymbols(names, symbols);
    return symbols.createHierarchy();
  }

  @NotNull
  private NameEnvironment obtainNames() {
    NameEnvironment names = SoftReference.dereference(myNamesCache);
    if (names == null) {
      myNamesCache = new SoftReference<NameEnvironment>(names = new NameEnvironment());
    }
    return names;
  }

  private void loadSymbols(NameEnvironment names, Symbols symbols) {
    StubEnter stubEnter = new StubEnter(names, symbols);

    loadUnits(false, names).forEach(stubEnter::unitEnter);
    stubEnter.connect1();

    loadUnits(true, names).forEach(stubEnter::unitEnter);
    stubEnter.connect2();
  }

  private Set<Unit> loadUnits(boolean sourceMode, NameEnvironment names) {
    Set<Unit> result = new THashSet<>(UNIT_HASHING_STRATEGY);
    StubIndex.getInstance().processAllKeys(JavaStubIndexKeys.UNITS, myProject, unit -> {
      Unit compact = shouldProcess(sourceMode, unit.myFileId) ? Translator.translate(names, unit) : null;
      if (compact != null && compact.myClasses.length > 0) {
        result.remove(compact); // there can be several (outdated) stub keys for the same file id, only the last one counts
        result.add(compact);
      }
      return true;
    });
    return result;
  }

  private boolean shouldProcess(boolean sourceMode, final int fileId) {
    VirtualFile file = PersistentFS.getInstance().findFileById(fileId);
    if (file == null) {
      return false;
    }
    return sourceMode ? myFileIndex.isInSourceContent(file) : myFileIndex.isInLibraryClasses(file);
  }

}
