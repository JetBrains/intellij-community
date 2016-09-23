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

import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileTask;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import gnu.trove.THashSet;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.jps.backwardRefs.CompilerElement;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CompilerReferenceServiceImpl extends CompilerReferenceService {
  private static final CompilerReferenceConverter[] BYTECODE_CONVERTERS =
    new CompilerReferenceConverter[]{new JavaCompilerReferenceConverter()};

  private final ProjectFileIndex myProjectFileIndex;
  private final Set<Module> myChangedModules = ContainerUtil.newConcurrentSet();
  private final Set<FileType> myFileTypes;

  private volatile CompilerReferenceReader myReader;

  private final Object myLock = new Object();

  public CompilerReferenceServiceImpl(Project project) {
    super(project);
    myProjectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    myFileTypes = Collections.unmodifiableSet(
      Stream.of(BYTECODE_CONVERTERS).map(CompilerReferenceConverter::getAvailabilitySrcFileType).collect(Collectors.toSet()));
  }

  @Override
  public void projectOpened() {
    if (isEnabled()) {
      CompilerManager.getInstance(myProject).addBeforeTask(new CompileTask() {
        @Override
        public boolean execute(CompileContext context) {
          closeReaderIfNeed();
          return true;
        }
      });
      CompilerManager.getInstance(myProject).addAfterTask(new CompileTask() {
        @Override
        public boolean execute(CompileContext context) {
          myChangedModules.clear();
          openReaderIfNeed();
          return true;
        }
      });
    }

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
        if (myReader != null && myProjectFileIndex.isInSourceContent(file) && myFileTypes.contains(file.getFileType())) {
          final Module module = myProjectFileIndex.getModuleForFile(file);
          if (module != null) {
            myChangedModules.add(module);
          }
        }
      }
    }, myProject);
  }

  @Override
  public void projectClosed() {
    closeReaderIfNeed();
  }

  @Override
  public GlobalSearchScope getScopeWithoutReferences(@NotNull PsiElement element) {
    if (!isServiceEnabled()) return null;
    return CachedValuesManager.getCachedValue(element, () -> CachedValueProvider.Result.create(calculateScopeWithoutReferences(element),
                                                                                               PsiModificationTracker.MODIFICATION_COUNT));
  }

  private boolean isServiceEnabled() {
    return myReader != null && isEnabled();
  }

  @Nullable
  private GlobalSearchScope calculateScopeWithoutReferences(@NotNull PsiElement element) {
    TIntHashSet referentFileIds = getReferentFileIds(element);
    if (referentFileIds == null) return null;

    final GlobalSearchScope everythingIsClearScope = GlobalSearchScope
      .getScopeRestrictedByFileTypes(ProjectScope.getContentScope(myProject), myFileTypes.toArray(new FileType[myFileTypes.size()]));
    return new ScopeWithoutBytecodeReferences(referentFileIds).intersectWith(everythingIsClearScope);
  }

  @Nullable
  private TIntHashSet getReferentFileIds(@NotNull PsiElement element) {
    final PsiFile file = element.getContainingFile();
    if (file == null) return null;
    final VirtualFile vFile = file.getVirtualFile();
    if (vFile == null) return null;

    ElementPlace place = ElementPlace.get(vFile, myProjectFileIndex);
    if (place == null) {
      return null;
    }
    if (!myChangedModules.isEmpty()) {
      final Module module = myProjectFileIndex.getModuleForFile(vFile);
      if (module == null || areDependenciesOrSelfChanged(module, new THashSet<>())) {
        return null;
      }
    }
    final FileType type = vFile.getFileType();
    CompilerElement[] compilerElements = null;
    if (place == ElementPlace.SRC) {
      for (CompilerReferenceConverter converter : BYTECODE_CONVERTERS) {
        if (converter.getAvailabilitySrcFileType().equals(type)) {
          final CompilerElement compilerElement = converter.sourceElementAsCompilerElement(element);
          compilerElements = compilerElement == null ? CompilerElement.EMPTY_ARRAY : new CompilerElement[]{compilerElement};
          break;
        }
      }
    }
    else {
      for (CompilerReferenceConverter converter : BYTECODE_CONVERTERS) {
        compilerElements = converter.libraryElementAsCompilerElements(element);
        if (compilerElements.length != 0) {
          break;
        }
      }
    }
    if (compilerElements == null || compilerElements.length == 0) return null;

    synchronized (myLock) {
      if (myReader == null) return null;
      TIntHashSet referentFileIds = new TIntHashSet();
      for (CompilerElement compilerElement : compilerElements) {
        referentFileIds.addAll(myReader.findReferentFileIds(compilerElement).toArray());
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

  private enum ElementPlace {
    SRC, LIB;

    private static ElementPlace get(VirtualFile file, ProjectFileIndex index) {
      return index.isInSourceContent(file) ? SRC :
             ((index.isInLibrarySource(file) || index.isInLibraryClasses(file)) ? LIB : null);
    }
  }

  private static class ScopeWithoutBytecodeReferences extends GlobalSearchScope {
    private final TIntHashSet myReferentIds;

    private ScopeWithoutBytecodeReferences(TIntHashSet ids) {
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

  private boolean areDependenciesOrSelfChanged(Module module, THashSet<Module> uniqueModules) {
    if (!uniqueModules.add(module)) {
      return false;
    }
    if (myChangedModules.contains(module)) {
      return true;
    }
    for (Module dependency : ModuleRootManager.getInstance(module).getDependencies()) {
      if (areDependenciesOrSelfChanged(dependency, uniqueModules)) {
        return true;
      }
    }
    return false;
  }
}
