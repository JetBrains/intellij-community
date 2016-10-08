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
import com.intellij.compiler.server.BuildManagerListener;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchScopeUtil;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import gnu.trove.THashSet;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.psi.search.GlobalSearchScope.*;

public class CompilerReferenceServiceImpl extends CompilerReferenceService implements ModificationTracker {
  private final Set<FileType> myFileTypes;
  private final DirtyModulesHolder myDirtyModulesHolder;
  private final ProjectFileIndex myProjectFileIndex;
  private final LongAdder myCompilationCount = new LongAdder();

  private volatile CompilerReferenceReader myReader;

  private final Object myLock = new Object();

  public CompilerReferenceServiceImpl(Project project) {
    super(project);

    myDirtyModulesHolder = new DirtyModulesHolder();
    myProjectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    myFileTypes = Collections.unmodifiableSet(Stream.of(LanguageLightUsageConverter.INSTANCES)
                                                    .map(LanguageLightUsageConverter::getFileSourceType)
                                                    .collect(Collectors.toSet()));
  }

  @Override
  public void projectOpened() {
    if (isEnabled()) {
      myDirtyModulesHolder.markAsDirty(ModuleManager.getInstance(myProject).getModules());

      myProject.getMessageBus().connect(myProject).subscribe(BuildManagerListener.TOPIC, new BuildManagerListener() {
        @Override
        public void buildStarted(Project project, UUID sessionId, boolean isAutomake) {
          myDirtyModulesHolder.compilationPhaseStarted();
          closeReaderIfNeed();
        }
      });

      CompilerManager.getInstance(myProject).addCompilationStatusListener(new CompilationStatusListener() {
        @Override
        public void compilationFinished(boolean aborted, int errors, int warnings, CompileContext compileContext) {
          compilationFinished(errors, compileContext);
        }

        @Override
        public void automakeCompilationFinished(int errors, int warnings, CompileContext compileContext) {
          compilationFinished(errors, compileContext);
        }

        private void compilationFinished(int errors, CompileContext context) {
          final Module[] compilationModules = context.getCompileScope().getAffectedModules();
          final Set<Module> modulesWithErrors;
          if (errors != 0) {
            modulesWithErrors = Stream
              .of(context.getMessages(CompilerMessageCategory.ERROR))
              .map(CompilerMessage::getVirtualFile)
              .distinct()
              .map(myProjectFileIndex::getModuleForFile)
              .collect(Collectors.toSet());
          }
          else {
            modulesWithErrors = Collections.emptySet();
          }
          myCompilationCount.increment();
          myDirtyModulesHolder.compilationPhaseFinished(compilationModules, modulesWithErrors);
          openReaderIfNeed();
        }
      });

      VirtualFileManager.getInstance().addVirtualFileListener(new VirtualFileAdapter() {
        @Override
        public void fileCreated(@NotNull VirtualFileEvent event) {
          processChange(event.getFile());
        }

        @Override
        public void fileCopied(@NotNull VirtualFileCopyEvent event) {
          processChange(event.getFile());
        }

        @Override
        public void fileMoved(@NotNull VirtualFileMoveEvent event) {
          processChange(event.getFile());
        }

        @Override
        public void beforePropertyChange(@NotNull VirtualFilePropertyEvent event) {
          if (VirtualFile.PROP_NAME.equals(event.getPropertyName()) || VirtualFile.PROP_SYMLINK_TARGET.equals(event.getPropertyName())) {
            processChange(event.getFile());
          }
        }

        @Override
        public void beforeContentsChange(@NotNull VirtualFileEvent event) {
          processChange(event.getFile());
        }

        @Override
        public void beforeFileDeletion(@NotNull VirtualFileEvent event) {
          processChange(event.getFile());
        }

        @Override
        public void beforeFileMovement(@NotNull VirtualFileMoveEvent event) {
          processChange(event.getFile());
        }

        private void processChange(VirtualFile file) {
          myDirtyModulesHolder.fileChanged(file);
        }
      }, myProject);
    }

  }

  @Override
  public void projectClosed() {
    closeReaderIfNeed();
  }

