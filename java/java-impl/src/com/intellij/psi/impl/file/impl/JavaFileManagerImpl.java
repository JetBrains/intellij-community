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
package com.intellij.psi.impl.file.impl;

import com.intellij.AppTopics;
import com.intellij.ProjectTopics;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManagerAdapter;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.file.PsiPackageImpl;
import com.intellij.psi.impl.java.stubs.index.JavaFullClassNameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Query;
import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class JavaFileManagerImpl implements JavaFileManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.file.impl.JavaFileManagerImpl");
  private final ConcurrentHashMap<GlobalSearchScope, PsiClass> myCachedObjectClassMap = new ConcurrentHashMap<GlobalSearchScope, PsiClass>();
  private final Map<String,PsiClass> myNameToClassMap = new ConcurrentHashMap<String, PsiClass>(); // used only in mode without repository
  @NonNls private static final String JAVA_EXTENSION = ".java";
  @NonNls private static final String CLASS_EXTENSION = ".class";
  private final PsiManagerEx myManager;
  private final ProjectRootManager myProjectRootManager;
  private final FileManager myFileManager;
  private final boolean myUseRepository;
  private Set<String> myNontrivialPackagePrefixes = null;
  private boolean myInitialized = false;
  private boolean myDisposed = false;
  private final PackageIndex myPackageIndex;


  public JavaFileManagerImpl(final PsiManagerEx manager, final ProjectRootManager projectRootManager, FileManager fileManager, MessageBus bus) {
    myManager = manager;
    myProjectRootManager = projectRootManager;
    myFileManager = fileManager;

    myUseRepository = true;

    myManager.registerRunnableToRunOnChange(new Runnable() {
      public void run() {
        myCachedObjectClassMap.clear();
      }
    });

    final MessageBusConnection connection = bus.connect();
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      public void beforeRootsChange(final ModuleRootEvent event) {
      }

      public void rootsChanged(final ModuleRootEvent event) {
        myNontrivialPackagePrefixes = null;
        clearNonRepositoryMaps();
      }
    });

    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      public void before(final List<? extends VFileEvent> events) {
        clearNonRepositoryMaps();
      }

      public void after(final List<? extends VFileEvent> events) {
        clearNonRepositoryMaps();
      }
    });

    connection.subscribe(AppTopics.FILE_DOCUMENT_SYNC, new FileDocumentManagerAdapter() {
      public void fileWithNoDocumentChanged(final VirtualFile file) {
        clearNonRepositoryMaps();
      }
    });
    
    myPackageIndex = PackageIndex.getInstance(myManager.getProject());
  }

  public void initialize() {
    myInitialized = true;
  }

  public void dispose() {
    myDisposed = true;
    myCachedObjectClassMap.clear();
  }

  private void clearNonRepositoryMaps() {
    if (!myUseRepository) {
      myNameToClassMap.clear();
    }
  }

  @Nullable
  public PsiPackage findPackage(@NotNull String packageName) {
    Query<VirtualFile> dirs = myPackageIndex.getDirsByPackageName(packageName, false);
    if (dirs.findFirst() == null) return null;
    return new PsiPackageImpl(myManager, packageName);
  }

  public PsiClass[] findClasses(@NotNull String qName, @NotNull final GlobalSearchScope scope) {
    final Collection<? extends PsiElement> classes = JavaFullClassNameIndex.getInstance().get(qName.hashCode(), myManager.getProject(), scope);
    if (classes.isEmpty()) return PsiClass.EMPTY_ARRAY;
    List<PsiClass> result = new ArrayList<PsiClass>(classes.size());
    int count = 0;
    PsiClass aClass = null;
    for (PsiElement found : classes) {
      if (notClass(found)) continue;

      aClass = (PsiClass)found;
      final String qualifiedName = aClass.getQualifiedName();
      if (qualifiedName == null || !qualifiedName.equals(qName)) continue;

      VirtualFile vFile = aClass.getContainingFile().getVirtualFile();
      if (!fileIsInScope(scope, vFile)) continue;

      result.add(aClass);
      count++;
    }

    if (count == 0) return PsiClass.EMPTY_ARRAY;
    if (count == 1) return new PsiClass[] {aClass};

    ContainerUtil.quickSort(result, new Comparator<PsiClass>() {
      public int compare(PsiClass o1, PsiClass o2) {
        return scope.compare(o2.getContainingFile().getVirtualFile(), o1.getContainingFile().getVirtualFile());
      }
    });

    return result.toArray(new PsiClass[count]);
  }

  public static boolean notClass(final PsiElement found) {
    if (found instanceof PsiClass) return false;

    VirtualFile faultyContainer = PsiUtil.getVirtualFile(found);
    LOG.error("Non class in class list: " + faultyContainer+". found: "+found);
    if (faultyContainer != null && faultyContainer.isValid()) {
      FileBasedIndex.getInstance().requestReindex(faultyContainer);
    }

    return true;
  }

  @Nullable
  public PsiClass findClass(@NotNull String qName, @NotNull GlobalSearchScope scope) {
    if (!myUseRepository) {
      return findClassWithoutRepository(qName);
    }

    if (!myInitialized) {
      LOG.error("Access to psi files should be performed only after startup activity");
      return null;
    }
    LOG.assertTrue(!myDisposed);

    if ("java.lang.Object".equals(qName)) { // optimization
      PsiClass cached = myCachedObjectClassMap.get(scope);
      if (cached == null) {
        cached = findClassInIndex(qName, scope);
        if (cached != null) {
          cached = myCachedObjectClassMap.cacheOrGet(scope, cached);
        }
      }

      return cached;
    }

    return findClassInIndex(qName, scope);
  }

  @Nullable
  private PsiClass findClassWithoutRepository(String qName) {
    PsiClass aClass = myNameToClassMap.get(qName);
    if (aClass != null) {
      return aClass;
    }

    aClass = _findClassWithoutRepository(qName);
    myNameToClassMap.put(qName, aClass);
    return aClass;
  }

  @Nullable
  private PsiClass _findClassWithoutRepository(String qName) {
    VirtualFile[] sourcePath = myProjectRootManager.orderEntries().sources().usingCache().getRoots();
    VirtualFile[] classPath = myProjectRootManager.orderEntries().withoutModuleSourceEntries().classes().usingCache().getRoots();

    int index = 0;
    while (index < qName.length()) {
      int index1 = qName.indexOf('.', index);
      if (index1 < 0) {
        index1 = qName.length();
      }
      String name = qName.substring(index, index1);

      final int sourceType = 0;
      //final int compiledType = 1;

      for (int type = 0; type < 2; type++) {
        VirtualFile[] vDirs = type == sourceType ? sourcePath : classPath;
        for (VirtualFile vDir : vDirs) {
          if (vDir != null) {
            VirtualFile vChild = type == sourceType
                                 ? vDir.findChild(name + JAVA_EXTENSION)
                                 : vDir.findChild(name + CLASS_EXTENSION);
            if (vChild != null) {
              PsiFile file = myFileManager.findFile(vChild);
              if (file instanceof PsiJavaFile) {
                PsiClass aClass = findClassByName((PsiJavaFile)file, name);
                if (aClass != null) {
                  index = index1 + 1;
                  while (index < qName.length()) {
                    index1 = qName.indexOf('.', index);
                    if (index1 < 0) {
                      index1 = qName.length();
                    }
                    name = qName.substring(index, index1);
                    aClass = findClassByName(aClass, name);
                    if (aClass == null) return null;
                    index = index1 + 1;
                  }
                  return aClass;
                }
              }
            }
          }
        }
      }

      boolean existsDir = false;
      for (int type = 0; type < 2; type++) {
        VirtualFile[] vDirs = type == sourceType ? sourcePath : classPath;
        for (int i = 0; i < vDirs.length; i++) {
          if (vDirs[i] != null) {
            VirtualFile vDirChild = vDirs[i].findChild(name);
            if (vDirChild != null) {
              PsiDirectory dir = myFileManager.findDirectory(vDirChild);
              if (dir != null) {
                vDirs[i] = vDirChild;
                existsDir = true;
                continue;
              }
            }
            vDirs[i] = null;
          }
        }
      }
      if (!existsDir) return null;
      index = index1 + 1;
    }
    return null;
  }

  @Nullable
  private static PsiClass findClassByName(PsiJavaFile scope, String name) {
    PsiClass[] classes = scope.getClasses();
    for (PsiClass aClass : classes) {
      if (name.equals(aClass.getName())) {
        return aClass;
      }
    }
    return null;
  }

  @Nullable
  private static PsiClass findClassByName(PsiClass scope, String name) {
    PsiClass[] classes = scope.getInnerClasses();
    for (PsiClass aClass : classes) {
      if (name.equals(aClass.getName())) {
        return aClass;
      }
    }
    return null;
  }

  @Nullable
  private PsiClass findClassInIndex(String qName, GlobalSearchScope scope) {
    VirtualFile bestFile = null;
    PsiClass bestClass = null;

    final Collection<? extends PsiElement> classes = JavaFullClassNameIndex.getInstance().get(qName.hashCode(), myManager.getProject(), scope);

    for (PsiElement found : classes) {
      if (notClass(found)) continue;

      PsiClass aClass = (PsiClass)found;
      final boolean valid = aClass.isValid();
      if (!valid) {
        LOG.error("Invalid class "+aClass+"; "+aClass.getContainingFile());
        continue;
      }

      final String qualifiedName = aClass.getQualifiedName();
      if (qualifiedName == null || !qualifiedName.equals(qName)) continue;

      PsiFile file = aClass.getContainingFile();
      if (file == null) {
        LOG.error("aClass=" + aClass);
        continue;
      }

      VirtualFile vFile = file.getVirtualFile();
      if (!fileIsInScope(scope, vFile)) continue;
      if (bestFile == null || scope.compare(vFile, bestFile) > 0) {
        bestFile = vFile;
        bestClass = aClass;
      }
    }

    return bestClass;
  }


  private boolean fileIsInScope(final GlobalSearchScope scope, final VirtualFile vFile) {
    if (!scope.contains(vFile)) return false;

    if (vFile.getFileType() == StdFileTypes.CLASS) {
      // See IDEADEV-5626
      final VirtualFile root = ProjectRootManager.getInstance(myManager.getProject()).getFileIndex().getClassRootForFile(vFile);
      VirtualFile parent = vFile.getParent();
      final PsiNameHelper nameHelper = JavaPsiFacade.getInstance(myManager.getProject()).getNameHelper();
      while (parent != null && parent != root) {
        if (!nameHelper.isIdentifier(parent.getName())) return false;
        parent = parent.getParent();
      }
    }

    return true;
  }

  public Collection<String> getNonTrivialPackagePrefixes() {
    if (myNontrivialPackagePrefixes == null) {
      Set<String> names = new HashSet<String>();
      final ProjectRootManager rootManager = myProjectRootManager;
      final VirtualFile[] sourceRoots = rootManager.getContentSourceRoots();
      final ProjectFileIndex fileIndex = rootManager.getFileIndex();
      for (final VirtualFile sourceRoot : sourceRoots) {
        final String packageName = fileIndex.getPackageNameByDirectory(sourceRoot);
        if (packageName != null && packageName.length() > 0) {
          names.add(packageName);
        }
      }
      myNontrivialPackagePrefixes = names;
    }
    return myNontrivialPackagePrefixes;
  }
}
