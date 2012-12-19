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

package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.favoritesTreeView.actions.AddToFavoritesAction;
import com.intellij.ide.projectView.impl.*;
import com.intellij.ide.projectView.impl.nodes.LibraryGroupElement;
import com.intellij.ide.projectView.impl.nodes.NamedLibraryElement;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vcs.ObjectsConvertor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.TreeItem;
import com.intellij.util.containers.Convertor;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreeCellRenderer;
import java.util.*;

public class FavoritesManager implements ProjectComponent, JDOMExternalizable {
  // fav list name -> list of (root: root url, root class)
  private final Map<String, List<TreeItem<Pair<AbstractUrl, String>>>> myName2FavoritesRoots =
    new LinkedHashMap<String, List<TreeItem<Pair<AbstractUrl, String>>>>();
  private final Set<String> myReadOnlyLists = new HashSet<String>();
  private final Set<String> myAllowsTreeLists = new HashSet<String>();
  private final Project myProject;
  private final List<FavoritesListener> myListeners = new ArrayList<FavoritesListener>();
  private final FavoritesViewSettings myViewSettings = new FavoritesViewSettings();
  private final Map<String, FavoritesListProvider.Operation> myEditHandlers = new HashMap<String, FavoritesListProvider.Operation>();
  private final Map<String, FavoritesListProvider.Operation> myAddHandlers = new HashMap<String, FavoritesListProvider.Operation>();
  private final Map<String, FavoritesListProvider.Operation> myDeleteHandlers = new HashMap<String, FavoritesListProvider.Operation>();
  private final Map<String, TreeCellRenderer> myCustomRenderers = new HashMap<String, TreeCellRenderer>();
  private final Map<String, Comparator<FavoritesTreeNodeDescriptor>> myComparators = new HashMap<String, Comparator<FavoritesTreeNodeDescriptor>>();

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

  public synchronized FavoritesListProvider.Operation getCustomAdd(final String name) {
    return myAddHandlers.get(name);
  }

  public synchronized FavoritesListProvider.Operation getCustomEdit(final String name) {
    return myEditHandlers.get(name);
  }

  public synchronized FavoritesListProvider.Operation getCustomDelete(final String name) {
    return myDeleteHandlers.get(name);
  }

  public void removeRootByIndexes(String name, List<Integer> elementsIndexes) {
    List<TreeItem<Pair<AbstractUrl, String>>> list = getFavoritesListRootUrls(name);
    assert list != null;
    for (Integer index : elementsIndexes.subList(0, elementsIndexes.size() - 1)) {
      assert index >= 0 && index < list.size();
      final TreeItem<Pair<AbstractUrl, String>> item = list.get(index);
      list = item.getChildren();
    }
    assert list != null && ! list.isEmpty();
    list.remove(elementsIndexes.get(elementsIndexes.size() - 1).intValue());
    fireListeners.rootsChanged(name);
  }

  public static FavoritesManager getInstance(Project project) {
    return project.getComponent(FavoritesManager.class);
  }

  public FavoritesManager(Project project) {
    myProject = project;
  }

  @NotNull public String[] getAvailableFavoritesListNames(){
    final Set<String> keys = myName2FavoritesRoots.keySet();
    return ArrayUtil.toStringArray(keys);
  }

  public synchronized boolean allowsTree(@NotNull final String name) {
    return myAllowsTreeLists.contains(name);
  }

  public synchronized void createNewList(@NotNull String name, boolean readOnly, boolean allowsTree){
    myName2FavoritesRoots.put(name, new ArrayList<TreeItem<Pair<AbstractUrl, String>>>());
    if (readOnly) {
      myReadOnlyLists.add(name);
    }
    if (allowsTree) {
      myAllowsTreeLists.add(name);
    }
    fireListeners.listAdded(name);
  }

  public synchronized void fireListeners(@NotNull final String listName) {
    fireListeners.rootsChanged(listName);
  }

  public FavoritesViewSettings getViewSettings() {
    return myViewSettings;
  }

  public synchronized boolean removeFavoritesList(@NotNull String name){
    if (myReadOnlyLists.contains(name)) return false;
    boolean result = myName2FavoritesRoots.remove(name) != null;
    fireListeners.listRemoved(name);
    return result;
  }

  @NotNull
  public List<TreeItem<Pair<AbstractUrl, String>>> getFavoritesListRootUrls(@NotNull String name) {
    final List<TreeItem<Pair<AbstractUrl, String>>> pairs = myName2FavoritesRoots.get(name);
    return pairs == null ? new ArrayList<TreeItem<Pair<AbstractUrl, String>>>() : pairs;
  }

