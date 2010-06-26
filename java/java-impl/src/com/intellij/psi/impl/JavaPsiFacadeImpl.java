/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.psi.impl;

import com.intellij.ProjectTopics;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadActionProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.PackageIndex;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.*;
import com.intellij.psi.impl.file.PsiPackageImpl;
import com.intellij.psi.impl.file.impl.JavaFileManager;
import com.intellij.psi.impl.file.impl.JavaFileManagerImpl;
import com.intellij.psi.impl.migration.PsiMigrationImpl;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.JavaDummyHolder;
import com.intellij.psi.impl.source.JavaDummyHolderFactory;
import com.intellij.psi.impl.source.codeStyle.HelperFactory;
import com.intellij.psi.impl.source.codeStyle.JavaHelperFactory;
import com.intellij.psi.impl.source.javadoc.JavadocManagerImpl;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.impl.source.resolve.PsiResolveHelperImpl;
import com.intellij.psi.impl.source.tree.JavaChangeUtilSupport;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.javadoc.JavadocManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.util.containers.HashMap;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentMap;

public class JavaPsiFacadeImpl extends JavaPsiFacadeEx implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.JavaPsiFacadeImpl");

  private PsiMigrationImpl myCurrentMigration;
  private final PsiElementFinder[] myElementFinders;
  private PsiShortNamesCache myShortNamesCache;
  private final PsiResolveHelper myResolveHelper;
  private final JavadocManager myJavadocManager;
  private final PsiNameHelper myNameHelper;
  private final PsiElementFactory myElementFactory;
  private final PsiConstantEvaluationHelper myConstantEvaluationHelper;
  private final ConcurrentMap<String, PsiPackage> myPackageCache = new ConcurrentHashMap<String, PsiPackage>();
  private final Project myProject;
  private final JavaFileManager myFileManager;
  private final PackagePrefixIndex myPackagePrefixIndex;


  public JavaPsiFacadeImpl(Project project,
                           PsiManagerImpl psiManager,
                           final ProjectRootManagerEx projectRootManagerEx,
                           StartupManager startupManager,
                           MessageBus bus

  ) {
    myProject = project;
    myResolveHelper = new PsiResolveHelperImpl(PsiManager.getInstance(project));
    myJavadocManager = new JavadocManagerImpl(project);
    myNameHelper = new PsiNameHelperImpl(this);
    myConstantEvaluationHelper = new PsiConstantEvaluationHelperImpl();
    myElementFactory = new PsiElementFactoryImpl(psiManager);

    List<PsiElementFinder> elementFinders = new ArrayList<PsiElementFinder>();
    elementFinders.add(new PsiElementFinderImpl());
    elementFinders.addAll(Arrays.asList(myProject.getExtensions(PsiElementFinder.EP_NAME)));
    myElementFinders = elementFinders.toArray(new PsiElementFinder[elementFinders.size()]);

    myPackagePrefixIndex = new PackagePrefixIndex(myProject);

    boolean isProjectDefault = project.isDefault();

    if (isProjectDefault) {
      myShortNamesCache = new EmptyShortNamesCacheImpl();
    } else {
      myShortNamesCache = new PsiShortNamesCacheImpl((PsiManagerEx)PsiManager.getInstance(project));
      for (final PsiShortNamesCache cache : project.getExtensions(PsiShortNamesCache.EP_NAME)) {
        _registerShortNamesCache(cache);
      }
    }

    myFileManager = new JavaFileManagerImpl(psiManager, projectRootManagerEx, psiManager.getFileManager(), bus);

    final PsiModificationTrackerImpl modificationTracker = (PsiModificationTrackerImpl) psiManager.getModificationTracker();
    psiManager.addTreeChangePreprocessor(new JavaCodeBlockModificationListener(modificationTracker));

    bus.connect().subscribe(ProjectTopics.MODIFICATION_TRACKER, new PsiModificationTracker.Listener() {
      private long lastTimeSeen = -1L;
      public void modificationCountChanged() {
        final long now = modificationTracker.getJavaStructureModificationCount();
        if (lastTimeSeen != now) {
          lastTimeSeen = now;
          myPackageCache.clear();
        }
      }
    });

    startupManager.registerStartupActivity(
      new Runnable() {
        public void run() {
          runStartupActivity();
        }
      }
    );


    JavaChangeUtilSupport.setup();
    DummyHolderFactory.setFactory(new JavaDummyHolderFactory());
    HelperFactory.setFactory(new JavaHelperFactory());
    JavaElementType.ANNOTATION.getIndex(); // Initialize stubs.
    Disposer.register(project, this);
  }

  private void runStartupActivity() {
    myFileManager.initialize();
    myShortNamesCache.runStartupActivity();
  }

  public void dispose() {
    myFileManager.dispose();
  }

  /**
   * @deprecated
   */
  public PsiClass findClass(@NotNull String qualifiedName) {
    return findClass(qualifiedName, GlobalSearchScope.allScope(myProject));
  }

  public PsiClass findClass(@NotNull final String qualifiedName, @NotNull GlobalSearchScope scope) {
    ProgressManager.checkCanceled(); // We hope this method is being called often enough to cancel daemon processes smoothly

    if (DumbService.getInstance(getProject()).isDumb()) {
      final List<PsiClass> classes = findClassesInDumbMode(qualifiedName, scope);
      if (!classes.isEmpty()) {
        return classes.get(0);
      }
      return null;
    }

    for (PsiElementFinder finder : myElementFinders) {
      PsiClass aClass = finder.findClass(qualifiedName, scope);
      if (aClass != null) return aClass;
    }

    return null;
  }

  @NotNull
  private List<PsiClass> findClassesInDumbMode(String qualifiedName, GlobalSearchScope scope) {
    final String packageName = StringUtil.getPackageName(qualifiedName);
    final PsiPackage pkg = findPackage(packageName);
    final String className = StringUtil.getShortName(qualifiedName);
    if (pkg == null && packageName.length() < qualifiedName.length()) {
      final List<PsiClass> containingClasses = findClassesInDumbMode(packageName, scope);
      if (containingClasses.size() == 1) {
        return filterByName(className, containingClasses.get(0).getInnerClasses());
      }

      return Collections.emptyList();
    }

    if (pkg == null || pkg instanceof PsiPackageImpl && !((PsiPackageImpl)pkg).containsClassNamed(className)) {
      return Collections.emptyList();
    }

    return filterByName(className, pkg.getClasses(scope));
  }

  private static List<PsiClass> filterByName(String className, PsiClass[] classes) {
    final List<PsiClass> foundClasses = new SmartList<PsiClass>();
    for (PsiClass psiClass : classes) {
      if (className.equals(psiClass.getName())) {
        foundClasses.add(psiClass);
      }
    }
    return foundClasses;
  }

  @NotNull
  public PsiClass[] findClasses(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    if (DumbService.getInstance(getProject()).isDumb()) {
      final List<PsiClass> classes = findClassesInDumbMode(qualifiedName, scope);
      return classes.toArray(new PsiClass[classes.size()]);
    }

    List<PsiClass> classes = new SmartList<PsiClass>();
    for (PsiElementFinder finder : myElementFinders) {
      PsiClass[] finderClasses = finder.findClasses(qualifiedName, scope);
      classes.addAll(Arrays.asList(finderClasses));
    }

    return classes.toArray(new PsiClass[classes.size()]);
  }

  @NotNull
  public PsiConstantEvaluationHelper getConstantEvaluationHelper() {
    return myConstantEvaluationHelper;
  }

  public PsiPackage findPackage(@NotNull String qualifiedName) {
    PsiPackage aPackage = myPackageCache.get(qualifiedName);
    if (aPackage == null) {
      if (DumbService.getInstance(getProject()).isDumb()) {
        return findPackageDefault(qualifiedName);
      }

      for (PsiElementFinder finder : myElementFinders) {
        aPackage = finder.findPackage(qualifiedName);
        if (aPackage != null) {
          aPackage = ConcurrencyUtil.cacheOrGet(myPackageCache, qualifiedName, aPackage);
          break;
        }
      }
    }

    return aPackage;
  }


  public PsiMigrationImpl getCurrentMigration() {
    return myCurrentMigration;
  }

  @NotNull
  public PsiJavaParserFacade getParserFacade() {
    return getElementFactory(); // TODO: ligter implementation which doesn't mark all the elements as generated.
  }

  @NotNull
  public PsiResolveHelper getResolveHelper() {
    return myResolveHelper;
  }

  @NotNull
  public PsiShortNamesCache getShortNamesCache() {
    return myShortNamesCache;
  }

  public void registerShortNamesCache(@NotNull PsiShortNamesCache cache) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    _registerShortNamesCache(cache);
  }

  private void _registerShortNamesCache(PsiShortNamesCache cache) {
    assert !(cache instanceof CompositeShortNamesCache) : cache;
    if (myShortNamesCache instanceof CompositeShortNamesCache) {
      ((CompositeShortNamesCache)myShortNamesCache).addCache(cache);
    }
    else {
      CompositeShortNamesCache composite = new CompositeShortNamesCache();
      composite.addCache(myShortNamesCache);
      composite.addCache(cache);
      myShortNamesCache = composite;
    }
  }

  @NotNull
  public PsiMigration startMigration() {
    LOG.assertTrue(myCurrentMigration == null);
    myCurrentMigration = new PsiMigrationImpl(this, (PsiManagerImpl)PsiManager.getInstance(myProject));
    return myCurrentMigration;
  }

  @NotNull
  public JavadocManager getJavadocManager() {
    return myJavadocManager;
  }

  @NotNull
  public PsiNameHelper getNameHelper() {
    return myNameHelper;
  }

  public PsiClass[] getClasses(PsiPackageImpl psiPackage, GlobalSearchScope scope) {
    List<PsiClass> result = null;
    for (PsiElementFinder finder : myElementFinders) {
      PsiClass[] classes = finder.getClasses(psiPackage, scope);
      if (classes.length == 0) continue;
      if (result == null) result = new ArrayList<PsiClass>();
      result.addAll(Arrays.asList(classes));
    }

    return result == null ? PsiClass.EMPTY_ARRAY : result.toArray(new PsiClass[result.size()]);
  }

  public boolean processPackageDirectories(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope, Processor<PsiDirectory> consumer) {
    for (PsiElementFinder finder : myElementFinders) {
      if (!finder.processPackageDirectories(psiPackage, scope, consumer)) {
        return false;
      }
    }
    return true;
  }

  public PsiPackage[] getSubPackages(PsiPackageImpl psiPackage, GlobalSearchScope scope) {
    List<PsiPackage> result = new ArrayList<PsiPackage>();
    for (PsiElementFinder finder : myElementFinders) {
      PsiPackage[] packages = finder.getSubPackages(psiPackage, scope);
      result.addAll(Arrays.asList(packages));
    }

    return result.toArray(new PsiPackage[result.size()]);
  }

  public JavaFileManager getJavaFileManager() {
    return myFileManager;
  }

  public boolean packagePrefixExists(String packageQName) {
    for (final String prefix : myPackagePrefixIndex.getAllPackagePrefixes(null)) {
      if (StringUtil.startsWithConcatenationOf(prefix, packageQName, ".") || prefix.equals(packageQName)) {
        return true;
      }
    }

    return false;
  }

  private PsiPackage findPackageDefault(String qualifiedName) {
    final PsiPackage aPackage = myFileManager.findPackage(qualifiedName);
    if (aPackage == null && myCurrentMigration != null) {
      final PsiPackage migrationPackage = myCurrentMigration.getMigrationPackage(qualifiedName);
      if (migrationPackage != null) return migrationPackage;
    }

    if (packagePrefixExists(qualifiedName)) {
      return new PsiPackageImpl((PsiManagerEx)PsiManager.getInstance(myProject), qualifiedName);
    }

    return aPackage;
  }

  private class PsiElementFinderImpl extends PsiElementFinder {
    public PsiClass findClass(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
      PsiClass psiClass = myFileManager.findClass(qualifiedName, scope);

      if (psiClass == null && myCurrentMigration != null) {
        psiClass = myCurrentMigration.getMigrationClass(qualifiedName);
      }

      return psiClass;
    }

    @NotNull
    public PsiClass[] findClasses(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
      final PsiClass[] classes = myFileManager.findClasses(qualifiedName, scope);
      if (classes.length == 0 && myCurrentMigration != null) {
        final PsiClass migrationClass = myCurrentMigration.getMigrationClass(qualifiedName);
        if (migrationClass != null) {
          return new PsiClass[]{migrationClass};
        }
      }
      return classes;
    }

    public PsiPackage findPackage(@NotNull String qualifiedName) {
      return findPackageDefault(qualifiedName);
    }

    @NotNull
    public PsiPackage[] getSubPackages(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
      final Map<String, PsiPackage> packagesMap = new HashMap<String, PsiPackage>();
      final String qualifiedName = psiPackage.getQualifiedName();
      for (PsiDirectory dir : psiPackage.getDirectories(scope)) {
        PsiDirectory[] subdirs = dir.getSubdirectories();
        for (PsiDirectory subdir : subdirs) {
          final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(subdir);
          if (aPackage != null) {
            final String subQualifiedName = aPackage.getQualifiedName();
            if (subQualifiedName.startsWith(qualifiedName) && !packagesMap.containsKey(subQualifiedName)) {
              packagesMap.put(aPackage.getQualifiedName(), aPackage);
            }
          }
        }
      }
      for (final String prefix : myPackagePrefixIndex.getAllPackagePrefixes(scope)) {
        if (StringUtil.isEmpty(qualifiedName) || StringUtil.startsWithConcatenationOf(prefix, qualifiedName, ".")) {
          final int i = prefix.indexOf('.', qualifiedName.length() + 1);
          String childName = i >= 0 ? prefix.substring(0, i) : prefix;
          if (!packagesMap.containsKey(childName)) {
            packagesMap.put(childName, new PsiPackageImpl((PsiManagerEx)psiPackage.getManager(), childName));
          }
        }
      }

      packagesMap.remove(qualifiedName);    // avoid SOE caused by returning a package as a subpackage of itself
      return packagesMap.values().toArray(new PsiPackage[packagesMap.size()]);
    }

    @NotNull
    public PsiClass[] getClasses(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
      List<PsiClass> list = null;
      final PsiDirectory[] dirs = psiPackage.getDirectories(scope);
      String packageName = psiPackage.getQualifiedName();
      for (PsiDirectory dir : dirs) {
        PsiClass[] classes = JavaDirectoryService.getInstance().getClasses(dir);
        if (classes.length == 0) continue;
        if (list == null) list = new ArrayList<PsiClass>();
        for (PsiClass aClass : classes) {
          // class file can be located in wrong place inside file system
          String qualifiedName = aClass.getQualifiedName();
          if (qualifiedName != null) qualifiedName = StringUtil.getPackageName(qualifiedName);
          if (Comparing.strEqual(qualifiedName, packageName)) {
            list.add(aClass);
          }
        }
      }
      return list == null ? PsiClass.EMPTY_ARRAY : list.toArray(new PsiClass[list.size()]);
    }

    @Override
    public boolean processPackageDirectories(@NotNull PsiPackage psiPackage, @NotNull final GlobalSearchScope scope, final Processor<PsiDirectory> consumer) {
      final PsiManager psiManager = PsiManager.getInstance(getProject());
      PackageIndex.getInstance(getProject()).getDirsByPackageName(psiPackage.getQualifiedName(), false).forEach(new ReadActionProcessor<VirtualFile>() {
        public boolean processInReadAction(final VirtualFile dir) {
          if (!scope.contains(dir)) return true;
          PsiDirectory psiDir = psiManager.findDirectory(dir);
          assert psiDir != null;
          return consumer.process(psiDir);
        }
      });
      return true;
    }
  }

  public void migrationModified(boolean terminated) {
    if (terminated) {
      myCurrentMigration = null;
    }

    ((PsiManagerEx)PsiManager.getInstance(myProject)).physicalChange();
  }


  public boolean isPartOfPackagePrefix(String packageName) {
    final Collection<String> packagePrefixes = myFileManager.getNonTrivialPackagePrefixes();
    for (final String subpackageName : packagePrefixes) {
      if (isSubpackageOf(subpackageName, packageName)) return true;
    }
    return false;
  }

  private static boolean isSubpackageOf(final String subpackageName, String packageName) {
    return subpackageName.equals(packageName) ||
           subpackageName.startsWith(packageName) && subpackageName.charAt(packageName.length()) == '.';
  }

  public boolean isInPackage(@NotNull PsiElement element, @NotNull PsiPackage aPackage) {
    final PsiFile file = FileContextUtil.getContextFile(element);
    if (file instanceof JavaDummyHolder) {
      return ((JavaDummyHolder) file).isInPackage(aPackage);
    }
    if (file instanceof PsiJavaFile) {
      final String packageName = ((PsiJavaFile) file).getPackageName();
      return packageName.equals(aPackage.getQualifiedName());
    }
    return false;
  }

  public boolean arePackagesTheSame(@NotNull PsiElement element1, @NotNull PsiElement element2) {
    PsiFile file1 = FileContextUtil.getContextFile(element1);
    PsiFile file2 = FileContextUtil.getContextFile(element2);
    if (Comparing.equal(file1, file2)) return true;
    if (file1 instanceof JavaDummyHolder && file2 instanceof JavaDummyHolder) return true;
    if (file1 instanceof JavaDummyHolder || file2 instanceof JavaDummyHolder) {
      JavaDummyHolder dummyHolder = (JavaDummyHolder) (file1 instanceof JavaDummyHolder ? file1 : file2);
      PsiElement other = file1 instanceof JavaDummyHolder ? file2 : file1;
      return dummyHolder.isSamePackage(other);
    }
    if (!(file1 instanceof PsiClassOwner)) return false;
    if (!(file2 instanceof PsiClassOwner)) return false;
    String package1 = ((PsiClassOwner) file1).getPackageName();
    String package2 = ((PsiClassOwner) file2).getPackageName();
    return Comparing.equal(package1, package2);
  }

  public Project getProject() {
    return myProject;
  }

  @NotNull
  public PsiElementFactory getElementFactory() {
    return myElementFactory;
  }

  public void setAssertOnFileLoadingFilter(final VirtualFileFilter filter) {
    ((PsiManagerImpl)PsiManager.getInstance(myProject)).setAssertOnFileLoadingFilter(filter);
  }

  private static class JavaCodeBlockModificationListener implements PsiTreeChangePreprocessor {
    private final PsiModificationTrackerImpl myModificationTracker;

    private JavaCodeBlockModificationListener(final PsiModificationTrackerImpl modificationTracker) {
      myModificationTracker = modificationTracker;
    }

    public void treeChanged(final PsiTreeChangeEventImpl event) {
      switch (event.getCode()) {
        case BEFORE_CHILDREN_CHANGE:
        case BEFORE_PROPERTY_CHANGE:
        case BEFORE_CHILD_MOVEMENT:
        case BEFORE_CHILD_REPLACEMENT:
        case BEFORE_CHILD_ADDITION:
          break;
        case BEFORE_CHILD_REMOVAL:
          checkAnnotation(event.getChild());
          checkModifierListOwner(event.getChild());
          if (event.getChild() instanceof PsiClassOwner) {
            PsiClass[] classes = ((PsiClassOwner)event.getChild()).getClasses();
            for (PsiClass psiClass : classes) {
              checkModifierListOwner(psiClass);              
            }
          }
          break;

        case CHILD_ADDED:
        case CHILD_REMOVED:
        case CHILD_REPLACED:
          processChange(event.getParent(), event.getOldChild(), event.getChild());
          break;

        case CHILDREN_CHANGED:
          processChange(event.getParent(), event.getParent(), null);
          break;

        case CHILD_MOVED:
        case PROPERTY_CHANGED:
          myModificationTracker.incCounter();
          break;

        default:
          LOG.error("Unknown code:" + event.getCode());
          break;
      }
    }

    private void checkModifierListOwner(PsiElement child) {
      if (child instanceof PsiClass || child instanceof PsiMethod) {
        PsiModifierList modifierList = ((PsiModifierListOwner)child).getModifierList();
        if (modifierList != null && modifierList.getAnnotations().length > 0) {
          myModificationTracker.incAnnotationModificationCounter();             
        }
      }
    }

    private void processChange(final PsiElement parent, final PsiElement child1, final PsiElement child2) {
      try {
        if (!isInsideCodeBlock(parent)) {
          if (parent != null && isClassOwner(parent.getContainingFile()) || isClassOwner(child1) || isClassOwner(child2)) {
            myModificationTracker.incCounter();
          }
          else {
            myModificationTracker.incOutOfCodeBlockModificationCounter();
          }
          checkAnnotation(parent);
          checkModifierListOwner(parent);
          return;
        }

        if (containsClassesInside(child1) || child2 != child1 && containsClassesInside(child2)) {
          myModificationTracker.incCounter();
        }
      }
      catch (PsiInvalidElementAccessException e) {
        myModificationTracker.incCounter(); // Shall not happen actually, just a pre-release paranoia
      }
    }

    private void checkAnnotation(PsiElement parent) {
      if (PsiTreeUtil.getParentOfType(parent, PsiAnnotation.class, false) != null) {
        myModificationTracker.incAnnotationModificationCounter();
      }
    }

    private static boolean isClassOwner(final PsiElement element) {
      return element instanceof PsiClassOwner && !(element instanceof XmlFile);
    }

    private static boolean containsClassesInside(final PsiElement element) {
      if (element == null) return false;
      if (element instanceof PsiClass) return true;

      PsiElement child = element.getFirstChild();
      while (child != null) {
        if (containsClassesInside(child)) return true;
        child = child.getNextSibling();
      }

      return false;
    }

    private static boolean isInsideCodeBlock(PsiElement element) {
      if (element instanceof PsiFileSystemItem) {
        return false;
      }
      
      if (element == null || element.getParent() == null) return true;

      while(true){
        if (element instanceof PsiFile || element instanceof PsiDirectory || element == null){
          return false;
        }
        PsiElement pparent = element.getParent();
        if (element instanceof PsiClass) return false; // anonymous or local class
        if (element instanceof PsiCodeBlock){
          if (pparent instanceof PsiMethod || pparent instanceof PsiClassInitializer){
            return true;
          }
        }
        element = pparent;
      }
    }

  }
}