  @Nullable
  @Override
  public GlobalSearchScope getScopeWithoutCodeReferences(@NotNull PsiElement element, @NotNull CompilerSearchAdapter adapter) {
    if (!isServiceEnabled()) return null;

    return CachedValuesManager.getCachedValue(element,
                                              () -> CachedValueProvider.Result.create(new ConcurrentFactoryMap<CompilerSearchAdapter, GlobalSearchScope>() {
                                                  @Nullable
                                                  @Override
                                                  protected GlobalSearchScope create(CompilerSearchAdapter key) {
                                                    return calculateScopeWithoutReferences(element, key);
                                                  }
                                              },
                                              PsiModificationTracker.MODIFICATION_COUNT)).get(adapter);
  }

  @Nullable
  @Override
  public <T extends PsiNamedElement> CompilerDirectInheritorInfo<T> getDirectInheritors(@NotNull PsiNamedElement aClass,
                                                                                        @NotNull GlobalSearchScope useScope,
                                                                                        @NotNull GlobalSearchScope searchScope,
                                                                                        @NotNull CompilerSearchAdapter compilerSearchAdapter,
                                                                                        @NotNull CompilerDirectInheritorSearchAdapter<T> inheritorSearchAdapter,
                                                                                        @NotNull FileType... searchFileTypes) {
    if (!isServiceEnabled()) return null;

    //TODO should be available to search inheritors of lib classes
    final VirtualFile file = aClass.getContainingFile().getVirtualFile();
    if (!myProjectFileIndex.isInSourceContent(file)) return null;

    final CompilerElement compilerElement = compilerSearchAdapter.asCompilerElement(aClass);
    if (compilerElement == null) return null;

    final GlobalSearchScope dirtyScope = myDirtyModulesHolder.getDirtyScope();
    final Couple<Map<VirtualFile, T[]>> directInheritorsAndCandidates = myReader.getDirectInheritors(compilerElement,
                                                                                                     aClass,
                                                                                                     inheritorSearchAdapter,
                                                                                                     useScope,
                                                                                                     dirtyScope,
                                                                                                     myProject,
                                                                                                     searchFileTypes);


    return new CompilerDirectInheritorInfo<>(selectClassesInScope(directInheritorsAndCandidates.getFirst(), searchScope),
                                             selectClassesInScope(directInheritorsAndCandidates.getSecond(), searchScope),
                                             dirtyScope);
  }

  private boolean isServiceEnabled() {
    return myReader != null && isEnabled();
  }

  @Nullable
  private GlobalSearchScope calculateScopeWithoutReferences(@NotNull PsiElement element, CompilerSearchAdapter adapter) {
    TIntHashSet referentFileIds = getReferentFileIds(element, adapter);
    if (referentFileIds == null) return null;

    return getScopeRestrictedByFileTypes(new ScopeWithoutReferencesOnCompilation(referentFileIds).intersectWith(notScope(myDirtyModulesHolder.getDirtyScope())),
                                         myFileTypes.toArray(new FileType[myFileTypes.size()]));
  }

  @Nullable
  private TIntHashSet getReferentFileIds(@NotNull PsiElement element, @NotNull CompilerSearchAdapter adapter) {
    final PsiFile file = element.getContainingFile();
    if (file == null) return null;
    final VirtualFile vFile = file.getVirtualFile();
    if (vFile == null) return null;

    ElementPlace place = ElementPlace.get(vFile, myProjectFileIndex);
    if (place == null) {
      return null;
    }

    if (myDirtyModulesHolder.contains(vFile)) {
      return null;
    }
    CompilerElement[] compilerElements;
    if (place == ElementPlace.SRC) {
      final CompilerElement compilerElement = adapter.asCompilerElement(element);
      compilerElements = compilerElement == null ? CompilerElement.EMPTY_ARRAY : new CompilerElement[]{compilerElement};
    }
    else {
      compilerElements = adapter.libraryElementAsCompilerElements(element);
    }
    if (compilerElements.length == 0) return null;

    synchronized (myLock) {
      if (myReader == null) return null;
      TIntHashSet referentFileIds = new TIntHashSet();
      for (CompilerElement compilerElement : compilerElements) {
        final TIntHashSet referents = myReader.findReferentFileIds(compilerElement, adapter, place == ElementPlace.SRC);
        if (referents == null) return null;
        referentFileIds.addAll(referents.toArray());
      }
      return referentFileIds;
    }
  }

