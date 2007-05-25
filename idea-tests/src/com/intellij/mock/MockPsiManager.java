package com.intellij.mock;

import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.*;
import com.intellij.psi.impl.cache.RepositoryManager;
import com.intellij.psi.impl.file.impl.FileManager;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.impl.source.resolve.ResolveUtil;
import com.intellij.psi.javadoc.JavadocManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.testFramework.LiteFixture;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MockPsiManager extends PsiManagerEx {
  private final List<PsiClass> myClasses = new SmartList<PsiClass>();
  private final List<PsiPackage> myPackages = new SmartList<PsiPackage>();
  private Project myProject;
  private ResolveCache myResolveCache;
  private PsiElementFactory myElementFactory;
  private final Map<VirtualFile,PsiDirectory> myDirectories = new THashMap<VirtualFile, PsiDirectory>();
  private CachedValuesManagerImpl myCachedValuesManager;
  private MockFileManager myMockFileManager;
  private PsiModificationTrackerImpl myPsiModificationTracker;

  public MockPsiManager() {
    this(null);
  }

  public MockPsiManager(final Project project) {
    myProject = project;
  }

  public void addPsiDirectory(VirtualFile file, PsiDirectory psiDirectory) {
    myDirectories.put(file, psiDirectory);
  }

  public Project getProject() {
    return myProject;
  }

  public PsiDirectory[] getRootDirectories(int rootType) {
    return PsiDirectory.EMPTY_ARRAY;
  }

  public OrderEntry findOrderEntry(PsiElement element) {
    return null;  //To change body of implemented methods use Options | File Templates.
  }

  public PsiFile findFile(VirtualFile file) {
    return null;
  }

  public
  @Nullable
  FileViewProvider findViewProvider(@NotNull VirtualFile file) {
    return null;
  }

  public @Nullable PsiFile findFile(@NotNull VirtualFile file, @NotNull Language aspect) {
    return null;
  }

  public @NotNull Language[] getKnownAspects(@NotNull VirtualFile file) {
    return new Language[0];
  }

  public PsiDirectory findDirectory(VirtualFile file) {
    return myDirectories.get(file);
  }

  public PsiClass findClass(@NotNull final String qualifiedName) {
    return ContainerUtil.find(myClasses, new Condition<PsiClass>() {
      public boolean value(final PsiClass psiClass) {
        return psiClass.getQualifiedName().equals(qualifiedName);
      }
    });
  }

  public <T extends PsiClass> T addClass(T psiClass) {
    final String qName = psiClass.getQualifiedName();
    final PsiClass existing = findClass(qName);
    if (existing != null) myClasses.remove(existing);
    myClasses.add(psiClass);
    return addToPackage(qName, psiClass);

  }

  public MockPsiPackage addPackage(String qName) {
    return addPackage(addToPackage(qName, new MockPsiPackage(qName)));
  }

  private <T extends PsiElement> T addToPackage(final String qName, final T declaration) {
    if (qName == null) return declaration;

    String superName = StringUtil.getPackageName(qName);
    PsiPackage superPackage = findPackage(superName);
    if (superPackage == null && StringUtil.isNotEmpty(qName)) {
      superPackage = addPackage(superName);
    }

    if (superPackage instanceof MockPsiPackage) {
      ((MockPsiPackage)superPackage).addDeclaration(declaration);
    }
    if (declaration instanceof MockPsiElement) {
      ((MockPsiElement)declaration).setParent(superPackage);
    }

    final PsiFile file = declaration.getContainingFile();
    if (file != null) {
      LiteFixture.setContext(file, superPackage);
    }
    return declaration;
  }

  public <T extends PsiPackage> T addPackage(T psiPackage) {
    myPackages.add(psiPackage);
    return psiPackage;
  }

  public PsiClass findClass(String qualifiedName, GlobalSearchScope scope) {
    return findClass(qualifiedName);
  }

  public PsiClass[] findClasses(String qualifiedName, GlobalSearchScope scope) {
    return PsiClass.EMPTY_ARRAY;
  }

  public PsiPackage findPackage(final String qualifiedName) {
    return ContainerUtil.find(myPackages, new Condition<PsiPackage>() {
      public boolean value(final PsiPackage psiPackage) {
        return psiPackage.getQualifiedName().equals(qualifiedName);
      }
    });
  }

  public boolean areElementsEquivalent(PsiElement element1, PsiElement element2) {
    return Comparing.equal(element1, element2);
  }

  public LanguageLevel getEffectiveLanguageLevel() {
    return LanguageLevel.HIGHEST;
  }

  public boolean isPartOfPackagePrefix(String packageName) {
    return false;
  }


  public PsiMigration startMigration() {
    return null;
  }

  public void commit(PsiFile file) {
  }

  public void commitAll() {
  }

  public PsiFile[] getAllFilesToCommit() {
    return PsiFile.EMPTY_ARRAY;
  }

  public void reloadFromDisk(PsiFile file) {
  }

  public void addPsiTreeChangeListener(PsiTreeChangeListener listener) {
  }

  public void addPsiTreeChangeListener(@NotNull PsiTreeChangeListener listener, Disposable parentDisposable) {
  }

  public void removePsiTreeChangeListener(PsiTreeChangeListener listener) {
  }

  public CodeStyleManager getCodeStyleManager() {
    return null;
  }

  @NotNull
  public PsiElementFactory getElementFactory() {
    if (myElementFactory == null) {
      myElementFactory = new MockPsiElementFactory(this);
    }
    return myElementFactory;
  }

  public void setElementFactory(final PsiElementFactory elementFactory) {
    myElementFactory = elementFactory;
  }

  @NotNull
  public PsiJavaParserFacade getParserFacade() {
    return getElementFactory();
  }

  public PsiSearchHelper getSearchHelper() {
    return null;
  }

  public PsiResolveHelper getResolveHelper() {
    return new MockResolveHelper(this) {
      public boolean isAccessible(final PsiMember member, final PsiModifierList modifierList, final PsiElement place,
                                  final PsiClass accessObjectClass, final PsiElement currentFileResolveScope) {
        return true;
      }

      public boolean isAccessible(final PsiMember member, final PsiElement place, final PsiClass accessObjectClass) {
        return true;
      }
    };
  }

  public PsiShortNamesCache getShortNamesCache() {
    return null;
  }

  public void registerShortNamesCache(PsiShortNamesCache cache) {
  }

  public JavadocManager getJavadocManager() {
    return null;
  }

  public PsiNameHelper getNameHelper() {
    return new PsiNameHelperImpl(this);
  }

  //public PsiConstantEvaluationHelper getConstantEvaluationHelper() {
  //  return new PsiConstantEvaluationHelperImpl();
  //}

  public PsiModificationTracker getModificationTracker() {
    if (myPsiModificationTracker == null) {
      myPsiModificationTracker = new PsiModificationTrackerImpl(this);
    }
    return myPsiModificationTracker;
  }

  public CachedValuesManager getCachedValuesManager() {
    if (myCachedValuesManager == null) {
      myCachedValuesManager = new CachedValuesManagerImpl(this);
    }
    return myCachedValuesManager;
  }

  public void moveFile(PsiFile file, PsiDirectory newParentDir) throws IncorrectOperationException {
  }

  public void moveDirectory(PsiDirectory dir, PsiDirectory newParentDir) throws IncorrectOperationException {
  }

  public void checkMove(PsiElement element, PsiElement newContainer) throws IncorrectOperationException {
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


  public void setEffectiveLanguageLevel(LanguageLevel languageLevel) {
  }

  public void dropResolveCaches() {
  }

  public boolean isInPackage(PsiElement element, PsiPackage aPackage) {
    return false;
  }

  public boolean arePackagesTheSame(PsiElement element1, PsiElement element2) {
    PsiFile file1 = ResolveUtil.getContextFile(element1);
    PsiFile file2 = ResolveUtil.getContextFile(element2);
    return Comparing.equal(file1, file2);
  }

  public boolean isInProject(PsiElement element) {
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

  public void registerLanguageInjector(LanguageInjector injector) {
  }

  public void registerLanguageInjector(@NotNull LanguageInjector injector, Disposable parentDisposable) {
  }

  public void unregisterLanguageInjector(@NotNull LanguageInjector injector) {

  }

  public ElementManipulatorsRegistry getElementManipulatorsRegistry() {
    return null;
  }

  public void postponeAutoFormattingInside(Runnable runnable) {
    PostprocessReformattingAspect.getInstance(getProject()).postponeFormattingInside(runnable);
  }

  public List<LanguageInjector> getLanguageInjectors() {
    return Collections.emptyList();
  }

  public PsiConstantEvaluationHelper getConstantEvaluationHelper() {
    return null;
  }

  public boolean isBatchFilesProcessingMode() {
    return false;
  }

  public RepositoryManager getRepositoryManager() {
    return new MockRepositoryManager();
  }

  public RepositoryElementsManager getRepositoryElementsManager() {
    throw new UnsupportedOperationException("Method getRepositoryElementsManager is not yet implemented in " + getClass().getName());
  }

  public boolean isAssertOnFileLoading(VirtualFile file) {
    return false;
  }

  public void nonPhysicalChange() {
    throw new UnsupportedOperationException("Method nonPhysicalChange is not yet implemented in " + getClass().getName());
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
}
