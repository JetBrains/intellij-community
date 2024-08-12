// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.bookmark.BookmarksListener;
import com.intellij.ide.projectView.impl.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.util.TreeItem;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.ide.favoritesTreeView.FavoritesListProvider.EP_NAME;

/**
 * @deprecated Use Bookmarks API instead.
 */
@Service(Service.Level.PROJECT)
@State(name = "FavoritesManager", storages = {
  @Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE),
  @Storage(value = StoragePathMacros.WORKSPACE_FILE, deprecated = true),
})
@Deprecated(forRemoval = true)
public final class FavoritesManager implements PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance(FavoritesManager.class);
  // fav list name -> list of (root: root url, root class)
  private final Map<String, List<TreeItem<Pair<AbstractUrl, String>>>> myName2FavoritesRoots = new TreeMap<>();
  private final List<String> myFavoritesRootsOrder = new ArrayList<>();
  private final Project myProject;
  private final List<FavoritesListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final FavoritesViewSettings myViewSettings = new FavoritesViewSettings();

  public static FavoritesManager getInstance(@NotNull Project project) {
    return project.getService(FavoritesManager.class);
  }

  public FavoritesManager(@NotNull Project project) {
    myProject = project;
    EP_NAME.getPoint(myProject).addChangeListener(() -> {
      rootsChanged();
    }, myProject);
  }

  private void rootsChanged() {
    for (FavoritesListener listener : myListeners) {
      listener.rootsChanged();
    }
  }

  private void listAdded(@NotNull String listName) {
    for (FavoritesListener listener : myListeners) {
      listener.listAdded(listName);
    }
  }

  public void addFavoritesListener(final FavoritesListener listener, @NotNull Disposable parent) {
    myListeners.add(listener);
    listener.rootsChanged();
    Disposer.register(parent, new Disposable() {
      @Override
      public void dispose() {
        myListeners.remove(listener);
      }
    });
  }

  public @NotNull List<String> getAvailableFavoritesListNames() {
    return new ArrayList<>(myFavoritesRootsOrder);
  }

  public synchronized void createNewList(@NotNull String listName) {
    myName2FavoritesRoots.put(listName, new ArrayList<>());
    myFavoritesRootsOrder.add(listName);
    listAdded(listName);
  }

  /**
   * @deprecated use {@link BookmarksListener#structureChanged} instead. For example,
   * {@code myProject.getMessageBus().syncPublisher(BookmarksListener.TOPIC).structureChanged(node)}.
   * The {@code null}-node can be used to rebuild the whole BookmarksView.
   */
  @Deprecated
  public synchronized void fireListeners(final @NotNull String listName) {
    myProject.getMessageBus().syncPublisher(BookmarksListener.TOPIC).structureChanged(null);
    rootsChanged();
  }

  public @NotNull FavoritesViewSettings getViewSettings() {
    return myViewSettings;
  }

  public @NotNull List<TreeItem<Pair<AbstractUrl, String>>> getFavoritesListRootUrls(@NotNull String name) {
    final List<TreeItem<Pair<AbstractUrl, String>>> pairs = myName2FavoritesRoots.get(name);
    return pairs == null ? new ArrayList<>() : pairs;
  }

  public synchronized boolean addRoots(@NotNull String name, Module moduleContext, @NotNull Object elements) {
    return true;
  }

  @Override
  public void loadState(@NotNull Element element) {
    myName2FavoritesRoots.clear();
    for (Element list : element.getChildren(ELEMENT_FAVORITES_LIST)) {
      final String name = list.getAttributeValue(ATTRIBUTE_NAME);
      List<TreeItem<Pair<AbstractUrl, String>>> roots = readRoots(list, myProject);
      myName2FavoritesRoots.put(name, roots);
      myFavoritesRootsOrder.add(name);
    }
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  @Override
  public void noStateLoaded() {
    LOG.info("no state loaded for old favorites");
  }

  private static final @NonNls String CLASS_NAME = "klass";
  private static final @NonNls String FAVORITES_ROOT = "favorite_root";
  private static final @NonNls String ELEMENT_FAVORITES_LIST = "favorites_list";
  private static final @NonNls String ATTRIBUTE_NAME = "name";

  private static List<TreeItem<Pair<AbstractUrl, String>>> readRoots(final Element list, Project project) {
    List<TreeItem<Pair<AbstractUrl, String>>> result = new ArrayList<>();
    readFavoritesOneLevel(list, project, result);
    return result;
  }

  private static void readFavoritesOneLevel(Element list, Project project, Collection<? super TreeItem<Pair<AbstractUrl, String>>> result) {
    for (Element favorite : list.getChildren(FAVORITES_ROOT)) {
      final String className = favorite.getAttributeValue(CLASS_NAME);
      final AbstractUrl abstractUrl = readUrlFromElement(favorite, project);
      if (abstractUrl != null) {
        final TreeItem<Pair<AbstractUrl, String>> treeItem = new TreeItem<>(Pair.create(abstractUrl, className));
        result.add(treeItem);
        readFavoritesOneLevel(favorite, project, treeItem.getChildren());
      }
    }
  }

  private static final ArrayList<AbstractUrl> ourAbstractUrlProviders = new ArrayList<>();

  static {
    ourAbstractUrlProviders.add(new ModuleUrl(null, null));
    ourAbstractUrlProviders.add(new DirectoryUrl(null, null));

    ourAbstractUrlProviders.add(new ModuleGroupUrl(null));

    ourAbstractUrlProviders.add(new PsiFileUrl(null));
    ourAbstractUrlProviders.add(new LibraryModuleGroupUrl(null));
    ourAbstractUrlProviders.add(new NamedLibraryUrl(null, null));
  }

  private static final @NonNls String ATTRIBUTE_TYPE = "type";
  private static final @NonNls String ATTRIBUTE_URL = "url";
  private static final @NonNls String ATTRIBUTE_MODULE = "module";

  private static @Nullable AbstractUrl readUrlFromElement(Element element, Project project) {
    final String type = element.getAttributeValue(ATTRIBUTE_TYPE);
    final String urlValue = element.getAttributeValue(ATTRIBUTE_URL);
    final String moduleName = element.getAttributeValue(ATTRIBUTE_MODULE);

    for (FavoriteNodeProvider nodeProvider : FavoriteNodeProvider.EP_NAME.getExtensions(project)) {
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

  @Override
  public Element getState() {
    Element element = new Element("state");
    for (String name : myFavoritesRootsOrder) {
      Element list = new Element(ELEMENT_FAVORITES_LIST);
      list.setAttribute(ATTRIBUTE_NAME, name);
      writeRoots(list, myName2FavoritesRoots.get(name));
      element.addContent(list);
    }
    DefaultJDOMExternalizer.writeExternal(this, element);
    return element;
  }

  private static void writeRoots(Element element, Collection<? extends TreeItem<Pair<AbstractUrl, String>>> roots) {
    for (TreeItem<Pair<AbstractUrl, String>> root : roots) {
      final AbstractUrl url = root.getData().getFirst();
      if (url == null) continue;
      final Element list = new Element(FAVORITES_ROOT);
      url.write(list);
      list.setAttribute(CLASS_NAME, root.getData().getSecond());
      element.addContent(list);
      final List<TreeItem<Pair<AbstractUrl, String>>> children = root.getChildren();
      if (children != null && !children.isEmpty()) {
        writeRoots(list, children);
      }
    }
  }
}
