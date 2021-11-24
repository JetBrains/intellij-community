// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.backwardRefs;

import com.intellij.compiler.CompilerDirectHierarchyInfo;
import com.intellij.compiler.CompilerReferenceService;
import com.intellij.compiler.backwardRefs.view.CompilerReferenceFindUsagesTestInfo;
import com.intellij.compiler.backwardRefs.view.CompilerReferenceHierarchyTestInfo;
import com.intellij.compiler.backwardRefs.view.DirtyScopeTestInfo;
import com.intellij.compiler.server.BuildManager;
import com.intellij.compiler.server.PortableCachesLoadListener;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.messages.MessageBusConnection;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.jps.backwardRefs.CompilerRef;
import org.jetbrains.jps.backwardRefs.NameEnumerator;
import org.jetbrains.jps.backwardRefs.index.CompilerReferenceIndex;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static com.intellij.psi.search.GlobalSearchScope.getScopeRestrictedByFileTypes;
import static com.intellij.psi.search.GlobalSearchScope.notScope;

public abstract class CompilerReferenceServiceBase<Reader extends CompilerReferenceReader<?>> implements CompilerReferenceService,
                                                                                                         ModificationTracker,
                                                                                                         Disposable {
  private static final Logger LOG = Logger.getInstance(CompilerReferenceServiceBase.class);

  private final Set<FileType> myFileTypes;
  private final DirtyScopeHolder myDirtyScopeHolder;
  private final ProjectFileIndex myProjectFileIndex;
  private final LongAdder myCompilationCount = new LongAdder();
  protected final ReentrantReadWriteLock myLock = new ReentrantReadWriteLock();
  protected final Lock myReadDataLock = myLock.readLock();
  private final Lock myOpenCloseLock = myLock.writeLock();
  protected final Project myProject;
  private final CompilerReferenceReaderFactory<? extends Reader> myReaderFactory;
  // index build start/finish callbacks are not ordered, so "build1 started" -> "build2 started" -> "build1 finished" -> "build2 finished" is expected sequence
  private int myActiveBuilds = 0;

  protected volatile Reader myReader;
  private final ThreadLocal<Boolean> myIsInsideLibraryScope = ThreadLocal.withInitial(() -> false);

  public CompilerReferenceServiceBase(Project project,
                                      CompilerReferenceReaderFactory<? extends Reader> readerFactory,
                                      BiConsumer<? super MessageBusConnection, ? super Set<String>> compilationAffectedModulesSubscription) {
    myProject = project;
    myReaderFactory = readerFactory;
    myProjectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    myFileTypes = LanguageCompilerRefAdapter.EP_NAME.getExtensionList().stream().flatMap(a -> a.getFileTypes().stream()).collect(Collectors.toSet());
    Set<FileType> affectedFileTypes = LanguageCompilerRefAdapter.EP_NAME.getExtensionList().stream().flatMap(a -> a.getAffectedFileTypes().stream()).collect(Collectors.toSet());
    myDirtyScopeHolder = new DirtyScopeHolder(project,
                                              affectedFileTypes,
                                              myProjectFileIndex,
                                              this,
                                              this,
                                              FileDocumentManager.getInstance(),
                                              PsiDocumentManager.getInstance(project),
                                              compilationAffectedModulesSubscription);

    if (!CompilerReferenceService.isEnabled()) {
      LOG.error("CompilerReferenceService is disabled, but service was requested");
      return;
    }

    myDirtyScopeHolder.installVFSListener(this);
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      CompilerManager compilerManager = CompilerManager.getInstance(project);
      boolean isUpToDate;
      File buildDir = BuildManager.getInstance().getProjectSystemDirectory(project);

      boolean validIndexExists = buildDir != null
                                 && CompilerReferenceIndex.exists(buildDir)
                                 && !CompilerReferenceIndex.versionDiffers(buildDir, myReaderFactory.expectedIndexVersion());

      if (validIndexExists) {
        CompileScope projectCompileScope = compilerManager.createProjectCompileScope(project);
        isUpToDate = compilerManager.isUpToDate(projectCompileScope);
      }
      else {
        isUpToDate = false;
      }
      executeOnBuildThread(() -> {
        if (isUpToDate) {
          openReaderIfNeeded(IndexOpenReason.UP_TO_DATE_CACHE);
        }
        else {
          markAsOutdated();
        }
      });

      project.getMessageBus().connect(this).subscribe(PortableCachesLoadListener.TOPIC, new PortableCachesLoadListener() {
        @Override
        public void loadingStarted() {
          closeReaderIfNeeded(IndexCloseReason.SHUTDOWN);
        }
      });
    }

    Disposer.register(this, () -> closeReaderIfNeeded(IndexCloseReason.SHUTDOWN));
  }

  @Nullable
  @Override
  public GlobalSearchScope getScopeWithCodeReferences(@NotNull PsiElement element) {
    if (!isServiceEnabledFor(element)) return null;

    try {
      return CachedValuesManager.getCachedValue(element,
                                                () -> CachedValueProvider.Result.create(
                                                  buildScopeWithReferences(getReferentFiles(element), element),
                                                  PsiModificationTracker.MODIFICATION_COUNT,
                                                  this));
    }
    catch (RuntimeException e1) {
      return onException(e1, "scope without code references");
    }
  }

  @Nullable
  @Override
  public GlobalSearchScope getScopeWithImplicitToStringCodeReferences(@NotNull PsiElement aClass) {
    if (!isServiceEnabledFor(aClass)) return null;

    try {
      return CachedValuesManager.getCachedValue(aClass,
                                                () -> CachedValueProvider.Result.create(
                                                  buildScopeWithReferences(getReferentFileIdsViaImplicitToString(aClass), aClass),
                                                  PsiModificationTracker.MODIFICATION_COUNT,
                                                  this));
    }
    catch (RuntimeException e1) {
      return onException(e1, "scope without implicit toString references");
    }
  }

  @Nullable
  @Override
  public CompilerDirectHierarchyInfo getDirectInheritors(@NotNull PsiNamedElement aClass,
                                                         @NotNull GlobalSearchScope searchScope,
                                                         @NotNull FileType searchFileType) {
    return getHierarchyInfo(aClass, searchScope, searchFileType, CompilerHierarchySearchType.DIRECT_INHERITOR);
  }

  @Nullable
  @Override
  public CompilerDirectHierarchyInfo getFunExpressions(@NotNull PsiNamedElement functionalInterface,
                                                       @NotNull GlobalSearchScope searchScope,
                                                       @NotNull FileType searchFileType) {
    return getHierarchyInfo(functionalInterface, searchScope, searchFileType, CompilerHierarchySearchType.FUNCTIONAL_EXPRESSION);
  }

  @Nullable
  @Override
  public Integer getCompileTimeOccurrenceCount(@NotNull PsiElement element, boolean isConstructorSuggestion) {
    if (!isServiceEnabledFor(element)) return null;
    try {
      return CachedValuesManager.getCachedValue(element,
                                                () -> CachedValueProvider.Result.create(ConcurrentFactoryMap.createMap(
                                                  (Boolean constructorSuggestion) -> calculateOccurrenceCount(element,
                                                                                                    constructorSuggestion.booleanValue())),
                                                                                        PsiModificationTracker.MODIFICATION_COUNT,
                                                                                        this)).get(Boolean.valueOf(isConstructorSuggestion));
    }
    catch (RuntimeException e) {
      return onException(e, "weighting for completion");
    }
  }

  private Integer calculateOccurrenceCount(@NotNull PsiElement element, boolean isConstructorSuggestion) {
    LanguageCompilerRefAdapter adapter = null;
    if (isConstructorSuggestion) {
      adapter = ReadAction.compute(() -> LanguageCompilerRefAdapter.findAdapter(element));
      if (adapter == null || !adapter.isClass(element)) {
        return null;
      }
    }
    final CompilerElementInfo searchElementInfo = asCompilerElements(element, false, false);
    if (searchElementInfo == null) return null;

    if (!myReadDataLock.tryLock()) return null;
    try {
      if (myReader == null) return null;
      try {
        if (isConstructorSuggestion) {
          int constructorOccurrences = 0;
          for (PsiElement constructor : adapter.getInstantiableConstructors(element)) {
            final CompilerRef constructorRef = adapter.asCompilerRef(constructor, myReader.getNameEnumerator());
            if (constructorRef != null) {
              constructorOccurrences += myReader.getOccurrenceCount(constructorRef);
            }
          }
          final Integer anonymousCount = myReader.getAnonymousCount(
            (CompilerRef.CompilerClassHierarchyElementDef)searchElementInfo.searchElements.get(0),
            searchElementInfo.place == ElementPlace.SRC
          );
          return anonymousCount == null ? constructorOccurrences : (constructorOccurrences + anonymousCount);
        } else {
          return myReader.getOccurrenceCount(searchElementInfo.searchElements.get(0));
        }
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    } finally {
      myReadDataLock.unlock();
    }
  }

  @Nullable
  private CompilerHierarchyInfoImpl getHierarchyInfo(@NotNull PsiNamedElement aClass,
                                                     @NotNull GlobalSearchScope searchScope,
                                                     @NotNull FileType searchFileType,
                                                     @NotNull CompilerHierarchySearchType searchType) {
    if (!isServiceEnabledFor(aClass)) return null;

    try {
      Map<VirtualFile, SearchId[]> candidatesPerFile = ReadAction.compute(() -> {
        if (myProject.isDisposed()) throw new ProcessCanceledException();
        return CachedValuesManager.getCachedValue(aClass, () -> CachedValueProvider.Result.create(
          ConcurrentFactoryMap.createMap((HierarchySearchKey key) -> calculateDirectInheritors(aClass,
                                                                                               key.mySearchFileType,
                                                                                               key.mySearchType)),
          PsiModificationTracker.MODIFICATION_COUNT, this)).get(new HierarchySearchKey(searchType, searchFileType));
      });

      if (candidatesPerFile == null) return null;
      GlobalSearchScope dirtyScope = myDirtyScopeHolder.getDirtyScope();
      if (ElementPlace.LIB == ReadAction.compute(() -> ElementPlace.get(aClass.getContainingFile().getVirtualFile(), myProjectFileIndex))) {
        dirtyScope = dirtyScope.union(ProjectScope.getLibrariesScope(myProject));
      }
      return new CompilerHierarchyInfoImpl(candidatesPerFile, aClass, dirtyScope, searchScope, myProject, searchFileType, searchType);
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (RuntimeException e) {
      return onException(e, "hierarchy");
    }
  }

  private boolean isServiceEnabledFor(PsiElement element) {
    if (!isActive() || isInsideLibraryScope()) return false;
    PsiFile file = ReadAction.compute(() -> element.getContainingFile());
    return file != null && !InjectedLanguageManager.getInstance(myProject).isInjectedFragment(file);
  }

  @Override
  public boolean isActive() {
    return myReader != null && CompilerReferenceService.isEnabled();
  }

  private Map<VirtualFile, SearchId[]> calculateDirectInheritors(@NotNull PsiElement aClass,
                                                                 @NotNull FileType searchFileType,
                                                                 @NotNull CompilerHierarchySearchType searchType) {
    SearchScope scope = aClass.getUseScope();
    if (!(scope instanceof GlobalSearchScope)) return null;
    final CompilerElementInfo searchElementInfo = asCompilerElements(aClass, false, true);
    if (searchElementInfo == null) return null;
    CompilerRef searchElement = searchElementInfo.searchElements.get(0);

    if (!myReadDataLock.tryLock()) return null;
    try {
      if (myReader == null) return null;
      try {
        return myReader.getDirectInheritors(searchElement, ((GlobalSearchScope)scope), myDirtyScopeHolder.getDirtyScope(), searchFileType, searchType);
      }
      catch (StorageException e) {
        throw new RuntimeException(e);
      }
    } finally {
      myReadDataLock.unlock();
    }
  }

  @Nullable
  private GlobalSearchScope buildScopeWithReferences(@Nullable Set<VirtualFile> referentFiles, @NotNull PsiElement element) {
    if (referentFiles == null) return null;

    GlobalSearchScope referencesScope = GlobalSearchScope.filesWithoutLibrariesScope(myProject, referentFiles);

    GlobalSearchScope knownDirtyScope = myDirtyScopeHolder.getDirtyScope();
    GlobalSearchScope wholeClearScope = notScope(knownDirtyScope);
    GlobalSearchScope knownCleanScope = getScopeRestrictedByFileTypes(wholeClearScope, myFileTypes.toArray(FileType.EMPTY_ARRAY));
    GlobalSearchScope wholeDirtyScope = notScope(knownCleanScope);
    GlobalSearchScope mayContainReferencesScope = referencesScope.uniteWith(wholeDirtyScope);
    return scopeWithLibraryIfNeeded(myProject, myProjectFileIndex, mayContainReferencesScope, element);
  }

  @NotNull
  public static GlobalSearchScope scopeWithLibraryIfNeeded(@NotNull Project project,
                                                           @NotNull ProjectFileIndex fileIndex,
                                                           @NotNull GlobalSearchScope baseScope,
                                                           @NotNull PsiElement element) {
    VirtualFile file = PsiUtilCore.getVirtualFile(element);
    if (file == null || !fileIndex.isInLibrary(file)) return baseScope;
    return baseScope.uniteWith(ProjectScope.getLibrariesScope(project));
  }

  @Nullable
  private Set<VirtualFile> getReferentFiles(@NotNull PsiElement element) {
    return getReferentFiles(element, true, (ref, elementPlace) -> myReader.findReferentFileIds(ref, elementPlace == ElementPlace.SRC));
  }

  @TestOnly
  public @Nullable Set<VirtualFile> getReferentFilesForTests(@NotNull PsiElement element) {
    return getReferentFiles(element);
  }

  @TestOnly
  public @Nullable Set<VirtualFile> getReferentFilesForTests(@NotNull CompilerRef compilerRef, boolean checkBaseClassAmbiguity) throws StorageException {
    return myReader.findReferentFileIds(compilerRef, checkBaseClassAmbiguity);
  }

  @TestOnly
  public @Nullable List<@NotNull CompilerRef> getCompilerRefsForTests(@NotNull PsiElement element) throws IOException {
    LanguageCompilerRefAdapter adapter = LanguageCompilerRefAdapter.findAdapter(element, true);
    if (adapter == null) return null;
    return adapter.asCompilerRefs(element, myReader.getNameEnumerator());
  }

  @Nullable
  private Set<VirtualFile> getReferentFileIdsViaImplicitToString(@NotNull PsiElement element) {
    return getReferentFiles(element, false, (ref, elementPlace) -> myReader.findFileIdsWithImplicitToString(ref));
  }

  @Nullable
  private Set<VirtualFile> getReferentFiles(@NotNull PsiElement element,
                                            boolean buildHierarchyForLibraryElements,
                                            @NotNull ReferentFileSearcher referentFileSearcher) {
    final CompilerElementInfo compilerElementInfo = asCompilerElements(element, buildHierarchyForLibraryElements, true);
    if (compilerElementInfo == null) return null;

    if (!myReadDataLock.tryLock()) return null;
    try {
      if (myReader == null) return null;
      Set<VirtualFile> referentFileIds = VfsUtilCore.createCompactVirtualFileSet();
      for (CompilerRef ref : compilerElementInfo.searchElements) {
        try {
          Set<VirtualFile> referents = referentFileSearcher.findReferentFiles(ref, compilerElementInfo.place);
          if (referents == null) {
            return null;
          }
          referentFileIds.addAll(referents);
        }
        catch (StorageException e) {
          throw new RuntimeException(e);
        }
      }
      return referentFileIds;

    }
    finally {
      myReadDataLock.unlock();
    }
  }

  @Nullable
  private CompilerElementInfo asCompilerElements(@NotNull PsiElement psiElement,
                                                 boolean buildHierarchyForLibraryElements,
                                                 boolean checkNotDirty) {
    if (!myReadDataLock.tryLock()) return null;
    try {
      if (myReader == null) return null;
      VirtualFile file = PsiUtilCore.getVirtualFile(psiElement);
      if (file == null) return null;
      ElementPlace place = ElementPlace.get(file, myProjectFileIndex);
      if (checkNotDirty) {
        if (place == null || (place == ElementPlace.SRC && myDirtyScopeHolder.contains(file))) {
          return null;
        }
      }
      final LanguageCompilerRefAdapter adapter = LanguageCompilerRefAdapter.findAdapter(psiElement, true);
      if (adapter == null) return null;
      final List<CompilerRef> refs = adapter.asCompilerRefs(psiElement, myReader.getNameEnumerator());
      if (refs == null) return null;
      if (place == ElementPlace.LIB && buildHierarchyForLibraryElements) {
        return computeInLibraryScope(() -> {
          GlobalSearchScope librariesScope = ProjectScope.getLibrariesScope(myProject);
          List<CompilerRef> resultList = new SmartList<>(refs);
          for (CompilerRef ref : refs) {
            resultList.addAll(adapter.getHierarchyRestrictedToLibraryScope(ref, psiElement, myReader.getNameEnumerator(), librariesScope));
          }

          return new CompilerElementInfo(place, resultList);
        });
      }
      else {
        return new CompilerElementInfo(place, refs);
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    } finally {
      myReadDataLock.unlock();
    }
  }

  @NotNull
  public <T, E extends Throwable> T computeInLibraryScope(ThrowableComputable<T, E> action) throws E {
    myIsInsideLibraryScope.set(true);
    try {
      return action.compute();
    }
    finally {
      myIsInsideLibraryScope.set(false);
    }
  }

  @FunctionalInterface
  public interface CompilerRefProvider {
    @Nullable CompilerRef provide(@NotNull NameEnumerator nameEnumerator) throws IOException;
  }

  public @NotNull SearchId @Nullable [] getDirectInheritorsNames(@NotNull CompilerRefProvider compilerRefFunction) {
    if (!myReadDataLock.tryLock()) return null;
    try {
      if (myReader == null) return null;
      try {
        CompilerRef hierarchyElement = compilerRefFunction.provide(myReader.getNameEnumerator());
        if (hierarchyElement == null) return null;
        return myReader.getDirectInheritorsNames(hierarchyElement);
      }
      catch (RuntimeException | StorageException | IOException e) {
        return onException(e, "direct inheritors names");
      }
    }
    finally {
      myReadDataLock.unlock();
    }
  }

  public boolean isInsideLibraryScope() {
    return myIsInsideLibraryScope.get();
  }

  protected void closeReaderIfNeeded(IndexCloseReason reason) {
    myOpenCloseLock.lock();
    try {
      if (reason == IndexCloseReason.COMPILATION_STARTED) {
        myActiveBuilds++;
        myDirtyScopeHolder.compilerActivityStarted();
      }

      boolean myReaderIsNotNull = myReader != null;
      if (myReaderIsNotNull) {
        myReader.close(reason == IndexCloseReason.AN_EXCEPTION);
        myReader = null;
      }

      LOG.info("backward reference index reader is closed" + (myReaderIsNotNull ? "" : " (didn't exist)"));
    } finally {
      myOpenCloseLock.unlock();
    }
  }

  protected void openReaderIfNeeded(IndexOpenReason reason) {
    // do not run read action inside myOpenCloseLock
    List<Module> compiledModules = null;
    if (reason == IndexOpenReason.COMPILATION_FINISHED) {
      compiledModules = ReadAction.nonBlocking(() -> {
        if (myProject.isDisposed()) {
          return null;
        }
        final ModuleManager moduleManager = ModuleManager.getInstance(myProject);
        return ContainerUtil.map(myDirtyScopeHolder.getCompilationAffectedModules(), moduleManager::findModuleByName);
      }).executeSynchronously();
    }

    myCompilationCount.increment();
    myOpenCloseLock.lock();
    try {
      if (reason == IndexOpenReason.COMPILATION_FINISHED) {
        myActiveBuilds--;
        myDirtyScopeHolder.compilerActivityFinished(compiledModules);
      }
      else if (reason == IndexOpenReason.UP_TO_DATE_CACHE) {
        myDirtyScopeHolder.upToDateCheckFinished(Module.EMPTY_ARRAY);
      }

      if (myActiveBuilds == 0 && myProject.isOpen()) {
        myReader = myReaderFactory.create(myProject);
        LOG.info("backward reference index reader " + (myReader == null ? "doesn't exist" : "is opened"));
      }
    }
    finally {
      myOpenCloseLock.unlock();
    }
  }

  private void markAsOutdated() {
    Module[] modules = ReadAction.compute(() -> myProject.isDisposed()
                                                ? null
                                                : ModuleManager.getInstance(myProject).getModules());
    myOpenCloseLock.lock();
    try {
      if (modules == null) return;
      myDirtyScopeHolder.upToDateCheckFinished(modules);
    } finally {
      myOpenCloseLock.unlock();
    }
  }

  public ProjectFileIndex getFileIndex() {
    return myProjectFileIndex;
  }

  public Set<FileType> getFileTypes() {
    return myFileTypes;
  }

  public Project getProject() {
    return myProject;
  }

  protected static void executeOnBuildThread(@NotNull Runnable compilationFinished) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      compilationFinished.run();
    }
    else {
      BuildManager.getInstance().runCommand(compilationFinished);
    }
  }

  protected enum ElementPlace {
    SRC, LIB;

    public static ElementPlace get(VirtualFile file, ProjectFileIndex index) {
      if (file == null) return null;
      return index.isInSourceContent(file) ? SRC : (index.isInLibrary(file) ? LIB : null);
    }
  }

  @Override
  public long getModificationCount() {
    return myCompilationCount.longValue();
  }

  protected static final class CompilerElementInfo {
    public final ElementPlace place;
    public final @NotNull List<@NotNull CompilerRef> searchElements;

    public CompilerElementInfo(ElementPlace place, @NotNull List<@NotNull CompilerRef> searchElements) {
      this.place = place;
      this.searchElements = searchElements;
    }
  }

  protected static final class HierarchySearchKey {
    private final CompilerHierarchySearchType mySearchType;
    private final FileType mySearchFileType;

    public HierarchySearchKey(CompilerHierarchySearchType searchType, FileType searchFileType) {
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

  // should not be used in production code
  @NotNull
  DirtyScopeHolder getDirtyScopeHolder() {
    return myDirtyScopeHolder;
  }

  @Nullable
  public CompilerReferenceFindUsagesTestInfo getTestFindUsages(@NotNull PsiElement element) {
    if (!myReadDataLock.tryLock()) return null;
    try {
      @Nullable Set<VirtualFile> referentFileIds = getReferentFiles(element);
      DirtyScopeTestInfo dirtyScopeInfo = myDirtyScopeHolder.getState();
      return new CompilerReferenceFindUsagesTestInfo(referentFileIds, dirtyScopeInfo);
    }
    finally {
      myReadDataLock.unlock();
    }
  }

  @Nullable
  public CompilerReferenceHierarchyTestInfo getTestHierarchy(@NotNull PsiNamedElement element, @NotNull GlobalSearchScope scope, @NotNull FileType fileType) {
    if (!myReadDataLock.tryLock()) return null;
    try {
      final CompilerHierarchyInfoImpl hierarchyInfo = getHierarchyInfo(element, scope, fileType, CompilerHierarchySearchType.DIRECT_INHERITOR);
      final DirtyScopeTestInfo dirtyScopeInfo = myDirtyScopeHolder.getState();
      return new CompilerReferenceHierarchyTestInfo(hierarchyInfo, dirtyScopeInfo);
    }
    finally {
      myReadDataLock.unlock();
    }
  }

  @Nullable
  public CompilerReferenceHierarchyTestInfo getTestFunExpressions(@NotNull PsiNamedElement element, @NotNull GlobalSearchScope scope, @NotNull FileType fileType) {
    if (!myReadDataLock.tryLock()) return null;
    try {
      final CompilerHierarchyInfoImpl hierarchyInfo = getHierarchyInfo(element, scope, fileType, CompilerHierarchySearchType.FUNCTIONAL_EXPRESSION);
      final DirtyScopeTestInfo dirtyScopeInfo = myDirtyScopeHolder.getState();
      return new CompilerReferenceHierarchyTestInfo(hierarchyInfo, dirtyScopeInfo);
    }
    finally {
      myReadDataLock.unlock();
    }
  }

  @Override
  public void dispose() { }

  @Nullable
  protected <T> T onException(@NotNull Exception e, @NotNull String actionName) {
    if (e instanceof ControlFlowException) {
      throw (RuntimeException)e;
    }

    LOG.error("an exception during " + actionName + " calculation", e);
    Throwable unwrapped = e instanceof RuntimeException ? e.getCause() : e;
    if (requireIndexRebuild(unwrapped)) {
      closeReaderIfNeeded(IndexCloseReason.AN_EXCEPTION);
    }
    return null;
  }

  @NotNull
  protected static IntSet intersection(@NotNull IntSet set1, @NotNull IntCollection set2) {
    if (set1.isEmpty()) {
      return set1;
    }

    IntSet result = ((IntOpenHashSet)set1).clone();
    result.retainAll(set2);
    return result;
  }

  private static boolean requireIndexRebuild(@Nullable Throwable exception) {
    return exception instanceof StorageException || exception instanceof IOException;
  }

  protected enum IndexCloseReason {
    AN_EXCEPTION,
    COMPILATION_STARTED,
    SHUTDOWN
  }

  protected enum IndexOpenReason {
    COMPILATION_FINISHED,
    UP_TO_DATE_CACHE
  }

  @FunctionalInterface
  protected interface ReferentFileSearcher {
    @Nullable
    Set<VirtualFile> findReferentFiles(@NotNull CompilerRef ref, @NotNull ElementPlace place) throws StorageException;
  }
}
