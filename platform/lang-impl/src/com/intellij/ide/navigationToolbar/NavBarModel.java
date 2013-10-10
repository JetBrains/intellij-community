/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

package com.intellij.ide.navigationToolbar;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleFileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiFileSystemItemProcessor;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Konstantin Bulenkov
 * @author Anna Kozlova
 */
public class NavBarModel {
  private List<Object> myModel = Collections.emptyList();
  private int mySelectedIndex;
  private final Project myProject;
  private final NavBarModelListener myNotificator;
  private boolean myChanged = true;
  private boolean updated = false;
  private boolean isFixedComponent = false;

  public NavBarModel(final Project project) {
    myProject = project;
    myNotificator = project.getMessageBus().syncPublisher(NavBarModelListener.NAV_BAR);
  }

  public int getSelectedIndex() {
    return mySelectedIndex;
  }

  @Nullable
  public Object getSelectedValue() {
    return getElement(mySelectedIndex);
  }

  @Nullable
  public Object getElement(int index) {
    if (index != -1 && index < myModel.size()) {
      return myModel.get(index);
    }
    return null;
  }

  public int size() {
    return myModel.size();
  }

  public boolean isEmpty() {
    return myModel.isEmpty();
  }

  public int getIndexByModel(int index) {
    if (index < 0) return myModel.size() + index;
    if (index >= myModel.size() && myModel.size() > 0) return index % myModel.size();
    return index;
  }

  protected void updateModel(DataContext dataContext) {
    if (LaterInvocator.isInModalContext() || (updated && !isFixedComponent)) return;

    if (PlatformDataKeys.CONTEXT_COMPONENT.getData(dataContext) instanceof NavBarPanel) return;

    PsiElement psiElement = CommonDataKeys.PSI_FILE.getData(dataContext);
    if (psiElement == null) {
      psiElement = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
    }

    psiElement = normalize(psiElement);
    if (!myModel.isEmpty() && myModel.get(myModel.size() - 1).equals(psiElement) && !myChanged) return;

    if (psiElement != null && psiElement.isValid()) {
      updateModel(psiElement);
    }
    else {
      if (UISettings.getInstance().SHOW_NAVIGATION_BAR && !myModel.isEmpty()) return;

      // Narrow down the root element to the first interesting one
      Object root = LangDataKeys.MODULE.getData(dataContext);
      if (root == null) {
        Project project = CommonDataKeys.PROJECT.getData(dataContext);
        if (project != null) {
          Module[] modules = getProjectChildren(project);
          if (modules.length == 1) {
            Module soleModule = modules[0];
            List<Object> contentRoots = getModuleChildren(soleModule);
            if (contentRoots.size() == 1) {
              root = contentRoots.get(0);
            }
            else {
              root = soleModule;
            }
          }
          else {
            root = project;
          }
        }
      }
      
      if (root != null) {
        setModel(Collections.singletonList(root));
      }
    }
    setChanged(false);

    updated = true;
  }

