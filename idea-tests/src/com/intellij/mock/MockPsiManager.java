package com.intellij.mock;

import com.intellij.ide.startup.CacheUpdater;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.*;
import com.intellij.psi.impl.cache.CacheManager;
import com.intellij.psi.impl.cache.RepositoryManager;
import com.intellij.psi.impl.cache.impl.CompositeCacheManager;
import com.intellij.psi.impl.file.impl.FileManager;
import com.intellij.psi.impl.search.PsiSearchHelperImpl;
import com.intellij.psi.impl.source.*;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.RepositoryTreeElement;
import com.intellij.psi.impl.source.tree.java.PsiAnnotationImpl;
import com.intellij.psi.impl.source.tree.java.PsiTypeParameterExtendsBoundsListImpl;
import com.intellij.psi.impl.source.tree.java.PsiTypeParameterImpl;
import com.intellij.psi.impl.source.tree.java.PsiTypeParameterListImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.IndexPattern;
import com.intellij.psi.search.IndexPatternProvider;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.ThrowableRunnable;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MockPsiManager extends PsiManagerEx {
  private static final Logger LOG = Logger.getInstance("#com.intellij.mock.MockPsiManager");
  
  private final Project myProject;
  private final Map<VirtualFile,PsiDirectory> myDirectories = new THashMap<VirtualFile, PsiDirectory>();
  private final Map<VirtualFile,PsiFile> myFiles = new THashMap<VirtualFile, PsiFile>();
  private CachedValuesManagerImpl myCachedValuesManager;
  private MockFileManager myMockFileManager;
  private PsiModificationTrackerImpl myPsiModificationTracker;
  private final CompositeCacheManager myCompositeCacheManager = new CompositeCacheManager();
  private ResolveCache myResolveCache;

  public MockPsiManager() {
    this(null);
  }

  public MockPsiManager(final Project project) {
    myProject = project;
  }

  public void addPsiDirectory(VirtualFile file, PsiDirectory psiDirectory) {
    myDirectories.put(file, psiDirectory);
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  public PsiFile findFile(@NotNull VirtualFile file) {
    return myFiles.get(file);
  }
  
  public
  @Nullable
  FileViewProvider findViewProvider(@NotNull VirtualFile file) {
    return null;
  }

  public PsiDirectory findDirectory(@NotNull VirtualFile file) {
    return myDirectories.get(file);
  }

  public boolean areElementsEquivalent(PsiElement element1, PsiElement element2) {
    return Comparing.equal(element1, element2);
  }

  public void reloadFromDisk(@NotNull PsiFile file) {
  }

  public void addPsiTreeChangeListener(@NotNull PsiTreeChangeListener listener) {
  }

  public void addPsiTreeChangeListener(@NotNull PsiTreeChangeListener listener, Disposable parentDisposable) {
  }

  public void removePsiTreeChangeListener(@NotNull PsiTreeChangeListener listener) {
  }

  @NotNull
  public CodeStyleManager getCodeStyleManager() {
    return CodeStyleManager.getInstance(myProject);
  }

  @NotNull
  public PsiSearchHelper getSearchHelper() {
    return new PsiSearchHelperImpl(this);
  }

  public RepositoryElementsManager getRepositoryElementsManager() {
    return new EmptyRepositoryElementsManager() {
      public SrcRepositoryPsiElement createRepositoryPsiElementByTreeElement(PsiManagerEx manager,
                                                                              RepositoryTreeElement treeElement) {
        IElementType elementType = treeElement.getElementType();
        if (elementType == JavaElementType.CLASS) {
          return new PsiClassImpl(manager, treeElement);
        }
        else if (elementType == JavaElementType.ANONYMOUS_CLASS) {
          return new PsiAnonymousClassImpl(manager, treeElement);
        }
        else if (elementType == JavaElementType.ENUM_CONSTANT_INITIALIZER) {
          return new PsiEnumConstantInitializerImpl(manager, treeElement);
        }
        else if (elementType == JavaElementType.METHOD) {
          return new PsiMethodImpl(manager, treeElement);
        }
        else if (elementType == JavaElementType.ANNOTATION_METHOD) {
          return new PsiAnnotationMethodImpl(manager, treeElement);
        }
        else if (elementType == JavaElementType.FIELD) {
          return new PsiFieldImpl(manager, treeElement);
        }
        else if (elementType == JavaElementType.ENUM_CONSTANT) {
          return new PsiEnumConstantImpl(manager, treeElement);
        }
        else if (elementType == JavaElementType.CLASS_INITIALIZER) {
          return new PsiClassInitializerImpl(manager, treeElement);
        }
        else if (elementType == JavaElementType.MODIFIER_LIST) {
          return new PsiModifierListImpl(manager, treeElement);
        }
        else if (elementType == JavaElementType.IMPORT_LIST) {
          return new PsiImportListImpl(manager, treeElement);
        }
        else if (elementType == JavaElementType.EXTENDS_LIST ||
                 elementType == JavaElementType.IMPLEMENTS_LIST ||
                 elementType == JavaElementType.THROWS_LIST) {
          return new PsiReferenceListImpl(manager, treeElement);
        }
        else if (elementType == JavaElementType.PARAMETER_LIST) {
          return new PsiParameterListImpl(manager, treeElement);
        }
        else if (elementType == JavaElementType.IMPORT_STATEMENT) {
          return new PsiImportStatementImpl(manager, treeElement);
        }
        else if (elementType == JavaElementType.IMPORT_STATIC_STATEMENT) {
          return new PsiImportStaticStatementImpl(manager, treeElement);
        }
        else if (elementType == JavaElementType.PARAMETER) {
          return new PsiParameterImpl(manager, treeElement);
        }
        else if (elementType == JavaElementType.TYPE_PARAMETER) {
          return new PsiTypeParameterImpl(manager, treeElement);
        }
        else if (elementType == JavaElementType.TYPE_PARAMETER_LIST) {
          return new PsiTypeParameterListImpl(manager, treeElement);
        }
        else if (elementType == JavaElementType.EXTENDS_BOUND_LIST) {
          return new PsiTypeParameterExtendsBoundsListImpl(manager, treeElement);
        }
        else if (elementType == JavaElementType.ANNOTATION) {
          return new PsiAnnotationImpl(manager, treeElement);
        }
        else {
          LOG.error("Incorrect TreeElement:" + treeElement);
          return null;
        }
      }
    };

  }

  public RepositoryManager getRepositoryManager() {
    return new EmptyRepositoryManagerImpl();
  }

  @NotNull
  public PsiModificationTracker getModificationTracker() {
    if (myPsiModificationTracker == null) {
      myPsiModificationTracker = new PsiModificationTrackerImpl();
    }
    return myPsiModificationTracker;
  }

  @NotNull
  public CachedValuesManager getCachedValuesManager() {
    if (myCachedValuesManager == null) {
      myCachedValuesManager = new CachedValuesManagerImpl(this);
    }
    return myCachedValuesManager;
  }

  public void moveFile(@NotNull PsiFile file, @NotNull PsiDirectory newParentDir) throws IncorrectOperationException {
  }

  public void moveDirectory(@NotNull PsiDirectory dir, @NotNull PsiDirectory newParentDir) throws IncorrectOperationException {
  }

  public void checkMove(@NotNull PsiElement element, @NotNull PsiElement newContainer) throws IncorrectOperationException {
  }

  public void startBatchFilesProcessingMode() {
  }

  public void finishBatchFilesProcessingMode() {
  }

  public <T> T getUserData(Key<T> key) {
    return null;
  }

  public <T> void putUserData(Key<T> key, T value) {
  }

  public boolean isDisposed() {
    return false;
  }


  public void dropResolveCaches() {
  }

  public boolean isInProject(@NotNull PsiElement element) {
    return false;
  }

  public void performActionWithFormatterDisabled(Runnable r) {
    r.run();
  }

  public <T extends Throwable> void performActionWithFormatterDisabled(ThrowableRunnable<T> r) throws T {
    r.run();
  }

  public <T> T performActionWithFormatterDisabled(Computable<T> r) {
    return r.compute();
  }

  public void registerLanguageInjector(@NotNull LanguageInjector injector) {
  }

  public void registerLanguageInjector(@NotNull LanguageInjector injector, Disposable parentDisposable) {
  }

  public void unregisterLanguageInjector(@NotNull LanguageInjector injector) {

  }

  public void postponeAutoFormattingInside(Runnable runnable) {
    PostprocessReformattingAspect.getInstance(getProject()).postponeFormattingInside(runnable);
  }

  @NotNull
  public List<LanguageInjector> getLanguageInjectors() {
    return Collections.emptyList();
  }

  public boolean isBatchFilesProcessingMode() {
    return false;
  }

  public boolean isAssertOnFileLoading(VirtualFile file) {
    return false;
  }

  public void nonPhysicalChange() {
    throw new UnsupportedOperationException("Method nonPhysicalChange is not yet implemented in " + getClass().getName());
  }

  public void physicalChange() {
    throw new UnsupportedOperationException("physicalChange is not implemented"); // TODO
  }

  public ResolveCache getResolveCache() {
    if (myResolveCache == null) {
      myResolveCache = new ResolveCache(this);
    }
    return myResolveCache;
  }

  public void registerRunnableToRunOnChange(Runnable runnable) {
  }

  public void registerWeakRunnableToRunOnChange(Runnable runnable) {
  }

  public void registerRunnableToRunOnAnyChange(Runnable runnable) {
  }

  public void registerRunnableToRunAfterAnyChange(Runnable runnable) {
    throw new UnsupportedOperationException("Method registerRunnableToRunAfterAnyChange is not yet implemented in " + getClass().getName());
  }

  public FileManager getFileManager() {
    if (myMockFileManager == null) {
      myMockFileManager = new MockFileManager(this);
    }
    return myMockFileManager;
  }

  public void invalidateFile(final PsiFile file) {
  }

  public void beforeChildRemoval(final PsiTreeChangeEventImpl event) {
  }

  public void addFileToCacheManager(final PsiFile file) {
    myFiles.put(file.getViewProvider().getVirtualFile(), file);
    myCompositeCacheManager.addCacheManager(new CacheManager() {
      public void initialize() {
      }

      public void dispose() {
      }

      @NotNull
      public CacheUpdater[] getCacheUpdaters() {
        return new CacheUpdater[0];
      }

      @NotNull
      public PsiFile[] getFilesWithWord(@NotNull final String word, final short occurenceMask, @NotNull final GlobalSearchScope scope,
                                        final boolean caseSensitively) {
        return new PsiFile[]{file};
      }

      public boolean processFilesWithWord(@NotNull final Processor<PsiFile> processor, @NotNull final String word, final short occurenceMask,
                                          @NotNull final GlobalSearchScope scope, final boolean caseSensitively) {
        return processor.process(file);
      }

      @NotNull
      public PsiFile[] getFilesWithTodoItems() {
        return PsiFile.EMPTY_ARRAY;
      }

      public int getTodoCount(@NotNull final VirtualFile file, final IndexPatternProvider patternProvider) {
        return 0;
      }

      public int getTodoCount(@NotNull final VirtualFile file, final IndexPattern pattern) {
        return 0;
      }

      public void addOrInvalidateFile(@NotNull final VirtualFile file) {
      }
    });
  }

  public CacheManager getCacheManager() {
    return myCompositeCacheManager;
  }
}