  private void closeReaderIfNeed() {
    synchronized (myLock) {
      if (myReader != null) {
        myReader.close();
        myReader = null;
      }
    }
  }

  private void openReaderIfNeed() {
    synchronized (myLock) {
      if (myProject.isOpen()) {
        myReader = CompilerReferenceReader.create(myProject);
      }
    }
  }

  private static <T extends PsiNamedElement> List<T> selectClassesInScope(Map<VirtualFile, T[]> classesPerFile, GlobalSearchScope searchScope) {
    return classesPerFile
      .entrySet()
      .stream()
      .filter(e -> searchScope.contains(e.getKey()))
      .flatMap(e -> Stream.of(e.getValue()))
      .collect(Collectors.toList());
  }

  @TestOnly
  @Nullable
  public Set<VirtualFile> getReferentFiles(@NotNull PsiElement element, @NotNull CompilerSearchAdapter adapter) {
    FileBasedIndex fileIndex = FileBasedIndex.getInstance();
    final TIntHashSet ids = getReferentFileIds(element, adapter);
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

  private enum ElementPlace {
    SRC, LIB;

    private static ElementPlace get(VirtualFile file, ProjectFileIndex index) {
      return index.isInSourceContent(file) ? SRC :
             ((index.isInLibrarySource(file) || index.isInLibraryClasses(file)) ? LIB : null);
    }
  }

  private static class ScopeWithoutReferencesOnCompilation extends GlobalSearchScope {
    private final TIntHashSet myReferentIds;

    private ScopeWithoutReferencesOnCompilation(TIntHashSet ids) {
      myReferentIds = ids;
    }

    @Override
    public boolean contains(@NotNull VirtualFile file) {
      return !(file instanceof VirtualFileWithId) || !myReferentIds.contains(((VirtualFileWithId)file).getId());
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

  private class DirtyModulesHolder extends UserDataHolderBase {
    private final Set<Module> myChangedModules = ContainerUtil.newHashSet();
    private final Set<Module> myChangedModulesDuringCompilation = ContainerUtil.newHashSet();
    private boolean myCompilationPhase;

    private final Object myLock = new Object();

    private void compilationPhaseStarted() {
      synchronized (myLock) {
        myCompilationPhase = true;
      }
    }

    private void compilationPhaseFinished(Module[] compilationModules, Set<Module> modulesWithErrors) {
      synchronized (myLock) {
        myCompilationPhase = false;

        ContainerUtil.removeAll(myChangedModules, compilationModules);
        myChangedModules.addAll(modulesWithErrors);
        myChangedModules.addAll(ContainerUtil.newHashSet(myChangedModulesDuringCompilation));
        myChangedModulesDuringCompilation.clear();
      }
    }

    private GlobalSearchScope getDirtyScope() {
      return CachedValuesManager.getManager(myProject).getCachedValue(this, () -> {
        synchronized (myLock) {
          final GlobalSearchScope dirtyScope =
            myChangedModules.stream().map(Module::getModuleWithDependentsScope).reduce(EMPTY_SCOPE, (s1, s2) -> s1.union(s2));
          return CachedValueProvider.Result.create(dirtyScope, PsiModificationTracker.MODIFICATION_COUNT, CompilerReferenceServiceImpl.this);
        }
      });
    }

    private void fileChanged(VirtualFile file) {
      if (myProjectFileIndex.isInSourceContent(file) && myFileTypes.contains(file.getFileType())) {
        final Module module = myProjectFileIndex.getModuleForFile(file);
        if (module != null) {
          synchronized (myLock) {
            if (myCompilationPhase) {
              myChangedModulesDuringCompilation.add(module);
            } else {
              myChangedModules.add(module);
            }
          }
        }
      }
    }

    private boolean contains(VirtualFile file) {
      return getDirtyScope().contains(file);
    }

    private void markAsDirty(Module[] modules) {
      //TODO delete; the service should be available on IDE restart
      synchronized (myLock) {
        Collections.addAll(myChangedModules, modules);
      }
    }
  }
}