  protected void updateModel(final PsiElement psiElement) {

    final Set<VirtualFile> roots = new HashSet<VirtualFile>();
    final ProjectRootManager projectRootManager = ProjectRootManager.getInstance(myProject);
    final ProjectFileIndex projectFileIndex = projectRootManager.getFileIndex();

    for (VirtualFile root : projectRootManager.getContentRoots()) {
      VirtualFile parent = root.getParent();
      if (parent == null || !projectFileIndex.isInContent(parent)) {
        roots.add(root);
      }
    }

    for (final NavBarModelExtension modelExtension : Extensions.getExtensions(NavBarModelExtension.EP_NAME)) {
      for (VirtualFile root : modelExtension.additionalRoots(psiElement.getProject())) {
        VirtualFile parent = root.getParent();
        if (parent == null || !projectFileIndex.isInContent(parent)) {
          roots.add(root);
        }
      }
    }

    final List<Object> updatedModel = new ArrayList<Object>();

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        traverseToRoot(psiElement, roots, updatedModel);
      }
    });

    setModel(updatedModel);
  }

  void revalidate() {
    final List<Object> objects = new ArrayList<Object>();
    boolean update = false;
    for (Object o : myModel) {
      if (isValid(o)) {
        objects.add(o);
      } else {
        update = true;
        break;
      }
    }
    if (update) {
      setModel(objects);
    }
  }

  protected void setModel(List<Object> model) {
    if (!model.equals(myModel)) {
      myModel = model;
      myNotificator.modelChanged();

      mySelectedIndex = myModel.size() - 1;
      myNotificator.selectionChanged();
    }
  }

  public void updateModel(final Object object) {
    if (object instanceof PsiElement) {
      updateModel((PsiElement)object);
    }
    else if (object instanceof Module) {
      List<Object> l = new ArrayList<Object>();
      l.add(myProject);
      l.add(object);
      setModel(l);
    }
  }

  private void traverseToRoot(@NotNull PsiElement psiElement, Set<VirtualFile> roots, List<Object> model) {
    if (!psiElement.isValid()) return;
    final PsiFile containingFile = psiElement.getContainingFile();
    if (containingFile != null &&
        (containingFile.getVirtualFile() == null || !containingFile.getViewProvider().isPhysical())) return; //non phisycal elements
    psiElement = getOriginalElement(psiElement);
    PsiElement resultElement = psiElement;

    for (final NavBarModelExtension modelExtension : Extensions.getExtensions(NavBarModelExtension.EP_NAME)) {
      resultElement = modelExtension.adjustElement(resultElement);
    }

    boolean foundByExtension = false;
    for (final NavBarModelExtension modelExtension : Extensions.getExtensions(NavBarModelExtension.EP_NAME)) {
      final PsiElement parent = modelExtension.getParent(resultElement);
      if (parent != null) {
        if (parent != resultElement) { // HACK is to return same element to stop traversing
          traverseToRoot(parent, roots, model);
        }
        foundByExtension = true;
        break;
      }
    }

    if (!foundByExtension) {
      if (containingFile != null) {
        final PsiDirectory containingDirectory = containingFile.getContainingDirectory();
        if (containingDirectory != null) {
            traverseToRoot(containingDirectory, roots, model);
          }
      }
      else if (psiElement instanceof PsiDirectory) {
        final PsiDirectory psiDirectory = (PsiDirectory)psiElement;

        if (!roots.contains(psiDirectory.getVirtualFile())) {
          PsiDirectory parentDirectory = psiDirectory.getParentDirectory();

          if (parentDirectory == null) {
            VirtualFile jar = PathUtil.getLocalFile(psiDirectory.getVirtualFile());
            if (ProjectRootManager.getInstance(myProject).getFileIndex().isInContent(jar)) {
              parentDirectory = PsiManager.getInstance(myProject).findDirectory(jar.getParent());
            }
          }


          if (parentDirectory != null) {
            traverseToRoot(parentDirectory, roots, model);
          }
        }
      }
      else if (psiElement instanceof PsiFileSystemItem) {
        final VirtualFile virtualFile = ((PsiFileSystemItem)psiElement).getVirtualFile();
        if (virtualFile == null) return;
        final PsiManager psiManager = PsiManager.getInstance(myProject);
        if (virtualFile.isDirectory()) {
          resultElement =  psiManager.findDirectory(virtualFile);
        }
        else {
          resultElement =  psiManager.findFile(virtualFile);
        }
        if (resultElement == null) return;
        final VirtualFile parentVFile = virtualFile.getParent();
        if (parentVFile != null && !roots.contains(parentVFile)) {
          final PsiDirectory parentDirectory = psiManager.findDirectory(parentVFile);
          if (parentDirectory != null) {
            traverseToRoot(parentDirectory, roots, model);
          }
        }
      }
    }

    model.add(resultElement);
  }

  private static PsiElement getOriginalElement(PsiElement psiElement) {
    final PsiElement originalElement = psiElement.getOriginalElement();
    return !(psiElement instanceof PsiCompiledElement) && originalElement instanceof PsiCompiledElement ? psiElement : originalElement;
  }


  protected boolean hasChildren(Object object) {
    if (!isValid(object)) return false;

    return !getChildren(object).isEmpty();
  }

  //to avoid the following situation: element was taken from NavBarPanel via data context and all left children
  // were truncated by traverseToRoot
  public void setChanged(boolean changed) {
    myChanged = changed;
  }

  @SuppressWarnings({"SimplifiableIfStatement"})
  static boolean isValid(final Object object) {
    if (object instanceof Project) {
      return !((Project)object).isDisposed();
    }
    if (object instanceof Module) {
      return !((Module)object).isDisposed();
    }
    if (object instanceof PsiElement) {
      return ApplicationManager.getApplication().runReadAction(
          new Computable<Boolean>() {
            @Override
            public Boolean compute() {
              return ((PsiElement)object).isValid();
            }
          }
      ).booleanValue();
    }
    return object != null;
  }

  public static void getDirectoryChildren(final PsiDirectory psiDirectory, final Object rootElement, final List<Object> result) {
    final ModuleFileIndex moduleFileIndex =
      rootElement instanceof Module ? ModuleRootManager.getInstance((Module)rootElement).getFileIndex() : null;
    final PsiElement[] children = psiDirectory.getChildren();
    for (PsiElement child : children) {
      if (child != null && child.isValid()) {
        if (moduleFileIndex != null) {
          final VirtualFile virtualFile = PsiUtilBase.getVirtualFile(child);
          if (virtualFile != null && !moduleFileIndex.isInContent(virtualFile)) continue;
        }
        result.add(normalize(child));
      }
    }
  }

  @Nullable
  private static PsiElement normalize(PsiElement child) {
    if (child == null) return null;
    for (NavBarModelExtension modelExtension : Extensions.getExtensions(NavBarModelExtension.EP_NAME)) {
      child = modelExtension.adjustElement(child);
      if (child == null ) return null;
    }
    return child;
  }

  protected List<Object> getChildren(final Object object) {
    if (!isValid(object)) return new ArrayList<Object>();
    final List<Object> result = new ArrayList<Object>();
    final Object rootElement = size() > 1 ? getElement(1) : null;
    if (!(object instanceof Project) && rootElement instanceof Module && ((Module)rootElement).isDisposed()) return result;
    if (object instanceof Project) {
      ContainerUtil.addAll(result, getProjectChildren((Project)object));
    }
    else if (object instanceof Module) {
      ContainerUtil.addAll(result, getModuleChildren((Module)object));
    }
    else if (object instanceof PsiDirectoryContainer) {
      final PsiDirectoryContainer psiPackage = (PsiDirectoryContainer)object;
      final PsiDirectory[] psiDirectories = ApplicationManager.getApplication().runReadAction(
          new Computable<PsiDirectory[]>() {
            @Override
            public PsiDirectory[] compute() {
              return rootElement instanceof Module
                                            ? psiPackage.getDirectories(GlobalSearchScope.moduleScope((Module)rootElement))
                                            : psiPackage.getDirectories();
            }
          }
      );
      for (final PsiDirectory psiDirectory : psiDirectories) {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
            @Override
            public void run(){
              getDirectoryChildren(psiDirectory, rootElement, result);
            }
        });
      }
    }
    else if (object instanceof PsiDirectory) {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
          @Override
          public void run(){
              getDirectoryChildren((PsiDirectory)object, rootElement, result);
          }
      });

    }
    else if (object instanceof PsiFileSystemItem) {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
          @Override
          public void run() {
            ((PsiFileSystemItem)object).processChildren(new PsiFileSystemItemProcessor() {
              @Override
              public boolean acceptItem(String name, boolean isDirectory) {
                return true;
              }

              @Override
              public boolean execute(@NotNull PsiFileSystemItem element) {
                result.add(element);
                return true;
              }
            });
          }
      });
    }
    Collections.sort(result, new SiblingsComparator());
    return result;
  }

  private static Module[] getProjectChildren(final Project object) {
    return ApplicationManager.getApplication().runReadAction(
      new Computable<Module[]>() {
        @Override
        public Module[] compute() {
          return ModuleManager.getInstance(object).getModules();
        }
      }
    );
  }

  private List<Object> getModuleChildren(Module module) {
    if (module.isDisposed()) return Collections.emptyList();

    final PsiManager psiManager = PsiManager.getInstance(myProject);
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    VirtualFile[] roots = moduleRootManager.getContentRoots();
    List<Object> result = new ArrayList<Object>(roots.length);
    for (final VirtualFile root : roots) {
      final PsiDirectory psiDirectory = ApplicationManager.getApplication().runReadAction(
        new Computable<PsiDirectory>() {
          @Override
          public PsiDirectory compute() {
            return psiManager.findDirectory(root);
          }
        }
      );
      if (psiDirectory != null) {
        result.add(psiDirectory);
      }
    }
    return result;
  }

  public Object get(final int index) {
    return myModel.get(index);
  }

  public int indexOf(Object value) {
    return myModel.indexOf(value);
  }

  public void setSelectedIndex(final int selectedIndex) {
    if (mySelectedIndex != selectedIndex) {
      mySelectedIndex = selectedIndex;
      myNotificator.selectionChanged();
    }
  }

  public void setFixedComponent(boolean fixedComponent) {
    isFixedComponent = fixedComponent;
  }

  private static final class SiblingsComparator implements Comparator<Object> {
    @Override
    public int compare(final Object o1, final Object o2) {
      final Pair<Integer, String> w1 = getWeightedName(o1);
      final Pair<Integer, String> w2 = getWeightedName(o2);
      if (w1 == null) return w2 == null ? 0 : -1;
      if (w2 == null) return 1;
      if (!w1.first.equals(w2.first)) {
        return -w1.first.intValue() + w2.first.intValue();
      }
      return w1.second.compareToIgnoreCase(w2.second);
    }

    @Nullable
    private static Pair<Integer, String> getWeightedName(Object object) {
      if (object instanceof Module) {
        return Pair.create(5, ((Module)object).getName());
      }
      if (object instanceof PsiDirectoryContainer) {
        return Pair.create(4, ((PsiDirectoryContainer)object).getName());
      }
      else if (object instanceof PsiDirectory) {
        return Pair.create(4, ((PsiDirectory)object).getName());
      }
      if (object instanceof PsiFile) {
        return Pair.create(2, ((PsiFile)object).getName());
      }
      if (object instanceof PsiNamedElement) {
        return Pair.create(3, ((PsiNamedElement)object).getName());
      }
      return null;
    }
  }
}