  public synchronized boolean addRoots(@NotNull String name, Module moduleContext, @NotNull Object elements) {
    Collection<AbstractTreeNode> nodes = AddToFavoritesAction.createNodes(myProject, moduleContext, elements, true, getViewSettings());
    return !nodes.isEmpty() && addRoots(name, nodes);
  }

  public synchronized Comparator<FavoritesTreeNodeDescriptor> getCustomComparator(@NotNull final String name) {
    return myComparators.get(name);
  }

  private Pair<AbstractUrl, String> createPairForNode(AbstractTreeNode node) {
    final String className = node.getClass().getName();
    final Object value = node.getValue();
    final AbstractUrl url = createUrlByElement(value, myProject);
    if (url == null) return null;
    return Pair.create(url, className);
  }

  public boolean addRoots(final String name, final Collection<AbstractTreeNode> nodes) {
    final Collection<TreeItem<Pair<AbstractUrl, String>>> list = getFavoritesListRootUrls(name);

    final HashSet<AbstractUrl> set = new HashSet<AbstractUrl>(ObjectsConvertor.convert(list, new Convertor<TreeItem<Pair<AbstractUrl, String>>, AbstractUrl>() {
      @Override
      public AbstractUrl convert(TreeItem<Pair<AbstractUrl, String>> o) {
        return o.getData().getFirst();
      }
    }));
    for (AbstractTreeNode node : nodes) {
      final Pair<AbstractUrl, String> pair = createPairForNode(node);
      if (pair != null) {
        if (set.contains(pair.getFirst())) continue;
        final TreeItem<Pair<AbstractUrl, String>> treeItem = new TreeItem<Pair<AbstractUrl, String>>(pair);
        list.add(treeItem);
        set.add(pair.getFirst());
        appendChildNodes(node, treeItem);
      }
    }
    fireListeners.rootsChanged(name);
    return true;
  }

  private void appendChildNodes(AbstractTreeNode node, TreeItem<Pair<AbstractUrl, String>> treeItem) {
    final Collection<? extends AbstractTreeNode> children = node.getChildren();
    for (AbstractTreeNode child : children) {
      final TreeItem<Pair<AbstractUrl, String>> childTreeItem = new TreeItem<Pair<AbstractUrl, String>>(createPairForNode(child));
      treeItem.addChild(childTreeItem);
      appendChildNodes(child, childTreeItem);
    }
  }

  public synchronized boolean addRoot(@NotNull String name,
                                      @NotNull List<AbstractTreeNode> parentElements,
                                      final AbstractTreeNode newElement,
                                      @Nullable AbstractTreeNode sibling) {
    final List<TreeItem<Pair<AbstractUrl, String>>> items = myName2FavoritesRoots.get(name);
    if (items == null) return false;
    AbstractUrl url = createUrlByElement(newElement.getValue(), myProject);
    if (url == null) return false;
    final TreeItem<Pair<AbstractUrl, String>> newItem =
      new TreeItem<Pair<AbstractUrl, String>>(Pair.create(url, newElement.getClass().getName()));

    if (parentElements.isEmpty()) {
      // directly to list
      if (sibling != null) {
        TreeItem<Pair<AbstractUrl, String>> after = null;
        AbstractUrl siblingUrl = createUrlByElement(sibling.getValue(), myProject);
        int idx = -1;
        for (int i = 0; i < items.size(); i++) {
          TreeItem<Pair<AbstractUrl, String>> item = items.get(i);
          if (item.getData().getFirst().equals(siblingUrl)) {
            idx = i;
            break;
          }
        }
        if (idx != -1) {
          items.add(idx, newItem);
        } else {
          items.add(newItem);
        }
      } else {
        items.add(newItem);
      }

      fireListeners.rootsChanged(name);
      return true;
    }

    Collection<TreeItem<Pair<AbstractUrl, String>>> list = items;
    TreeItem<Pair<AbstractUrl, String>> item = null;
    for (AbstractTreeNode obj : parentElements) {
      AbstractUrl objUrl = createUrlByElement(obj.getValue(), myProject);
      item = findNextItem(objUrl, list);
      if (item == null) return false;
      list = item.getChildren();
    }

    if (sibling != null) {
      TreeItem<Pair<AbstractUrl, String>> after = null;
      AbstractUrl siblingUrl = createUrlByElement(sibling.getValue(), myProject);
      for (TreeItem<Pair<AbstractUrl, String>> treeItem : list) {
        if (treeItem.getData().getFirst().equals(siblingUrl)) {
          after = treeItem;
          break;
        }
      }
      if (after == null) {
        item.addChild(newItem);
      } else {
        item.addChildAfter(newItem, after);
      }
    } else {
      item.addChild(newItem);
    }
    fireListeners.rootsChanged(name);
    return true;
  }

