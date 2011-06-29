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
package com.intellij.psi.impl.file;

import com.intellij.codeInsight.completion.scope.JavaCompletionProcessor;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.PackageViewPane;
import com.intellij.ide.projectView.impl.ProjectRootsUtil;
import com.intellij.ide.projectView.impl.nodes.PackageElement;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.GlobalUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaPsiFacadeImpl;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.migration.PsiMigrationImpl;
import com.intellij.psi.impl.source.tree.java.PsiCompositeModifierList;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.util.CommonProcessors;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class PsiPackageImpl extends PsiPackageBase implements PsiPackage, Queryable {

  private volatile CachedValue<PsiModifierList> myAnnotationList;
  private volatile CachedValue<Collection<PsiDirectory>> myDirectories;

  private volatile Set<String> myPublicClassNamesCache;
  private final Object myPublicClassNamesCacheLock = new String("package classnames cache lock");

  public PsiPackageImpl(PsiManagerEx manager, String qualifiedName) {
    super(manager, qualifiedName);
  }

  protected Collection<PsiDirectory> getAllDirectories() {
    if (myDirectories == null) {
      myDirectories = CachedValuesManager.getManager(myManager.getProject()).createCachedValue(new CachedValueProvider<Collection<PsiDirectory>>() {
        public Result<Collection<PsiDirectory>> compute() {
          final CommonProcessors.CollectProcessor<PsiDirectory> processor = new CommonProcessors.CollectProcessor<PsiDirectory>();
          getFacade().processPackageDirectories(PsiPackageImpl.this, allScope(), processor);
          return Result.create(processor.getResults(),
                               PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT, ProjectRootManager.getInstance(getProject()));
        }
      }, false);
    }
    return myDirectories.getValue();
  }

  @Override
  protected PsiElement findPackage(String qName) {
    return getFacade().findPackage(qName);
  }

  public void handleQualifiedNameChange(@NotNull final String newQualifiedName) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    final String oldQualifedName = getQualifiedName();
    final boolean anyChanged = changePackagePrefixes(oldQualifedName, newQualifiedName);
    if (anyChanged) {
      UndoManager.getInstance(myManager.getProject()).undoableActionPerformed(new GlobalUndoableAction() {
        public void undo() {
          changePackagePrefixes(newQualifiedName, oldQualifedName);
        }

        public void redo() {
          changePackagePrefixes(oldQualifedName, newQualifiedName);
        }
      });
    }
  }

  private boolean changePackagePrefixes(final String oldQualifiedName, final String newQualifiedName) {
    final Module[] modules = ModuleManager.getInstance(myManager.getProject()).getModules();
    List<ModifiableRootModel> modelsToCommit = new ArrayList<ModifiableRootModel>();
    for (final Module module : modules) {
      boolean anyChange = false;
      final ModifiableRootModel rootModel = ModuleRootManager.getInstance(module).getModifiableModel();
      final ContentEntry[] contentEntries = rootModel.getContentEntries();
      for (final ContentEntry contentEntry : contentEntries) {
        final SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
        for (final SourceFolder sourceFolder : sourceFolders) {
          final String packagePrefix = sourceFolder.getPackagePrefix();
          if (packagePrefix.startsWith(oldQualifiedName)) {
            sourceFolder.setPackagePrefix(newQualifiedName + packagePrefix.substring(oldQualifiedName.length()));
            anyChange = true;
          }
        }
      }
      if (anyChange) {
        modelsToCommit.add(rootModel);
      } else {
        rootModel.dispose();
      }
    }

    if (!modelsToCommit.isEmpty()) {
      ProjectRootManager.getInstance(myManager.getProject()).multiCommit(
        modelsToCommit.toArray(new ModifiableRootModel[modelsToCommit.size()])
      );
      return true;
    } else {
      return false;
    }
  }

  public VirtualFile[] occursInPackagePrefixes() {
    List<VirtualFile> result = new ArrayList<VirtualFile>();
    final Module[] modules = ModuleManager.getInstance(myManager.getProject()).getModules();

    for (final Module module : modules) {
      final ContentEntry[] contentEntries = ModuleRootManager.getInstance(module).getContentEntries();
      for (final ContentEntry contentEntry : contentEntries) {
        final SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
        for (final SourceFolder sourceFolder : sourceFolders) {
          final String packagePrefix = sourceFolder.getPackagePrefix();
          if (packagePrefix.startsWith(getQualifiedName())) {
            final VirtualFile file = sourceFolder.getFile();
            if (file != null) {
              result.add(file);
            }
          }
        }
      }
    }

    return VfsUtil.toVirtualFileArray(result);
  }

  @Override
  public PsiPackageImpl getParentPackage() {
    return (PsiPackageImpl)super.getParentPackage();
  }


  @Override
  protected PsiPackageImpl createInstance(PsiManagerEx manager, String qName) {
    return new PsiPackageImpl(myManager, qName);
  }

  @NotNull
  public Language getLanguage() {
    return StdFileTypes.JAVA.getLanguage();
  }

  public boolean isValid() {
    final CommonProcessors.FindFirstProcessor<PsiDirectory> processor = new CommonProcessors.FindFirstProcessor<PsiDirectory>();
    getFacade().processPackageDirectories(this, allScope(), processor);
    return processor.getFoundValue() != null || getFacade().packagePrefixExists(getQualifiedName());
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitPackage(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiPackage:" + getQualifiedName();
  }

  @NotNull
  public PsiClass[] getClasses() {
    return getClasses(allScope());
  }

  protected GlobalSearchScope allScope() {
    return NonClasspathClassFinder.addNonClasspathScope(getProject(), GlobalSearchScope.allScope(getProject()));
  }

  @NotNull
  public PsiClass[] getClasses(@NotNull GlobalSearchScope scope) {
    return getFacade().getClasses(this, scope);
  }

  @Nullable
  public PsiModifierList getAnnotationList() {
    if (myAnnotationList == null) {
      myAnnotationList = CachedValuesManager.getManager(myManager.getProject()).createCachedValue(new PackageAnnotationValueProvider());
    }
    return myAnnotationList.getValue();
  }

  @NotNull
  public PsiPackage[] getSubPackages() {
    return getSubPackages(allScope());
  }

  @NotNull
  public PsiPackage[] getSubPackages(@NotNull GlobalSearchScope scope) {
    return getFacade().getSubPackages(this, scope);
  }

  private JavaPsiFacadeImpl getFacade() {
    return (JavaPsiFacadeImpl)JavaPsiFacade.getInstance(myManager.getProject());
  }

  private Set<String> getClassNamesCache() {
    if (myPublicClassNamesCache == null) {
      Set<String> classNames = getFacade().getClassNames(this, allScope());
      synchronized (myPublicClassNamesCacheLock) {
        myPublicClassNamesCache = classNames;
      }
    }

    return myPublicClassNamesCache;
  }

  @NotNull
  private PsiClass[] findClassesByName(String name, GlobalSearchScope scope) {
    final String qName = getQualifiedName();
    final String classQName = qName.length() > 0 ? qName + "." + name : name;
    return getFacade().findClasses(classQName, scope);
  }

  public boolean containsClassNamed(String name) {
    return getClassNamesCache().contains(name);
  }

  @Nullable
  private PsiPackage findSubPackageByName(String name) {
    final String qName = getQualifiedName();
    final String subpackageQName = qName.length() > 0 ? qName + "." + name : name;
    PsiPackage aPackage = getFacade().findPackage(subpackageQName);
    if (aPackage == null) return null;
    //if (aPackage.getDirectories(scope).length == 0) return null;
    return aPackage;
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    GlobalSearchScope scope = place.getResolveScope();

    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, this);
    ElementClassHint classHint = processor.getHint(ElementClassHint.KEY);

    final JavaPsiFacadeImpl facade = getFacade();
    final PsiMigrationImpl migration = facade.getCurrentMigration();

    final Condition<String> prefixMatcher = processor.getHint(JavaCompletionProcessor.NAME_FILTER);

    if (classHint == null || classHint.shouldProcess(ElementClassHint.DeclarationKind.CLASS)) {
      NameHint nameHint = processor.getHint(NameHint.KEY);
      if (nameHint != null) {
        final String shortName = nameHint.getName(state);
        if ((migration != null || containsClassNamed(shortName))
            && processClassesByName(processor, state, place, scope, shortName)) return false;
      }
      else if (prefixMatcher != null && migration == null) {
        for (String className : getClassNamesCache()) {
          if (prefixMatcher.value(className)) {
            if (processClassesByName(processor, state, place, scope, className)) return false;
          }
        }
      }
      else {
        PsiClass[] classes = getClasses(scope);
        if (!processClasses(processor, state, classes)) return false;
        if (migration != null) {
          for (PsiClass psiClass : migration.getMigrationClasses(getQualifiedName())) {
            if (!processor.execute(psiClass, state)) {
              return false;
            }
          }
        }
      }
    }
    if (classHint == null || classHint.shouldProcess(ElementClassHint.DeclarationKind.PACKAGE)) {
      NameHint nameHint = processor.getHint(NameHint.KEY);
      if (nameHint != null) {
        PsiPackage aPackage = findSubPackageByName(nameHint.getName(state));
        if (aPackage != null) {
          if (!processor.execute(aPackage, state)) return false;
        }
      }
      else {
        PsiPackage[] packs = getSubPackages(scope);
        for (PsiPackage pack : packs) {
          final String packageName = pack.getName();
          if (packageName == null) continue;
          if (!facade.getNameHelper().isIdentifier(packageName, PsiUtil.getLanguageLevel(this))) {
            continue;
          }
          if (!processor.execute(pack, state)) {
            return false;
          }
        }

        if (migration != null) {
          for (PsiPackage aPackage : migration.getMigrationPackages(getQualifiedName())) {
            if (!processor.execute(aPackage, state)) {
              return false;
            }
          }
        }
      }
    }
    return true;
  }

  private boolean processClassesByName(PsiScopeProcessor processor, ResolveState state, PsiElement place, GlobalSearchScope scope, String className) {
    final PsiClass[] classes = findClassesByName(className, scope);
    return !processClasses(processor, state, classes);
  }

  private static boolean processClasses(PsiScopeProcessor processor, ResolveState state, PsiClass[] classes) {
    for (PsiClass aClass : classes) {
      if (!processor.execute(aClass, state)) return false;
    }
    return true;
  }

  public boolean canNavigate() {
    return isValid();
  }

  public void navigate(final boolean requestFocus) {
    ToolWindow window = ToolWindowManager.getInstance(getProject()).getToolWindow(ToolWindowId.PROJECT_VIEW);
    window.activate(null);
    window.getActivation().doWhenDone(new Runnable() {
      public void run() {
        final ProjectView projectView = ProjectView.getInstance(getProject());
        projectView.changeView(PackageViewPane.ID);
        final PsiDirectory[] directories = getDirectories();
        final VirtualFile firstDir = directories[0].getVirtualFile();
        final boolean isLibraryRoot = ProjectRootsUtil.isLibraryRoot(firstDir, getProject());

        final Module module = ProjectRootManager.getInstance(getProject()).getFileIndex().getModuleForFile(firstDir);
        final PackageElement packageElement = new PackageElement(module, PsiPackageImpl.this, isLibraryRoot);
        projectView.getProjectViewPaneById(PackageViewPane.ID).select(packageElement, firstDir, requestFocus);
      }
    });
  }

  private class PackageAnnotationValueProvider implements CachedValueProvider<PsiModifierList> {
    @NonNls private static final String PACKAGE_INFO_FILE = "package-info.java";
    private final Object[] OOCB_DEPENDENCY = { PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT };

    public Result<PsiModifierList> compute() {
      List<PsiModifierList> list = new ArrayList<PsiModifierList>();
      for(PsiDirectory directory: getDirectories()) {
        PsiFile file = directory.findFile(PACKAGE_INFO_FILE);
        if (file != null) {
          PsiPackageStatement stmt = PsiTreeUtil.getChildOfType(file, PsiPackageStatement.class);
          if (stmt != null) {
            final PsiModifierList modifierList = stmt.getAnnotationList();
            if (modifierList != null) {
              list.add(modifierList);
            }
          }
        }
      }

      final JavaPsiFacadeImpl facade = getFacade();
      for (PsiClass aClass : facade.findClasses(getQualifiedName() + ".package-info", allScope())) {
        ContainerUtil.addIfNotNull(aClass.getModifierList(), list);
      }

      return new Result<PsiModifierList>(list.isEmpty() ? null : new PsiCompositeModifierList(getManager(), list), OOCB_DEPENDENCY);
    }
  }

  @Nullable
  public PsiModifierList getModifierList() {
    return getAnnotationList();
  }

  public boolean hasModifierProperty(@NonNls @NotNull final String name) {
    return false;
  }

  public PsiQualifiedNamedElement getContainer() {
    return getParentPackage();
  }

}
