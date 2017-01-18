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

import com.intellij.compiler.CompilerDirectHierarchyInfo;
import com.intellij.compiler.CompilerReferenceService;
import com.intellij.compiler.backwardRefs.view.CompilerReferenceFindUsagesTestInfo;
import com.intellij.compiler.backwardRefs.view.CompilerReferenceHierarchyTestInfo;
import com.intellij.compiler.backwardRefs.view.DirtyScopeTestInfo;
import com.intellij.compiler.server.BuildManager;
import com.intellij.compiler.server.BuildManagerListener;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.impl.LibraryScopeCache;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.StorageException;
import gnu.trove.THashSet;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.jps.backwardRefs.LightRef;

import java.util.*;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.psi.search.GlobalSearchScope.getScopeRestrictedByFileTypes;
import static com.intellij.psi.search.GlobalSearchScope.notScope;

public class CompilerReferenceServiceImpl extends CompilerReferenceService implements ModificationTracker {
  private final static Logger LOG = Logger.getInstance(CompilerReferenceServiceImpl.class);

  private final Set<FileType> myFileTypes;
  private final DirtyScopeHolder myDirtyScopeHolder;
  private final ProjectFileIndex myProjectFileIndex;
  private final LongAdder myCompilationCount = new LongAdder();
  private final ReentrantReadWriteLock myLock = new ReentrantReadWriteLock();
  private final Lock myReadDataLock = myLock.readLock();
  private final Lock myOpenCloseLock = myLock.writeLock();

  private volatile CompilerReferenceReader myReader;

  public CompilerReferenceServiceImpl(Project project, FileDocumentManager fileDocumentManager, PsiDocumentManager psiDocumentManager) {
    super(project);

    myProjectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    myFileTypes = Stream.of(LanguageLightRefAdapter.INSTANCES).flatMap(a -> a.getFileTypes().stream()).collect(Collectors.toSet());
    myDirtyScopeHolder = new DirtyScopeHolder(this, fileDocumentManager, psiDocumentManager);
  }