  public synchronized boolean editRoot(@NotNull String name, @NotNull List<Integer> elementsIndexes, final AbstractTreeNode newElement) {
    List<TreeItem<Pair<AbstractUrl, String>>> list = getFavoritesListRootUrls(name);
    assert list != null;
    for (Integer index : elementsIndexes.subList(0, elementsIndexes.size() - 1)) {
      assert index >= 0 && index < list.size();
      final TreeItem<Pair<AbstractUrl, String>> item = list.get(index);
      list = item.getChildren();
    }
    assert list != null && ! list.isEmpty();
    final Object value = newElement.getValue();
    final AbstractUrl urlByElement = createUrlByElement(value, myProject);
    if (urlByElement == null) return false;
    list.set(elementsIndexes.get(elementsIndexes.size() - 1).intValue(), new TreeItem<Pair<AbstractUrl, String>>(Pair.create(urlByElement, newElement.getClass().getName())));
    return true;
  }

  private <T> boolean findListToRemoveFrom(@NotNull String name, @NotNull final List<T> elements,
                                                                       final Convertor<T, AbstractUrl> convertor) {
    Collection<TreeItem<Pair<AbstractUrl, String>>> list = getFavoritesListRootUrls(name);
    if (elements.size() > 1) {
      final List<T> sublist = elements.subList(0, elements.size() - 1);
      for (T obj : sublist) {
        AbstractUrl objUrl = convertor.convert(obj);
        final TreeItem<Pair<AbstractUrl, String>> item = findNextItem(objUrl, list);
        if (item == null || item.getChildren() == null) return false;
        list = item.getChildren();
      }
    }

    TreeItem<Pair<AbstractUrl, String>> found = null;
    AbstractUrl url = convertor.convert(elements.get(elements.size() - 1));
    if (url == null) return false;
    for (TreeItem<Pair<AbstractUrl, String>> pair : list) {
      if (url.equals(pair.getData().getFirst())) {
        found = pair;
        break;
      }
    }

    if (found != null) {
      list.remove(found);
      fireListeners.rootsChanged(name);
      return true;
    }
    return false;
  }

  public synchronized boolean removeRoot(@NotNull String name, @NotNull List<AbstractTreeNode> elements) {
    final Convertor<AbstractTreeNode, AbstractUrl> convertor = new Convertor<AbstractTreeNode, AbstractUrl>() {
      @Override
      public AbstractUrl convert(AbstractTreeNode obj) {
        return createUrlByElement(obj.getValue(), myProject);
      }
    };
    boolean result = true;
    for (AbstractTreeNode element : elements) {
      final List<AbstractTreeNode> path = TaskDefaultFavoriteListProvider.getPathToUsualNode(element);
      result &= findListToRemoveFrom(name, path.subList(1, path.size()), convertor);
    }
    return result;
  }

  private TreeItem<Pair<AbstractUrl, String>> findNextItem(AbstractUrl url, Collection<TreeItem<Pair<AbstractUrl, String>>> list) {
    for (TreeItem<Pair<AbstractUrl, String>> pair : list) {
      if (url.equals(pair.getData().getFirst())) {
        return pair;
      }
    }
    return null;
  }

  public synchronized boolean renameFavoritesList(@NotNull String oldName, @NotNull String newName) {
    if (myReadOnlyLists.contains(oldName)) return false;
    List<TreeItem<Pair<AbstractUrl, String>>> list = myName2FavoritesRoots.remove(oldName);
    if (list != null && newName.length() > 0) {
      myName2FavoritesRoots.put(newName, list);
      fireListeners.listRemoved(oldName);
      fireListeners.listAdded(newName);
      return true;
    }
    return false;
  }

  public synchronized boolean isReadOnly(@NotNull final String listName) {
    return myReadOnlyLists.contains(listName);
  }

  public void initComponent() {
  }

  public void disposeComponent() {}

