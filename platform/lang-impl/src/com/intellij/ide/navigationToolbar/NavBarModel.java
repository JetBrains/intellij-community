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
 * User: anna
 * Date: 22-Mar-2007
 */
package com.intellij.ide.navigationToolbar;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.projectView.impl.ProjectRootsUtil;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.diagnostic.Logger;
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
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;
import java.util.List;

public class NavBarModel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.navigationToolbar.NavBarModel");

  private List<Object> myModel = Collections.emptyList();
  private int mySelectedIndex;
  private final Project myProject;

  private final static SimpleTextAttributes WOLFED = new SimpleTextAttributes(null, null, Color.red, SimpleTextAttributes.STYLE_WAVED);
  private final NavBarModelListener myNotificator;

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
    if (LaterInvocator.isInModalContext()) return;
    PsiElement psiElement = LangDataKeys.PSI_FILE.getData(dataContext);
    if (psiElement == null) {
      psiElement = LangDataKeys.PSI_ELEMENT.getData(dataContext);
    }

    psiElement = normalize(psiElement);
    if (psiElement != null && psiElement.isValid()) {
      updateModel(psiElement);
    }
    else {
      if (UISettings.getInstance().SHOW_NAVIGATION_BAR) return;

      Object moduleOrProject = LangDataKeys.MODULE.getData(dataContext);
      if (moduleOrProject == null) {
        moduleOrProject = LangDataKeys.PROJECT.getData(dataContext);
      }

      if (moduleOrProject != null) {
        setModel(Collections.singletonList(moduleOrProject));
      }
    }

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

    final List<Object> updatedModel = new ArrayList<Object>();

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        traverseToRoot(psiElement, roots, updatedModel);
      }
    });

    setModel(updatedModel);
  }

  private void setModel(List<Object> model) {
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
    psiElement = psiElement.getOriginalElement();
    PsiElement resultElement = psiElement;
    if (containingFile != null) {
      for (final NavBarModelExtension modelExtension : Extensions.getExtensions(NavBarModelExtension.EP_NAME)) {
        resultElement = modelExtension.adjustElement(resultElement);
      }
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
    else {
      final PsiElement el = psiElement;
      for (final NavBarModelExtension modelExtension : Extensions.getExtensions(NavBarModelExtension.EP_NAME)) {
        final PsiElement parent = modelExtension.getParent(el);
        if (parent != null) {
          traverseToRoot(parent, roots, model);
        }
      }
    }
    model.add(resultElement);
  }


  protected boolean hasChildren(Object object) {
    if (!checkValid(object)) return false;

    return !calcElementChildren(object).isEmpty();
  }

  @SuppressWarnings({"SimplifiableIfStatement"})
  static boolean checkValid(final Object object) {
    if (object instanceof Project) {
      return !((Project)object).isDisposed();
    }
    if (object instanceof Module) {
      return !((Module)object).isDisposed();
    }
    if (object instanceof PsiElement) {
      return ApplicationManager.getApplication().runReadAction(
          new Computable<Boolean>() {
            public Boolean compute() {
              return ((PsiElement)object).isValid();
            }
          }
      ).booleanValue();
    }
    return object != null;
  }

  @NotNull
  protected static String getPresentableText(final Object object, Window window) {
    if (!checkValid(object)) return IdeBundle.message("node.structureview.invalid");
    for (NavBarModelExtension modelExtension : Extensions.getExtensions(NavBarModelExtension.EP_NAME)) {
      String text = modelExtension.getPresentableText(object);
      if (text != null) {
        boolean truncated = false;
        if (window != null) {
          final int windowWidth = window.getWidth();
          while (window.getFontMetrics(window.getFont()).stringWidth(text) + 100 > windowWidth && text.length() > 10) {
            text = text.substring(0, text.length() - 10);
            truncated = true;
          }
        }
        return text + (truncated ? "..." : "");
      }
    }
    LOG.error("Failed to find navbar presentable text for " + object);
    return object.toString();
  }


  protected SimpleTextAttributes getTextAttributes(final Object object, final boolean selected) {
    if (!checkValid(object)) return SimpleTextAttributes.REGULAR_ATTRIBUTES;
    if (object instanceof PsiElement) {
      if (!ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
        public Boolean compute() {
          return ((PsiElement)object).isValid();
        }
      }).booleanValue()) return SimpleTextAttributes.GRAYED_ATTRIBUTES;
      PsiFile psiFile = ((PsiElement)object).getContainingFile();
      if (psiFile != null) {
        final VirtualFile virtualFile = psiFile.getVirtualFile();
        return new SimpleTextAttributes(null, selected ? null : FileStatusManager.getInstance(myProject).getStatus(virtualFile).getColor(),
                                        Color.red, WolfTheProblemSolver.getInstance(myProject).isProblemFile(virtualFile)
                                                   ? SimpleTextAttributes.STYLE_WAVED
                                                   : SimpleTextAttributes.STYLE_PLAIN);
      }
      else {
        if (object instanceof PsiDirectory) {
          VirtualFile vDir = ((PsiDirectory)object).getVirtualFile();
          if (vDir.getParent() == null || ProjectRootsUtil.isModuleContentRoot(vDir, myProject)) {
            return SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES;
          }
        }
        
        if (NavBarPanel.wolfHasProblemFilesBeneath((PsiElement)object)) {
          return WOLFED;
        }
      }
    }
    else if (object instanceof Module) {
      if (WolfTheProblemSolver.getInstance(myProject).hasProblemFilesBeneath((Module)object)) {
        return WOLFED;
      }

    }
    else if (object instanceof Project) {
      final Project project = (Project)object;
      final Module[] modules = ApplicationManager.getApplication().runReadAction(
          new Computable<Module[]>() {
            public Module[] compute() {
              return  ModuleManager.getInstance(project).getModules();
            }
          }
      );
      for (Module module : modules) {
        if (WolfTheProblemSolver.getInstance(project).hasProblemFilesBeneath(module)) {
          return WOLFED;
        }
      }
    }
    return SimpleTextAttributes.REGULAR_ATTRIBUTES;
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

  List<Object> calcElementChildren(final Object object) {
    if (!checkValid(object)) return new ArrayList<Object>();
    final List<Object> result = new ArrayList<Object>();
    final Object rootElement = size() > 1 ? getElement(1) : null;
    if (!(object instanceof Project) && rootElement instanceof Module && ((Module)rootElement).isDisposed()) return result;
    final PsiManager psiManager = PsiManager.getInstance(myProject);
    if (object instanceof Project) {
      ContainerUtil.addAll(result, ApplicationManager.getApplication().runReadAction(
        new Computable<Module[]>() {
          public Module[] compute() {
            return ModuleManager.getInstance((Project)object).getModules();
          }
        }
      ));
    }
    else if (object instanceof Module) {
      Module module = (Module)object;
      if (!module.isDisposed()) {
        ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
        VirtualFile[] roots = moduleRootManager.getContentRoots();
        for (final VirtualFile root : roots) {
          final PsiDirectory psiDirectory = ApplicationManager.getApplication().runReadAction(
              new Computable<PsiDirectory>() {
                public PsiDirectory compute() {
                  return psiManager.findDirectory(root);
                }
              }
          );
          if (psiDirectory != null) {
            result.add(psiDirectory);
          }
        }
      }
    }
    else if (object instanceof PsiDirectoryContainer) {
      final PsiDirectoryContainer psiPackage = (PsiDirectoryContainer)object;
      final PsiDirectory[] psiDirectories = ApplicationManager.getApplication().runReadAction(
          new Computable<PsiDirectory[]>() {
            public PsiDirectory[] compute() {
              return rootElement instanceof Module
                                            ? psiPackage.getDirectories(GlobalSearchScope.moduleScope((Module)rootElement))
                                            : psiPackage.getDirectories();
            }
          }
      );
      for (final PsiDirectory psiDirectory : psiDirectories) {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
            public void run(){
              getDirectoryChildren(psiDirectory, rootElement, result);
            }
        });
      }
    }
    else if (object instanceof PsiDirectory) {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run(){
              getDirectoryChildren((PsiDirectory)object, rootElement, result);
          }
      });

    }
    Collections.sort(result, new SiblingsComparator());
    return result;
  }

  public Object get(final int index) {
    return myModel.get(index);
  }

  public void setSelectedIndex(final int selectedIndex) {
    if (mySelectedIndex != selectedIndex) {
      mySelectedIndex = selectedIndex;
      myNotificator.selectionChanged();
    }
  }

  private static final class SiblingsComparator implements Comparator<Object> {
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
