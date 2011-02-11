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

package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.favoritesTreeView.actions.AddToFavoritesAction;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.*;
import com.intellij.ide.projectView.impl.nodes.LibraryGroupElement;
import com.intellij.ide.projectView.impl.nodes.NamedLibraryElement;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.ArrayUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class FavoritesManager implements ProjectComponent, JDOMExternalizable {
  // fav list name -> list of (root: root url, root class)
  private final Map<String, LinkedHashSet<Pair<AbstractUrl,String>>> myName2FavoritesRoots =
    new LinkedHashMap<String, LinkedHashSet<Pair<AbstractUrl, String>>>();
  private final Project myProject;
  private final List<FavoritesListener> myListeners = new ArrayList<FavoritesListener>();
  public interface FavoritesListener {
    void rootsChanged(String listName);
    void listAdded(String listName);
    void listRemoved(String listName);
  }
  private final FavoritesListener fireListeners = new FavoritesListener() {
    public void rootsChanged(String listName) {
      FavoritesListener[] listeners = myListeners.toArray(new FavoritesListener[myListeners.size()]);
      for (FavoritesListener listener : listeners) {
        listener.rootsChanged(listName);
      }
    }

    public void listAdded(String listName) {
      FavoritesListener[] listeners = myListeners.toArray(new FavoritesListener[myListeners.size()]);
      for (FavoritesListener listener : listeners) {
        listener.listAdded(listName);
      }
    }

    public void listRemoved(String listName) {
      FavoritesListener[] listeners = myListeners.toArray(new FavoritesListener[myListeners.size()]);
      for (FavoritesListener listener : listeners) {
        listener.listRemoved(listName);
      }
    }
  };

  public synchronized void addFavoritesListener(FavoritesListener listener) {
    myListeners.add(listener);
  }
  public synchronized void removeFavoritesListener(FavoritesListener listener) {
    myListeners.remove(listener);
  }

  public static FavoritesManager getInstance(Project project) {
    return project.getComponent(FavoritesManager.class);
  }

  public FavoritesManager(Project project) {
    myProject = project;
  }

  @NotNull public String[] getAvailableFavoritesLists(){
    final Set<String> keys = myName2FavoritesRoots.keySet();
    return ArrayUtil.toStringArray(keys);
  }

  public synchronized void createNewList(@NotNull String name){
    myName2FavoritesRoots.put(name, new LinkedHashSet<Pair<AbstractUrl, String>>());
    fireListeners.listAdded(name);
  }

  public synchronized boolean removeFavoritesList(@NotNull String name){
    if (name.equals(myProject.getName())) return false;
    boolean result = myName2FavoritesRoots.remove(name) != null;
    fireListeners.listRemoved(name);
    return result;
  }

  public Collection<Pair<AbstractUrl, String>> getFavoritesListRootUrls(@NotNull String name) {
    return myName2FavoritesRoots.get(name);
  }

  public synchronized boolean addRoots(@NotNull String name, Module moduleContext, @NotNull Object elements) {
    Collection<AbstractTreeNode> nodes = AddToFavoritesAction.createNodes(myProject, moduleContext, elements, true, ViewSettings.DEFAULT);
    return !nodes.isEmpty() && addRoots(name, nodes);
  }

  public boolean addRoots(final String name, final Collection<AbstractTreeNode> nodes) {
    final Collection<Pair<AbstractUrl, String>> list = getFavoritesListRootUrls(name);
    for (AbstractTreeNode node : nodes) {
      final String className = node.getClass().getName();
      final Object value = node.getValue();
      final AbstractUrl url = createUrlByElement(value, myProject);
      if (url != null) {
        list.add(Pair.create(url, className));
      }
    }
    fireListeners.rootsChanged(name);
    return true;
  }

  public synchronized boolean removeRoot(@NotNull String name, @NotNull Object element) {
    AbstractUrl url = createUrlByElement(element, myProject);
    if (url == null) return false;
    Collection<Pair<AbstractUrl, String>> list = getFavoritesListRootUrls(name);
    for (Pair<AbstractUrl, String> pair : list) {
      if (url.equals(pair.getFirst())) {
        list.remove(pair);
        break;
      }
    }
    fireListeners.rootsChanged(name);
    return true;
  }

  public synchronized boolean renameFavoritesList(@NotNull String oldName, @NotNull String newName) {
    LinkedHashSet<Pair<AbstractUrl, String>> list = myName2FavoritesRoots.remove(oldName);
    if (list != null && newName.length() > 0) {
      myName2FavoritesRoots.put(newName, list);
      fireListeners.listRemoved(oldName);
      fireListeners.listAdded(newName);
      return true;
    }
    return false;
  }

  public void initComponent() {
  }

  public void disposeComponent() {}

  public void projectOpened() {
    StartupManager.getInstance(myProject).registerPostStartupActivity(new DumbAwareRunnable() {
      public void run() {
        if (myName2FavoritesRoots.isEmpty()) {
          final String name = myProject.getName();
          createNewList(name);
        }
        final MyRootsChangeAdapter myPsiTreeChangeAdapter = new MyRootsChangeAdapter();

        PsiManager.getInstance(myProject).addPsiTreeChangeListener(myPsiTreeChangeAdapter, myProject);
      }
    });
  }

  public void projectClosed() {
  }

  @NotNull
  public String getComponentName() {
    return "FavoritesManager";
  }

  public void readExternal(Element element) throws InvalidDataException {
    myName2FavoritesRoots.clear();
    for (Object list : element.getChildren(ELEMENT_FAVORITES_LIST)) {
      final String name = ((Element)list).getAttributeValue(ATTRIBUTE_NAME);
      LinkedHashSet<Pair<AbstractUrl, String>> roots = readRoots((Element)list, myProject);
      myName2FavoritesRoots.put(name, roots);
    }
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  @NonNls private static final String CLASS_NAME = "klass";
  @NonNls private static final String FAVORITES_ROOT = "favorite_root";
  @NonNls private static final String ELEMENT_FAVORITES_LIST = "favorites_list";
  @NonNls private static final String ATTRIBUTE_NAME = "name";
  private static LinkedHashSet<Pair<AbstractUrl, String>> readRoots(final Element list, Project project) {
    LinkedHashSet<Pair<AbstractUrl, String>> result = new LinkedHashSet<Pair<AbstractUrl, String>>();
    for (Object favorite : list.getChildren(FAVORITES_ROOT)) {
      final String className = ((Element)favorite).getAttributeValue(CLASS_NAME);
      final AbstractUrl abstractUrl = readUrlFromElement((Element)favorite, project);
      if (abstractUrl != null) {
        result.add(Pair.create(abstractUrl, className));
      }
    }
    return result;
  }

  private static final ArrayList<AbstractUrl> ourAbstractUrlProviders = new ArrayList<AbstractUrl>();
  static {
    ourAbstractUrlProviders.add(new ModuleUrl(null, null));
    ourAbstractUrlProviders.add(new DirectoryUrl(null, null));
    
    ourAbstractUrlProviders.add(new ModuleGroupUrl(null));

    ourAbstractUrlProviders.add(new PsiFileUrl(null, null));
    ourAbstractUrlProviders.add(new LibraryModuleGroupUrl(null));
    ourAbstractUrlProviders.add(new NamedLibraryUrl(null, null));
  }
  @NonNls private static final String ATTRIBUTE_TYPE = "type";
  @NonNls private static final String ATTRIBUTE_URL = "url";
  @NonNls private static final String ATTRIBUTE_MODULE = "module";

  @Nullable
  private static AbstractUrl readUrlFromElement(Element element, Project project) {
    final String type = element.getAttributeValue(ATTRIBUTE_TYPE);
    final String urlValue = element.getAttributeValue(ATTRIBUTE_URL);
    final String moduleName = element.getAttributeValue(ATTRIBUTE_MODULE);

    for(FavoriteNodeProvider nodeProvider: Extensions.getExtensions(FavoriteNodeProvider.EP_NAME, project)) {
      if (nodeProvider.getFavoriteTypeId().equals(type)) {
        return new AbstractUrlFavoriteAdapter(urlValue, moduleName, nodeProvider);
      }
    }

    for (AbstractUrl urlProvider : ourAbstractUrlProviders) {
      AbstractUrl url = urlProvider.createUrl(type, moduleName, urlValue);
      if (url != null) return url;
    }
    return null;
  }


  public void writeExternal(Element element) throws WriteExternalException {
    for (final String name : myName2FavoritesRoots.keySet()) {
      Element list = new Element(ELEMENT_FAVORITES_LIST);
      list.setAttribute(ATTRIBUTE_NAME, name);
      writeRoots(list, myName2FavoritesRoots.get(name));
      element.addContent(list);
    }
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  @Nullable
  private static AbstractUrl createUrlByElement(Object element, final Project project) {
    if (element instanceof SmartPsiElementPointer) element = ((SmartPsiElementPointer)element).getElement();
                                                                                                                                               
    for(FavoriteNodeProvider nodeProvider: Extensions.getExtensions(FavoriteNodeProvider.EP_NAME, project)) {
      String url = nodeProvider.getElementUrl(element);
      if (url != null) {
        return new AbstractUrlFavoriteAdapter(url, nodeProvider.getElementModuleName(element), nodeProvider);
      }
    }

    for (AbstractUrl urlProvider : ourAbstractUrlProviders) {
      AbstractUrl url = urlProvider.createUrlByElement(element);
      if (url != null) return url;
    }
    return null;
  }

  private static void writeRoots(Element element, LinkedHashSet<Pair<AbstractUrl, String>> roots) {
    for (Pair<AbstractUrl, String> root : roots) {
      final AbstractUrl url = root.getFirst();
      if (url == null) continue;
      final Element list = new Element(FAVORITES_ROOT);
      url.write(list);
      list.setAttribute(CLASS_NAME, root.getSecond());
      element.addContent(list);
    }
  }


  public boolean contains(@NotNull String name, @NotNull final VirtualFile vFile){
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    final Set<Boolean> find = new HashSet<Boolean>();
    final ContentIterator contentIterator = new ContentIterator() {
      public boolean processFile(VirtualFile fileOrDir) {
        if (fileOrDir != null && fileOrDir.getPath().equals(vFile.getPath())) {
          find.add(Boolean.TRUE);
        }
        return true;
      }
    };

    Collection<Pair<AbstractUrl, String>> urls = getFavoritesListRootUrls(name);
    for (Pair<AbstractUrl, String> pair : urls) {
      AbstractUrl abstractUrl = pair.getFirst();
      if (abstractUrl == null) {
        continue;
      }
      final Object[] path = abstractUrl.createPath(myProject);
      if (path == null || path.length < 1 || path[0] == null) {
        continue;
      }
      Object element = path[path.length - 1];
      if (element instanceof SmartPsiElementPointer) {
        final VirtualFile virtualFile = PsiUtilBase.getVirtualFile(((SmartPsiElementPointer)element).getElement());
        if (virtualFile == null) continue;
        if (vFile.getPath().equals(virtualFile.getPath())) {
          return true;
        }
        if (!virtualFile.isDirectory()) {
          continue;
        }
        projectFileIndex.iterateContentUnderDirectory(virtualFile, contentIterator);
      }

      if (element instanceof PsiElement) {
        final VirtualFile virtualFile = PsiUtilBase.getVirtualFile((PsiElement)element);
        if (virtualFile == null) continue;
        if (vFile.getPath().equals(virtualFile.getPath())){
          return true;
        }
        if (!virtualFile.isDirectory()){
          continue;
        }
        projectFileIndex.iterateContentUnderDirectory(virtualFile, contentIterator);
      }
      if (element instanceof Module){
        ModuleRootManager.getInstance((Module)element).getFileIndex().iterateContent(contentIterator);
      }
      if (element instanceof LibraryGroupElement){
        final boolean inLibrary =
          ModuleRootManager.getInstance(((LibraryGroupElement)element).getModule()).getFileIndex().isInContent(vFile) &&
          projectFileIndex.isInLibraryClasses(vFile);
        if (inLibrary){
          return true;
        }
      }
      if (element instanceof NamedLibraryElement){
        NamedLibraryElement namedLibraryElement = (NamedLibraryElement)element;
        final VirtualFile[] files = namedLibraryElement.getOrderEntry().getFiles(OrderRootType.CLASSES);
        if (files != null && ArrayUtil.find(files, vFile) > -1){
          return true;
        }
      }
      if (element instanceof ModuleGroup){
        ModuleGroup group = (ModuleGroup) element;
        final Collection<Module> modules = group.modulesInGroup(myProject, true);
        for (Module module : modules) {
          ModuleRootManager.getInstance(module).getFileIndex().iterateContent(contentIterator);
        }
      }


      for(FavoriteNodeProvider provider: Extensions.getExtensions(FavoriteNodeProvider.EP_NAME, myProject)) {
        if (provider.elementContainsFile(element, vFile)) {
          return true;
        }
      }

      if (!find.isEmpty()){
        return true;
      }
    }
    return false;
  }

  private class MyRootsChangeAdapter extends PsiTreeChangeAdapter {
    public void beforeChildMovement(final PsiTreeChangeEvent event) {
      final PsiElement oldParent = event.getOldParent();
      final PsiElement newParent = event.getNewParent();
      final PsiElement child = event.getChild();
      if (newParent instanceof PsiDirectory) {
        final Module module = ModuleUtil.findModuleForPsiElement(newParent);
        if (module == null) return;
        AbstractUrl childUrl = null;
        if (child instanceof PsiFile) {
          childUrl =
            new PsiFileUrl(((PsiDirectory)newParent).getVirtualFile().getUrl() + "/" + ((PsiFile)child).getName(), module.getName());
        }
        else if (child instanceof PsiDirectory) {
          childUrl =
            new DirectoryUrl(((PsiDirectory)newParent).getVirtualFile().getUrl() + "/" + ((PsiDirectory)child).getName(), module.getName());
        }
        for (String listName : myName2FavoritesRoots.keySet()) {
          final LinkedHashSet<Pair<AbstractUrl, String>> roots = myName2FavoritesRoots.get(listName);
          final LinkedHashSet<Pair<AbstractUrl, String>> newRoots = new LinkedHashSet<Pair<AbstractUrl, String>>();
          for (Pair<AbstractUrl, String> root : roots) {
            final Object[] path = root.first.createPath(myProject);
            if (path == null || path.length < 1 || path[0] == null) {
              continue;
            }
            final Object element = path[path.length - 1];
            if (element == child && childUrl != null) {
              newRoots.add(Pair.create(childUrl, root.second));
            }
            else {
              if (element == oldParent) {
                newRoots.add(Pair.create(root.first.createUrlByElement(newParent), root.second));
              }
              newRoots.add(root);
            }
          }
          myName2FavoritesRoots.put(listName, newRoots);
        }
      }
    }

    public void beforePropertyChange(final PsiTreeChangeEvent event) {
      if (event.getPropertyName().equals(PsiTreeChangeEvent.PROP_FILE_NAME) || event.getPropertyName().equals(PsiTreeChangeEvent.PROP_DIRECTORY_NAME)) {
        final PsiElement psiElement = event.getChild();
        if (psiElement instanceof PsiFile || psiElement instanceof PsiDirectory) {
          final Module module = ModuleUtil.findModuleForPsiElement(psiElement);
          if (module == null) return;
          final String url = ((PsiDirectory)psiElement.getParent()).getVirtualFile().getUrl() + "/" + event.getNewValue();
          final AbstractUrl childUrl = psiElement instanceof PsiFile ? new PsiFileUrl(url, module.getName()) : new DirectoryUrl(url, module.getName());
          for (String listName : myName2FavoritesRoots.keySet()) {
            final LinkedHashSet<Pair<AbstractUrl, String>> roots = myName2FavoritesRoots.get(listName);
            final LinkedHashSet<Pair<AbstractUrl, String>> newRoots = new LinkedHashSet<Pair<AbstractUrl, String>>();
            for (Pair<AbstractUrl, String> root : roots) {
              final Object[] path = root.first.createPath(myProject);
              if (path == null || path.length < 1 || path[0] == null) {
                continue;
              }
              final Object element = path[path.length - 1];
              if (element == psiElement && psiElement instanceof PsiFile) {
                newRoots.add(Pair.create(childUrl, root.second));
              } else {
                newRoots.add(root);
              }
            }
            myName2FavoritesRoots.put(listName, newRoots);
          }
        }
      }
    }
  }
}