  public void projectOpened() {
    StartupManager.getInstance(myProject).registerPostStartupActivity(new DumbAwareRunnable() {
      public void run() {
        final FavoritesListProvider[] extensions = Extensions.getExtensions(FavoritesListProvider.EP_NAME, myProject);
        for (FavoritesListProvider extension : extensions) {
          final String name = extension.getListName(myProject);
          if (! myName2FavoritesRoots.containsKey(name)) {
            createNewList(name, extension.canBeRemoved(), extension.isTreeLike());
          } else if (! myReadOnlyLists.contains(name) && ! extension.canBeRemoved()) {
            myReadOnlyLists.add(name);
          }
          final FavoritesListProvider.Operation addOperation = extension.getCustomAddOperation();
          if (! myAddHandlers.containsKey(name)) {
            myAddHandlers.put(name, addOperation);
          }
          final FavoritesListProvider.Operation editOperation = extension.getCustomEditOperation();
          if (! myEditHandlers.containsKey(name)) {
            myEditHandlers.put(name, editOperation);
          }
          final FavoritesListProvider.Operation deleteOperation = extension.getCustomDeleteOperation();
          if (! myDeleteHandlers.containsKey(name)) {
            myDeleteHandlers.put(name, deleteOperation);
          }
          final TreeCellRenderer treeCellRenderer = extension.getTreeCellRenderer();
          if (treeCellRenderer != null && ! myCustomRenderers.containsKey(name)) {
            myCustomRenderers.put(name, treeCellRenderer);
          }
          final Comparator<FavoritesTreeNodeDescriptor> comparator = extension.getNodeDescriptorComparator();
          if (comparator != null && ! myComparators.containsKey(name)) {
            myComparators.put(name, comparator);
          }
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

  public synchronized TreeCellRenderer getCustomRenderer(@NotNull final String name) {
    return myCustomRenderers.get(name);
  }

  public void readExternal(Element element) throws InvalidDataException {
    myName2FavoritesRoots.clear();
    for (Object list : element.getChildren(ELEMENT_FAVORITES_LIST)) {
      final String name = ((Element)list).getAttributeValue(ATTRIBUTE_NAME);
      List<TreeItem<Pair<AbstractUrl, String>>> roots = readRoots((Element)list, myProject);
      myName2FavoritesRoots.put(name, roots);
    }
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  @NonNls private static final String CLASS_NAME = "klass";
  @NonNls private static final String FAVORITES_ROOT = "favorite_root";
  @NonNls private static final String ELEMENT_FAVORITES_LIST = "favorites_list";
  @NonNls private static final String ATTRIBUTE_NAME = "name";
  private static List<TreeItem<Pair<AbstractUrl, String>>> readRoots(final Element list, Project project) {
    List<TreeItem<Pair<AbstractUrl, String>>> result = new ArrayList<TreeItem<Pair<AbstractUrl, String>>>();
    readFavoritesOneLevel(list, project, result);
    return result;
  }

  private static void readFavoritesOneLevel(Element list, Project project, Collection<TreeItem<Pair<AbstractUrl, String>>> result) {
    final List listChildren = list.getChildren(FAVORITES_ROOT);
    if (listChildren == null || listChildren.isEmpty()) return;

    for (Object favorite : listChildren) {
      final Element favoriteElement = (Element)favorite;
      final String className = favoriteElement.getAttributeValue(CLASS_NAME);
      final AbstractUrl abstractUrl = readUrlFromElement(favoriteElement, project);
      if (abstractUrl != null) {
        final TreeItem<Pair<AbstractUrl, String>> treeItem = new TreeItem<Pair<AbstractUrl, String>>(Pair.create(abstractUrl, className));
        result.add(treeItem);
        readFavoritesOneLevel(favoriteElement, project, treeItem.getChildren());
      }
    }
  }

  private static final ArrayList<AbstractUrl> ourAbstractUrlProviders = new ArrayList<AbstractUrl>();
  static {
    ourAbstractUrlProviders.add(new ModuleUrl(null, null));
    ourAbstractUrlProviders.add(new DirectoryUrl(null, null));
    
    ourAbstractUrlProviders.add(new ModuleGroupUrl(null));

    ourAbstractUrlProviders.add(new PsiFileUrl(null));
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
  public static AbstractUrl createUrlByElement(Object element, final Project project) {
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

  private static void writeRoots(Element element, Collection<TreeItem<Pair<AbstractUrl, String>>> roots) {
    for (TreeItem<Pair<AbstractUrl, String>> root : roots) {
      final AbstractUrl url = root.getData().getFirst();
      if (url == null) continue;
      final Element list = new Element(FAVORITES_ROOT);
      url.write(list);
      list.setAttribute(CLASS_NAME, root.getData().getSecond());
      element.addContent(list);
      final List<TreeItem<Pair<AbstractUrl, String>>> children = root.getChildren();
      if (children != null && ! children.isEmpty()) {
        writeRoots(list, children);
      }
    }
  }

  // currently only one level here..
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

    Collection<TreeItem<Pair<AbstractUrl, String>>> urls = getFavoritesListRootUrls(name);
    for (TreeItem<Pair<AbstractUrl, String>> pair : urls) {
      AbstractUrl abstractUrl = pair.getData().getFirst();
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
        final VirtualFile[] files = namedLibraryElement.getOrderEntry().getRootFiles(OrderRootType.CLASSES);
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

  private void iterateTreeItems(final Collection<TreeItem<Pair<AbstractUrl, String>>> coll, Consumer<TreeItem<Pair<AbstractUrl, String>>> consumer) {
    final ArrayDeque<TreeItem<Pair<AbstractUrl, String>>> queue = new ArrayDeque<TreeItem<Pair<AbstractUrl, String>>>();
    queue.addAll(coll);
    while (! queue.isEmpty()) {
      final TreeItem<Pair<AbstractUrl, String>> item = queue.removeFirst();
      consumer.consume(item);
      final List<TreeItem<Pair<AbstractUrl, String>>> children = item.getChildren();
      if (children != null && ! children.isEmpty()) {
        queue.addAll(children);
      }
    }
  }

  private class MyRootsChangeAdapter extends PsiTreeChangeAdapter {
    public void beforeChildMovement(@NotNull final PsiTreeChangeEvent event) {
      final PsiElement oldParent = event.getOldParent();
      final PsiElement newParent = event.getNewParent();
      final PsiElement child = event.getChild();
      if (newParent instanceof PsiDirectory) {
        final Module module = ModuleUtil.findModuleForPsiElement(newParent);
        if (module == null) return;
        AbstractUrl childUrl = null;
        if (child instanceof PsiFile) {
          childUrl = new PsiFileUrl(((PsiDirectory)newParent).getVirtualFile().getUrl() + "/" + ((PsiFile)child).getName());
        }
        else if (child instanceof PsiDirectory) {
          childUrl =
            new DirectoryUrl(((PsiDirectory)newParent).getVirtualFile().getUrl() + "/" + ((PsiDirectory)child).getName(), module.getName());
        }

        for (String listName : myName2FavoritesRoots.keySet()) {
          final List<TreeItem<Pair<AbstractUrl, String>>> roots = myName2FavoritesRoots.get(listName);
          final AbstractUrl finalChildUrl = childUrl;
          iterateTreeItems(roots, new Consumer<TreeItem<Pair<AbstractUrl, String>>>() {
            @Override
            public void consume(TreeItem<Pair<AbstractUrl, String>> item) {
              final Pair<AbstractUrl, String> root = item.getData();
              final Object[] path = root.first.createPath(myProject);
              if (path == null || path.length < 1 || path[0] == null) {
                return;
              }
              final Object element = path[path.length - 1];
              if (element == child && finalChildUrl != null) {
                item.setData(Pair.create(finalChildUrl, root.second));
              }
              else {
                if (element == oldParent) {
                  item.setData(Pair.create(root.first.createUrlByElement(newParent), root.second));
                }
              }
            }
          });
        }
      }
    }

    public void beforePropertyChange(@NotNull final PsiTreeChangeEvent event) {
      if (event.getPropertyName().equals(PsiTreeChangeEvent.PROP_FILE_NAME) || event.getPropertyName().equals(PsiTreeChangeEvent.PROP_DIRECTORY_NAME)) {
        final PsiElement psiElement = event.getChild();
        if (psiElement instanceof PsiFile || psiElement instanceof PsiDirectory) {
          final Module module = ModuleUtil.findModuleForPsiElement(psiElement);
          if (module == null) return;
          final String url = ((PsiDirectory)psiElement.getParent()).getVirtualFile().getUrl() + "/" + event.getNewValue();
          final AbstractUrl childUrl = psiElement instanceof PsiFile ? new PsiFileUrl(url) : new DirectoryUrl(url, module.getName());

          for (String listName : myName2FavoritesRoots.keySet()) {
            final List<TreeItem<Pair<AbstractUrl, String>>> roots = myName2FavoritesRoots.get(listName);
            iterateTreeItems(roots, new Consumer<TreeItem<Pair<AbstractUrl, String>>>() {
              @Override
              public void consume(TreeItem<Pair<AbstractUrl, String>> item) {
                final Pair<AbstractUrl, String> root = item.getData();
                final Object[] path = root.first.createPath(myProject);
                if (path == null || path.length < 1 || path[0] == null) {
                  return;
                }
                final Object element = path[path.length - 1];
                if (element == psiElement && psiElement instanceof PsiFile) {
                  item.setData(Pair.create(childUrl, root.second));
                }
                else {
                  item.setData(root);
                }
              }
            });
          }
        }
      }
    }
  }
}