  @Override
  public void projectOpened() {
    if (isEnabled()) {
      myProject.getMessageBus().connect(myProject).subscribe(BuildManagerListener.TOPIC, new BuildManagerListener() {
        @Override
        public void buildStarted(Project project, UUID sessionId, boolean isAutomake) {
          if (project == myProject) {
            myDirtyScopeHolder.compilerActivityStarted();
            closeReaderIfNeed();
          }
        }
      });

      CompilerManager compilerManager = CompilerManager.getInstance(myProject);
      compilerManager.addCompilationStatusListener(new CompilationStatusListener() {
        @Override
        public void compilationFinished(boolean aborted, int errors, int warnings, CompileContext compileContext) {
          compilationFinished(compileContext);
        }

        @Override
        public void automakeCompilationFinished(int errors, int warnings, CompileContext compileContext) {
          compilationFinished(compileContext);
        }

        private void compilationFinished(CompileContext context) {
          if (context.getProject() == myProject) {
            Runnable compilationFinished = () -> {
              final Module[] compilationModules = ReadAction.compute(() -> {
                if (myProject.isDisposed()) return null;
                return context.getCompileScope().getAffectedModules();
              });
              if (compilationModules == null) return;
              myDirtyScopeHolder.compilerActivityFinished();
              myCompilationCount.increment();
              openReaderIfNeed();
            };
            executeOnBuildThread(compilationFinished);
          }
        }
      });

      myDirtyScopeHolder.installVFSListener();

      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
          CompileScope projectCompileScope = compilerManager.createProjectCompileScope(myProject);
          boolean isUpToDate = compilerManager.isUpToDate(projectCompileScope);
          executeOnBuildThread(() -> {
            myDirtyScopeHolder.upToDateChecked(isUpToDate);
            if (isUpToDate) {
              myCompilationCount.increment();
              openReaderIfNeed();
            }
          });
        });
      }
    }
  }

  @Override
  public void projectClosed() {
    closeReaderIfNeed();
  }

  @Nullable
  @Override
  public GlobalSearchScope getScopeWithoutCodeReferences(@NotNull PsiElement element) {
    if (!isServiceEnabledFor(element)) return null;

    try {
      return CachedValuesManager.getCachedValue(element,
                                                () -> CachedValueProvider.Result.create(calculateScopeWithoutReferences(element),
                                                                                        PsiModificationTracker.MODIFICATION_COUNT,
                                                                                        this));
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Exception e) {
      LOG.error("an exception during scope without code references calculation", e);
      return null;
    }
  }

  @Nullable
  @Override
  public CompilerDirectHierarchyInfo getDirectInheritors(@NotNull PsiNamedElement aClass,
                                                         @NotNull GlobalSearchScope useScope,
                                                         @NotNull GlobalSearchScope searchScope,
                                                         @NotNull FileType searchFileType) {
    return getHierarchyInfo(aClass, useScope, searchScope, searchFileType, CompilerHierarchySearchType.DIRECT_INHERITOR);
  }

  @Nullable
  @Override
  public CompilerDirectHierarchyInfo getFunExpressions(@NotNull PsiNamedElement functionalInterface,
                                                       @NotNull GlobalSearchScope useScope,
                                                       @NotNull GlobalSearchScope searchScope,
                                                       @NotNull FileType searchFileType) {
    return getHierarchyInfo(functionalInterface, useScope, searchScope, searchFileType, CompilerHierarchySearchType.FUNCTIONAL_EXPRESSION);
  }

  @Nullable
  private CompilerHierarchyInfoImpl getHierarchyInfo(@NotNull PsiNamedElement aClass,
                                                     @NotNull GlobalSearchScope useScope,
                                                     @NotNull GlobalSearchScope searchScope,
                                                     @NotNull FileType searchFileType,
                                                     @NotNull CompilerHierarchySearchType searchType) {
    if (!isServiceEnabledFor(aClass) || searchScope == LibraryScopeCache.getInstance(myProject).getLibrariesOnlyScope()) return null;

    try {
      Map<VirtualFile, Object[]> candidatesPerFile = ReadAction.compute(() -> {
        if (myProject.isDisposed()) throw new ProcessCanceledException();
        return CachedValuesManager.getCachedValue(aClass, () -> CachedValueProvider.Result.create(
          new ConcurrentFactoryMap<HierarchySearchKey, Map<VirtualFile, Object[]>>() {
            @Nullable
            @Override
            protected Map<VirtualFile, Object[]> create(HierarchySearchKey key) {
              return calculateDirectInheritors(aClass,
                                               useScope,
                                               key.mySearchFileType,
                                               key.mySearchType);
            }
          }, PsiModificationTracker.MODIFICATION_COUNT, this)).get(new HierarchySearchKey(searchType, searchFileType));
      });

      if (candidatesPerFile == null) return null;
      GlobalSearchScope dirtyScope = myDirtyScopeHolder.getDirtyScope();
      if (ElementPlace.LIB == ReadAction.compute(() -> ElementPlace.get(aClass.getContainingFile().getVirtualFile(), myProjectFileIndex))) {
        dirtyScope = dirtyScope.union(LibraryScopeCache.getInstance(myProject).getLibrariesOnlyScope());
      }
      return new CompilerHierarchyInfoImpl(candidatesPerFile, aClass, dirtyScope, searchScope, myProject, searchFileType, searchType);
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Exception e) {
      LOG.error("an exception during hierarchy calculation", e);
      return null;
    }
  }

  private boolean isServiceEnabledFor(PsiElement element) {
    if (!isServiceEnabled()) return false;
    PsiFile file = ReadAction.compute(() -> element.getContainingFile());
    return file != null && !InjectedLanguageManager.getInstance(myProject).isInjectedFragment(file);
  }

  private boolean isServiceEnabled() {
    return myReader != null && isEnabled();
  }

  private Map<VirtualFile, Object[]> calculateDirectInheritors(@NotNull PsiNamedElement aClass,
                                                               @NotNull GlobalSearchScope useScope,
                                                               @NotNull FileType searchFileType,
                                                               @NotNull CompilerHierarchySearchType searchType) {
    final CompilerElementInfo searchElementInfo = asCompilerElements(aClass, false);
    if (searchElementInfo == null) return null;
    LightRef searchElement = searchElementInfo.searchElements[0];

    myReadDataLock.lock();
    try {
      if (myReader == null) return null;
      try {
        return myReader.getDirectInheritors(searchElement, useScope, myDirtyScopeHolder.getDirtyScope(), searchFileType, searchType);
      }
      catch (StorageException e) {
        throw new RuntimeException(e);
      }
    } finally {
      myReadDataLock.unlock();
    }
  }

  @Nullable
  private GlobalSearchScope calculateScopeWithoutReferences(@NotNull PsiElement element) {
    TIntHashSet referentFileIds = getReferentFileIds(element);
    if (referentFileIds == null) return null;

    return getScopeRestrictedByFileTypes(new ScopeWithoutReferencesOnCompilation(referentFileIds, myProjectFileIndex).intersectWith(notScope(
      myDirtyScopeHolder.getDirtyScope())),
                                         myFileTypes.toArray(new FileType[myFileTypes.size()]));
  }

  @Nullable
  private TIntHashSet getReferentFileIds(@NotNull PsiElement element) {
    final CompilerElementInfo compilerElementInfo = asCompilerElements(element, true);
    if (compilerElementInfo == null) return null;

    myReadDataLock.lock();
    try {
      if (myReader == null) return null;
      TIntHashSet referentFileIds = new TIntHashSet();
      for (LightRef ref : compilerElementInfo.searchElements) {
        try {
          final TIntHashSet referents = myReader.findReferentFileIds(ref, compilerElementInfo.place == ElementPlace.SRC);
          if (referents == null) return null;
          referentFileIds.addAll(referents.toArray());
        }
        catch (StorageException e) {
          throw new RuntimeException(e);
        }
         }
      return referentFileIds;

    } finally {
      myReadDataLock.unlock();
    }
  }

  @Nullable
  private CompilerElementInfo asCompilerElements(@NotNull PsiElement psiElement, boolean buildHierarchyForLibraryElements) {
    myReadDataLock.lock();
    try {
      if (myReader == null) return null;
      VirtualFile file = PsiUtilCore.getVirtualFile(psiElement);
      if (file == null) return null;
      ElementPlace place = ElementPlace.get(file, myProjectFileIndex);
      if (place == null || (place == ElementPlace.SRC && myDirtyScopeHolder.contains(file))) {
        return null;
      }
      final LanguageLightRefAdapter adapter = findAdapterForFileType(file.getFileType());
      if (adapter == null) return null;
      final LightRef ref = adapter.asLightUsage(psiElement, myReader.getNameEnumerator());
      if (ref == null) return null;
      if (place == ElementPlace.LIB && buildHierarchyForLibraryElements) {
        final List<LightRef> elements = adapter.getHierarchyRestrictedToLibraryScope(ref,
                                                                                     psiElement,
                                                                                     myReader.getNameEnumerator(),
                                                                                     LibraryScopeCache.getInstance(myProject).getLibrariesOnlyScope());
        final LightRef[] fullHierarchy = new LightRef[elements.size() + 1];
        fullHierarchy[0] = ref;
        int i = 1;
        for (LightRef element : elements) {
          fullHierarchy[i++] = element;
        }
        return new CompilerElementInfo(place, fullHierarchy);
      }
      else {
        return new CompilerElementInfo(place, ref);
      }
    } finally {
      myReadDataLock.unlock();
    }
  }

  private void closeReaderIfNeed() {
    myOpenCloseLock.lock();
    try {
      if (myReader != null) {
        myReader.close();
        myReader = null;
      }
    } finally {
      myOpenCloseLock.unlock();
    }
  }

  private void openReaderIfNeed() {
    myOpenCloseLock.lock();
    try {
      if (myProject.isOpen()) {
        myReader = CompilerReferenceReader.create(myProject);
      }
    } finally {
      myOpenCloseLock.unlock();
    }
  }

  ProjectFileIndex getFileIndex() {
    return myProjectFileIndex;
  }

  Set<FileType> getFileTypes() {
    return myFileTypes;
  }

  Project getProject() {
    return myProject;
  }

  @Nullable
  static LanguageLightRefAdapter findAdapterForFileType(@NotNull FileType fileType) {
    for (LanguageLightRefAdapter adapter : LanguageLightRefAdapter.INSTANCES) {
      if (adapter.getFileTypes().contains(fileType)) {
        return adapter;
      }
    }
    return null;
  }

  private static void executeOnBuildThread(Runnable compilationFinished) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      compilationFinished.run();
    } else {
      BuildManager.getInstance().runCommand(compilationFinished);
    }
  }

  private enum ElementPlace {
    SRC, LIB;

    private static ElementPlace get(VirtualFile file, ProjectFileIndex index) {
      if (file == null) return null;
      return index.isInSourceContent(file) ? SRC : ((index.isInLibrarySource(file) || index.isInLibraryClasses(file)) ? LIB : null);
    }
  }

  private static class ScopeWithoutReferencesOnCompilation extends GlobalSearchScope {
    private final TIntHashSet myReferentIds;
    private final ProjectFileIndex myIndex;

    private ScopeWithoutReferencesOnCompilation(TIntHashSet ids, ProjectFileIndex index) {
      myReferentIds = ids;
      myIndex = index;
    }

    @Override
    public boolean contains(@NotNull VirtualFile file) {
      return file instanceof VirtualFileWithId && myIndex.isInSourceContent(file) && !myReferentIds.contains(((VirtualFileWithId)file).getId());
    }

    @Override
    public int compare(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
      return 0;
    }

    @Override
    public boolean isSearchInModuleContent(@NotNull Module aModule) {
      return true;
    }

    @Override
    public boolean isSearchInLibraries() {
      return false;
    }
  }

  @Override
  public long getModificationCount() {
    return myCompilationCount.longValue();
  }

  static class CompilerElementInfo {
    final ElementPlace place;
    final LightRef[] searchElements;

    private CompilerElementInfo(ElementPlace place, LightRef... searchElements) {
      this.place = place;
      this.searchElements = searchElements;
    }
  }

  private static class HierarchySearchKey {
    private final CompilerHierarchySearchType mySearchType;
    private final FileType mySearchFileType;

    HierarchySearchKey(CompilerHierarchySearchType searchType, FileType searchFileType) {
      mySearchType = searchType;
      mySearchFileType = searchFileType;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      HierarchySearchKey key = (HierarchySearchKey)o;
      return mySearchType == key.mySearchType && mySearchFileType == key.mySearchFileType;
    }

    @Override
    public int hashCode() {
      return 31 * mySearchType.hashCode() + mySearchFileType.hashCode();
    }
  }

  @TestOnly
  @Nullable
  public Set<VirtualFile> getReferentFiles(@NotNull PsiElement element) {
    FileBasedIndex fileIndex = FileBasedIndex.getInstance();
    final TIntHashSet ids = getReferentFileIds(element);
    if (ids == null) return null;
    Set<VirtualFile> fileSet = new THashSet<>();
    ids.forEach(id -> {
      final VirtualFile vFile = fileIndex.findFileById(myProject, id);
      assert vFile != null;
      fileSet.add(vFile);
      return true;
    });
    return fileSet;
  }

  @TestOnly
  @NotNull
  public DirtyScopeHolder getDirtyScopeHolder() {
    return myDirtyScopeHolder;
  }

  @NotNull
  public CompilerReferenceFindUsagesTestInfo getTestFindUsages(@NotNull PsiElement element) {
    myReadDataLock.lock();
    try {
      final TIntHashSet referentFileIds = getReferentFileIds(element);
      final DirtyScopeTestInfo dirtyScopeInfo = myDirtyScopeHolder.getState();
      return new CompilerReferenceFindUsagesTestInfo(referentFileIds, dirtyScopeInfo, myProject);
    } finally {
      myReadDataLock.unlock();
    }
  }

  @NotNull
  public CompilerReferenceHierarchyTestInfo getTestHierarchy(@NotNull PsiNamedElement element, @NotNull GlobalSearchScope scope, @NotNull FileType fileType) {
    myReadDataLock.lock();
    try {
      final CompilerHierarchyInfoImpl hierarchyInfo = getHierarchyInfo(element, scope, scope, fileType, CompilerHierarchySearchType.DIRECT_INHERITOR);
      final DirtyScopeTestInfo dirtyScopeInfo = myDirtyScopeHolder.getState();
      return new CompilerReferenceHierarchyTestInfo(hierarchyInfo, dirtyScopeInfo);
    } finally {
      myReadDataLock.unlock();
    }
  }

  @NotNull
  public CompilerReferenceHierarchyTestInfo getTestFunExpressions(@NotNull PsiNamedElement element, @NotNull GlobalSearchScope scope, @NotNull FileType fileType) {
    myReadDataLock.lock();
    try {
      final CompilerHierarchyInfoImpl hierarchyInfo = getHierarchyInfo(element, scope, scope, fileType, CompilerHierarchySearchType.FUNCTIONAL_EXPRESSION);
      final DirtyScopeTestInfo dirtyScopeInfo = myDirtyScopeHolder.getState();
      return new CompilerReferenceHierarchyTestInfo(hierarchyInfo, dirtyScopeInfo);
    } finally {
      myReadDataLock.unlock();
    }
  }
}
