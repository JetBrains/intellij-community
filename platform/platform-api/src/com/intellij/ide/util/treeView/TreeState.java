// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.treeView;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Progressive;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringHash;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.reference.SoftReference;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.tree.TreeVisitor;
import com.intellij.ui.treeStructure.CachingTreePath;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Interner;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

import static com.intellij.ide.util.treeView.CachedTreePresentationData.createFromTree;

/**
 * @see #createOn(JTree)
 * @see #createOn(JTree, DefaultMutableTreeNode)
 * @see #applyTo(JTree)
 * @see #applyTo(JTree, Object)
 */
public final class TreeState implements JDOMExternalizable {
  private static final Interner<String> INTERNER = Interner.createStringInterner();

  private static final Logger LOG = Logger.getInstance(TreeState.class);

  public static final Key<WeakReference<ActionCallback>> CALLBACK = Key.create("Callback");
  private static final Key<Promise<Void>> EXPANDING = Key.create("TreeExpanding");

  private static final @NotNull String EXPAND_TAG = "expand";
  private static final @NotNull String SELECT_TAG = "select";
  private static final @NotNull String PRESENTATION_TAG = "presentation";
  private static final @NotNull String PATH_TAG = "path";

  private enum Match {OBJECT, ID_TYPE}

  @Tag("item")
  static final class PathElement implements CachedTreePathElement {
    String id;
    String type;
    transient Object userObject;
    final transient int index;

    @SuppressWarnings("unused")
    PathElement() {
      this(null, null, -1, null);
    }

    PathElement(String itemId, String itemType, int itemIndex, Object userObject) {
      setId(itemId);
      setType(itemType);

      index = itemIndex;
      this.userObject = userObject instanceof String stringObject ? INTERNER.intern(stringObject) : userObject;
    }

    @Override
    public String toString() {
      return id + ": " + type;
    }

    @Override
    public boolean matches(@NotNull Object node) {
      return isMatchTo(node);
    }

    private boolean isMatchTo(Object object) {
      return getMatchTo(object) != null;
    }

    private Match getMatchTo(Object object) {
      Object userObject = TreeUtil.getUserObject(object);
      if (this.userObject != null && this.userObject.equals(userObject)) {
        return Match.OBJECT;
      }
      return Objects.equals(id, calcId(object)) &&
             Objects.equals(type, calcType(object)) ? Match.ID_TYPE : null;
    }

    @Attribute("name")
    public void setId(String id) {
      this.id = id == null ? null : INTERNER.intern(id);
    }

    @Override
    @Attribute("name")
    public String getId() {
      return id;
    }

    @Attribute("type")
    public void setType(String type) {
      this.type = type == null ? null : INTERNER.intern(type);
    }

    @Override
    @Attribute("type")
    public String getType() {
      return type;
    }
  }

  private static final class PathMatcher {
    private final @NotNull PathElement @NotNull [] serializedPath;
    private int matchedSoFar = 0;
    private @Nullable TreePath matchedPath;

    static @Nullable PathMatcher tryStart(@NotNull PathElement @NotNull [] serializedPath, @NotNull TreePath rootPath) {
      if (serializedPath.length == 0) return null;
      if (!serializedPath[0].matches(rootPath.getLastPathComponent())) return null;
      var attempt = new PathMatcher(serializedPath, rootPath.getParentPath());
      return attempt.tryAdvance(rootPath.getLastPathComponent()) ? attempt : null;
    }

    private PathMatcher(@NotNull PathElement @NotNull [] serializedPath, @Nullable TreePath parentPath) {
      this.serializedPath = serializedPath;
      this.matchedPath = parentPath;
    }

    @Nullable TreePath matchedPath() {
      return matchedPath;
    }

    boolean fullyMatched() {
      return matchedSoFar == serializedPath.length;
    }

    boolean tryAdvance(@NotNull Object node) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Trying to advance a matcher using " + node);
        LOG.debug("The serialized path: " + Arrays.toString(serializedPath));
        LOG.debug("Matched so far: " + matchedSoFar + " elements corresponding to " + matchedPath);
      }
      assert matchedSoFar <= serializedPath.length;
      if (matchedSoFar == serializedPath.length) throw new IllegalStateException("Already matched all the path");
      boolean flattenedSucceeded = false;
      boolean plainSucceeded = false;
      // The flattened case (a node represents several nested nodes).
      var provider = getProvider(node);
      if (provider != null) {
        var flattened = provider.getFlattenedElements();
        if (flattened != null && flattened.size() > 1 && matchedSoFar + flattened.size() <= serializedPath.length) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("Unflattened elements: " + flattened);
          }
          var allMatch = true;
          for (int i = 0; i < flattened.size(); ++i) {
            var actualElement = flattened.get(i);
            var serializedElement = serializedPath[matchedSoFar + i];
            if (!serializedElement.id.equals(actualElement.id()) || !serializedElement.type.equals(actualElement.type())) {
              if (LOG.isDebugEnabled()) {
                LOG.debug("Mismatched element at " + i + ": " + actualElement + " != " + serializedElement);
              }
              allMatch = false;
              break;
            }
          }
          if (allMatch) {
            flattenedSucceeded = true;
            matchedSoFar += flattened.size();
          }
        }
      }
      // The regular case (one-to-one match).
      if (!flattenedSucceeded) {
        if (serializedPath[matchedSoFar].isMatchTo(node)) {
          plainSucceeded = true;
          ++matchedSoFar;
        }
      }
      if (flattenedSucceeded || plainSucceeded) {
        matchedPath = matchedPath == null ? new CachingTreePath(node) : matchedPath.pathByAddingChild(node);
        if (LOG.isDebugEnabled()) {
          LOG.debug("Advanced successfully to " + matchedSoFar + " elements corresponding to " + matchedPath);
        }
        return true;
      }
      else {
        LOG.debug("Failed to advance");
        return false;
      }
    }
  }

  @Tag("presentation")
  static final class SerializableCachedPresentation {
    PathElement item;
    CachedPresentationDataImpl data;
    Map<String, String> attributes;

    SerializableCachedPresentation() {
      item = new PathElement();
      data = new CachedPresentationDataImpl();
    }

    SerializableCachedPresentation(@NotNull CachedTreePresentationData data) {
      this.item = (PathElement)data.getPathElement();
      this.data = (CachedPresentationDataImpl)data.getPresentation();
      this.attributes = data.getExtraAttributes();
    }

    boolean isValid() {
      return item != null && data != null && data.isValid();
    }

    @Property(surroundWithTag = false)
    public @NotNull PathElement getItem() {
      return item;
    }

    @Property(surroundWithTag = false)
    public void setItem(@NotNull PathElement item) {
      this.item = item;
    }

    @Property(surroundWithTag = false)
    public @NotNull CachedPresentationDataImpl getData() {
      return data;
    }

    @Property(surroundWithTag = false)
    public void setData(@NotNull CachedPresentationDataImpl data) {
      this.data = data;
    }

    @XCollection(style = XCollection.Style.v2)
    public @Nullable Map<String, String> getAttributes() {
      return attributes;
    }

    @XCollection(style = XCollection.Style.v2)
    public void setAttributes(@Nullable Map<String, String> attributes) {
      this.attributes = attributes;
    }

    @Override
    public String toString() {
      return "SerializableCachedPresentation{" +
             "item=" + item +
             ", data=" + data +
             ", attributes=" + attributes +
             '}';
    }
  }

  @Tag("data")
  static final class CachedPresentationDataImpl implements CachedPresentationData {
    String text;
    String iconPath;
    String iconPlugin;
    String iconModule;
    boolean isLeaf = true;

    @SuppressWarnings("unused")
    CachedPresentationDataImpl() { }

    CachedPresentationDataImpl(
      @NotNull String text,
      @Nullable CachedIconPresentation iconPresentation,
      boolean isLeaf
    ) {
      this.text = text;
      if (iconPresentation != null) {
        this.iconPath = iconPresentation.getPath();
        this.iconPlugin = iconPresentation.getPlugin();
        this.iconModule = iconPresentation.getModule();
      }
      this.isLeaf = isLeaf;
    }

    boolean isValid() {
      return text != null;
    }

    @Override
    @Attribute("text")
    public @NotNull String getText() {
      return text;
    }

    @Attribute("text")
    public void setText(String text) {
      this.text = text;
    }

    @Attribute("iconPath")
    public @Nullable String getIconPath() {
      return iconPath;
    }

    @Attribute("iconPath")
    public void setIconPath(String iconPath) {
      this.iconPath = iconPath;
    }

    @Attribute("iconPlugin")
    public @Nullable String getIconPlugin() {
      return iconPlugin;
    }

    @Attribute("iconPlugin")
    public void setIconPlugin(String iconPlugin) {
      this.iconPlugin = iconPlugin;
    }

    @Attribute("iconModule")
    public @Nullable String getIconModule() {
      return iconModule;
    }

    @Attribute("iconModule")
    public void setIconModule(String iconModule) {
      this.iconModule = iconModule;
    }

    @Override
    public @Nullable CachedIconPresentation getIconData() {
      if (iconPath == null || iconPlugin == null) return null;
      return new CachedIconPresentation(iconPath, iconPlugin, iconModule);
    }

    @Override
    @Attribute("isLeaf")
    public boolean isLeaf() {
      return isLeaf;
    }

    @Attribute("isLeaf")
    public void setLeaf(boolean leaf) {
      isLeaf = leaf;
    }

    @Override
    public String toString() {
      return "'" + text + "' icon=" + iconPath;
    }
  }

  @XCollection(style = XCollection.Style.v2)
  private final List<PathElement[]> myExpandedPaths;
  @XCollection(style = XCollection.Style.v2)
  private final List<PathElement[]> mySelectedPaths;
  private @Nullable CachedTreePresentationData myPresentationData;
  private boolean myScrollToSelection;

  TreeState() {
    this(new SmartList<>(), new SmartList<>(), null);
  }

  private TreeState(List<PathElement[]> expandedPaths, List<PathElement[]> selectedPaths, @Nullable CachedTreePresentationData presentationData) {
    myExpandedPaths = expandedPaths;
    mySelectedPaths = selectedPaths;
    myPresentationData = presentationData;
    myScrollToSelection = true;
    if (LOG.isDebugEnabled()) {
      LOG.debug("TreeState created: " + this);
    }
  }

  public boolean isEmpty() {
    return myExpandedPaths.isEmpty() && mySelectedPaths.isEmpty();
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    readExternal(element, myExpandedPaths, EXPAND_TAG);
    readExternal(element, mySelectedPaths, SELECT_TAG);
    try {
      myPresentationData = readExternalPresentation(element);
    }
    catch (CancellationException ignored) {
    }
    catch (Exception e) {
      LOG.warn("An error occurred while trying to read a cached tree presentation", e);
    }
  }

  private static void readExternal(@NotNull Element root, List<PathElement[]> list, @NotNull String name) {
    list.clear();
    for (Element element : root.getChildren(name)) {
      for (Element child : element.getChildren(PATH_TAG)) {
        PathElement[] path = XmlSerializer.deserialize(child, PathElement[].class);
        list.add(path);
      }
    }
  }

  private static @Nullable CachedTreePresentationData readExternalPresentation(Element element) {
    var presentationChildren = element.getChildren(PRESENTATION_TAG);
    if (presentationChildren.size() != 1) return null;
    var presentations = new SmartList<CachedTreePresentationData>();
    readExternalPresentation(presentationChildren.get(0), presentations);
    return presentations.size() == 1 ? presentations.get(0) : null;
  }

  private static void readExternalPresentation(Element element, List<CachedTreePresentationData> result) {
    var deserialized = XmlSerializer.deserialize(element, SerializableCachedPresentation.class);
    if (!deserialized.isValid()) return;
    var item = deserialized.getItem();
    var presentationData = deserialized.getData();
    var attributes = deserialized.getAttributes();
    var children = new SmartList<CachedTreePresentationData>();
    var data = new CachedTreePresentationData(item, presentationData, attributes, children);
    for (Element child : element.getChildren(PRESENTATION_TAG)) {
      readExternalPresentation(child, children);
    }
    if (!children.isEmpty()) { // Just in case isLeaf is incorrect in the XML.
      presentationData.setLeaf(false);
    }
    result.add(data);
  }

  public static @NotNull TreeState createOn(@NotNull JTree tree, @NotNull DefaultMutableTreeNode treeNode) {
    return createOn(tree, new CachingTreePath(treeNode.getPath()));
  }

  public static @NotNull TreeState createOn(@NotNull JTree tree, @NotNull TreePath rootPath) {
    return new TreeState(createPaths(tree, TreeUtil.collectExpandedPaths(tree, rootPath)),
                         createPaths(tree, TreeUtil.collectSelectedPaths(tree, rootPath)),
                         null);
  }

  public static @NotNull TreeState createOn(@NotNull JTree tree) {
    return createOn(tree, true, false);
  }

  public static @NotNull TreeState createOn(@NotNull JTree tree, boolean persistExpand, boolean persistSelect) {
    return createOn(tree, persistExpand, persistSelect, false);
  }

  @ApiStatus.Internal
  public static @NotNull TreeState createOn(@NotNull JTree tree, boolean persistExpand, boolean persistSelect, boolean persistPresentation) {
    List<TreePath> expanded = persistExpand ? TreeUtil.collectExpandedPaths(tree) : Collections.emptyList();
    List<TreePath> selected = persistSelect ? TreeUtil.collectSelectedPaths(tree) : Collections.emptyList();
    return createOn(tree, expanded, selected, persistPresentation);
  }

  public static @NotNull TreeState createOn(@NotNull JTree tree, @NotNull List<TreePath> expandedPaths, @NotNull List<TreePath> selectedPaths) {
    return createOn(tree, expandedPaths, selectedPaths, false);
  }

  @ApiStatus.Internal
  public static @NotNull TreeState createOn(@NotNull JTree tree, @NotNull List<TreePath> expandedPaths, @NotNull List<TreePath> selectedPaths, boolean persistPresentation) {
    List<PathElement[]> expandedPathElements = !expandedPaths.isEmpty()
      ? createPaths(tree, expandedPaths)
      : new ArrayList<>();
    List<PathElement[]> selectedPathElements = !selectedPaths.isEmpty()
      ? createPaths(tree, selectedPaths)
      : new ArrayList<>();
    return new TreeState(expandedPathElements, selectedPathElements, persistPresentation ? createFromTree(tree) : null);
  }

  public static @NotNull TreeState createFrom(@Nullable Element element) {
    TreeState state = new TreeState();
    try {
      if (element != null) {
        state.readExternal(element);
      }
    }
    catch (InvalidDataException e) {
      LOG.warn(e);
    }
    return state;
  }

  @Override
  public void writeExternal(Element element) {
    writeExternal(element, myExpandedPaths, EXPAND_TAG);
    writeExternal(element, mySelectedPaths, SELECT_TAG);
    writeExternal(element, myPresentationData);
  }

  private static void writeExternal(Element element, @Nullable CachedTreePresentationData data) {
    if (data == null) return;
    Element root = XmlSerializer.serialize(new SerializableCachedPresentation(data));
    for (CachedTreePresentationData child : data.getChildren()) {
      writeExternal(root, child);
    }
    element.addContent(root);
  }

  private static void writeExternal(Element element, List<PathElement[]> list, String name) {
    Element root = new Element(name);
    for (PathElement[] path : list) {
      Element e = XmlSerializer.serialize(path);
      e.setName(PATH_TAG);
      root.addContent(e);
    }
    element.addContent(root);
  }

  private static List<PathElement[]> createPaths(@NotNull JTree tree, @NotNull @Unmodifiable List<? extends TreePath> paths) {
    List<PathElement[]> list = new ArrayList<>();
    for (TreePath o : paths) {
      if (o.getPathCount() > 1 || tree.isRootVisible()) {
        list.add(createPath(tree.getModel(), o));
      }
    }
    return list;
  }

  private static PathElement[] createPath(@NotNull TreeModel model, @NotNull TreePath treePath) {
    Object prev = null;
    int count = treePath.getPathCount();
    ArrayList<PathElement> result = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      Object cur = treePath.getPathComponent(i);
      Object userObject = TreeUtil.getUserObject(cur);
      int childIndex = prev == null ? 0 : model.getIndexOfChild(prev, cur);
      boolean isFlattened = false;
      String id = null;
      String type = null;
      var provider = getProvider(cur);
      if (provider != null) {
        var flattened = provider.getFlattenedElements();
        if (flattened != null && !flattened.isEmpty()) {
          if (flattened.size() == 1) {
            id = flattened.get(0).id();
            type = flattened.get(0).type();
          }
          else {
            isFlattened = true;
            for (SerializablePathElement element : flattened) {
              // No "user object" because the unflattened sequence elements aren't present in the tree at this moment.
              result.add(new PathElement(element.id(), element.type(), childIndex, null));
              // The first element of the unflattened sequence has the same child index as the flattened element.
              // Every other element is the first and only child of its parent.
              // For example, a directory "a/b/c" becomes a -> b -> c, where "a" has the same place in the tree as the flattened node.
              childIndex = 0;
            }
          }
        }
      }
      if (!isFlattened) {
        // A special case: when flattened returns a list of one item, reuse that ID/type to avoid calculating them twice.
        if (id == null) {
          id = calcId(cur);
        }
        if (type == null) {
          type = calcType(cur);
        }
        result.add(new PathElement(id, type, childIndex, userObject));
      }
      prev = cur;
    }
    return result.toArray(PathElement[]::new);
  }

  private static @Nullable PathElementIdProvider getProvider(@Nullable Object node) {
    if (node == null) return null;
    if (node instanceof PathElementIdProvider provider) return provider;
    var userObject = TreeUtil.getUserObject(node);
    if (userObject instanceof PathElementIdProvider provider) return provider;
    return null;
  }

  static @NotNull String calcId(@Nullable Object node) {
    if (node == null) return "";
    var provider = getProvider(node);
    // The easiest case: the node provides an ID explicitly.
    if (provider != null) {
      return provider.getPathElementId();
    }
    return defaultPathElementId(node);
  }

  /**
   * The default ID calculation implementation
   * <p>
   *   Calculates the ID based on the value returned by the node's user object's {@code toString()}.
   * </p>
   * @param node a tree node or its user object (a node is unwrapped as needed)
   * @return the default value representation of the node's user object
   */
  public static @NotNull String defaultPathElementId(@NotNull Object node) {
    var userObject = TreeUtil.getUserObject(node);
    if (userObject == null) return "";
    // There used to be a lot of code here that all started in 2005 with IDEA-29734 (back then IDEADEV-2150),
    // which later was modified many times, but in the end all it did was to invoke some slow operations on EDT
    // (IDEA-270843, IDEA-305055), and IDEA-29734 was still broken.
    // Now we just fall back to toString(), which MUST work fast. If that doesn't work, implement PathElementIdProvider.
    return StringUtil.notNullize(userObject.toString());
  }

  static @NotNull String calcType(@Nullable Object node) {
    if (node == null) return "";
    var provider = getProvider(node);
    if (provider != null) {
      // A special override for unusual cases, for example, nodes with cached presentations.
      var providedType = provider.getPathElementType();
      if (providedType != null) return providedType;
    }
    return defaultPathElementType(node);
  }

  /**
   * The default type calculation implementation
   * <p>
   *   Calculates the type based on the actual class of the node's user object.
   * </p>
   * @param node a tree node or its user object (a node is unwrapped as needed)
   * @return the default type representation of the node's user object
   */
  public static @NotNull String defaultPathElementType(@NotNull Object node) {
    var userObject = TreeUtil.getUserObject(node);
    if (userObject == null) return "";
    String name = userObject.getClass().getName();
    return Integer.toHexString(StringHash.murmur(name, 31)) + ":" + StringUtil.getShortName(name);
  }

  public void applyTo(@NotNull JTree tree) {
    applyTo(tree, tree.getModel().getRoot());
  }

  public void applyTo(@NotNull JTree tree, @Nullable Object root) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("TreeState.applyTo: " + tree + "\n" + this);
    }
    if (tree instanceof @NotNull Tree jbTree) {
      jbTree.fireTreeStateRestoreStarted();
    }
    applyCachedPresentation(tree);
    if (visit(tree)) return; // AsyncTreeModel#accept
    if (root == null) return;
    TreeFacade facade = TreeFacade.getFacade(tree);
    ActionCallback callback = facade.getInitialized().doWhenDone(new TreeRunnable("TreeState.applyTo: on done facade init") {
      @Override
      public void perform() {
        facade.batch(indicator -> applyExpandedTo(facade, new CachingTreePath(root), indicator));
      }
    });
    if (tree.getSelectionCount() == 0) {
      callback.doWhenDone(new TreeRunnable("TreeState.applyTo: on done") {
        @Override
        public void perform() {
          if (tree.getSelectionCount() == 0) {
            applySelectedTo(tree);
          }
        }
      });
    }
  }

  private void applyCachedPresentation(@NotNull JTree tree) {
    if (myPresentationData != null && tree instanceof CachedTreePresentationSupport cps) {
      cps.setCachedPresentation(myPresentationData.createTree());
      if (tree instanceof @NotNull Tree jbTree) {
        jbTree.fireTreeStateCachedStateRestored();
      }
    }
  }

  private static void clearCachedPresentation(@NotNull JTree tree) {
    if (tree instanceof CachedTreePresentationSupport jbTree) {
      jbTree.setCachedPresentation(null);
    }
  }

  private void applyExpandedTo(@NotNull TreeFacade tree, @NotNull TreePath rootPath, @NotNull ProgressIndicator indicator) {
    indicator.checkCanceled();

    for (PathElement[] path : myExpandedPaths) {
      if (path.length == 0) continue;
      var matcher = PathMatcher.tryStart(path, rootPath);
      if (matcher == null) continue;
      expandImpl(matcher, tree, indicator);
    }

    tree.finishExpanding();
  }

  private void applySelectedTo(@NotNull JTree tree) {
    List<TreePath> selection = new ArrayList<>();
    for (PathElement[] path : mySelectedPaths) {
      TreeModel model = tree.getModel();
      TreePath treePath = new CachingTreePath(model.getRoot());
      for (int i = 1; treePath != null && i < path.length; i++) {
        treePath = findMatchedChild(model, treePath, path[i]);
      }
      ContainerUtil.addIfNotNull(selection, treePath);
    }
    if (selection.isEmpty()) return;
    tree.setSelectionPaths(selection.toArray(TreeUtil.EMPTY_TREE_PATH));
    if (myScrollToSelection) {
      TreeUtil.showRowCentered(tree, tree.getRowForPath(selection.get(0)), true, true);
    }
  }


  private static @Nullable TreePath findMatchedChild(@NotNull TreeModel model, @NotNull TreePath treePath, @NotNull PathElement pathElement) {
    Object parent = treePath.getLastPathComponent();
    int childCount = model.getChildCount(parent);
    if (childCount <= 0) return null;

    boolean idMatchedFound = false;
    Object idMatchedChild = null;
    for (int j = 0; j < childCount; j++) {
      Object child = model.getChild(parent, j);
      Match match = pathElement.getMatchTo(child);
      if (match == Match.OBJECT) {
        return treePath.pathByAddingChild(child);
      }
      if (match == Match.ID_TYPE && !idMatchedFound) {
        idMatchedChild = child;
        idMatchedFound = true;
      }
    }
    if (idMatchedFound) {
      return treePath.pathByAddingChild(idMatchedChild);
    }

    int index = Math.max(0, Math.min(pathElement.index, childCount - 1));
    Object child = model.getChild(parent, index);
    return treePath.pathByAddingChild(child);
  }

  private static void expandImpl(
    @NotNull PathMatcher matcher,
    TreeFacade tree,
    ProgressIndicator indicator
  ) {
    var parentPath = matcher.matchedPath();
    assert parentPath != null; // This function is only called after some initial match succeeds.
    tree.expand(parentPath).doWhenDone(new TreeRunnable("TreeState.applyTo") {
      @Override
      public void perform() {
        indicator.checkCanceled();

        if (matcher.fullyMatched()) return;


        Object parent = parentPath.getLastPathComponent();
        TreeModel model = tree.tree.getModel();
        int childCount = model.getChildCount(parent);
        for (int j = 0; j < childCount; j++) {
          Object child = tree.tree.getModel().getChild(parent, j);
          if (matcher.tryAdvance(child)) {
            expandImpl(matcher, tree, indicator);
            break;
          }
        }
      }
    });
  }

  abstract static class TreeFacade {

    final JTree tree;

    TreeFacade(@NotNull JTree tree) {this.tree = tree;}

    abstract ActionCallback getInitialized();

    abstract ActionCallback expand(TreePath treePath);

    abstract void finishExpanding();

    abstract void batch(Progressive progressive);

    static TreeFacade getFacade(JTree tree) {
      return new JTreeFacade(tree);
    }
  }

  static class JTreeFacade extends TreeFacade {
    private final boolean useBulkExpand;
    private final @NotNull List<@NotNull TreePath> pathsToExpand = new ArrayList<>();

    JTreeFacade(JTree tree) {
      super(tree);
      useBulkExpand = TreeUtil.isBulkExpandCollapseSupported(tree);
    }

    @Override
    public ActionCallback expand(@NotNull TreePath treePath) {
      if (useBulkExpand) {
        pathsToExpand.add(treePath);
      }
      else {
        tree.expandPath(treePath);
      }
      return ActionCallback.DONE;
    }

    @Override
    void finishExpanding() {
      clearCachedPresentation(tree);
      if (useBulkExpand) {
        TreeUtil.expandPaths(tree, pathsToExpand);
      }
    }

    @Override
    public ActionCallback getInitialized() {
      WeakReference<ActionCallback> ref = ComponentUtil.getClientProperty(tree, CALLBACK);
      ActionCallback callback = SoftReference.dereference(ref);
      if (callback != null) return callback;
      return ActionCallback.DONE;
    }

    @Override
    public void batch(Progressive progressive) {
      progressive.run(new EmptyProgressIndicator());
    }
  }

  public void setScrollToSelection(boolean scrollToSelection) {
    myScrollToSelection = scrollToSelection;
  }

  @Override
  public String toString() {
    Element st = new Element("TreeState");
    String content;
    try {
      writeExternal(st);
      content = JDOMUtil.writeChildren(st, "\n");
    }
    catch (IOException e) {
      content = ExceptionUtil.getThrowableText(e);
    }
    return "TreeState(" + myScrollToSelection + ")\n" + content;
  }

  /**
   * @deprecated Temporary solution to resolve simultaneous expansions with async tree model.
   * Note that the specified consumer must resolve async promise at the end.
   */
  @Deprecated
  public static void expand(@NotNull JTree tree, @NotNull Consumer<? super AsyncPromise<Void>> consumer) {
    Promise<Void> expanding = ComponentUtil.getClientProperty(tree, EXPANDING);
    LOG.debug("EXPANDING: ", expanding);
    if (expanding == null) expanding = Promises.resolvedPromise();
    expanding.onProcessed(value -> {
      AsyncPromise<Void> promise = new AsyncPromise<>();
      ComponentUtil.putClientProperty(tree, EXPANDING, promise);
      consumer.accept(promise);
    });
  }

  private static boolean isSelectionNeeded(List<TreePath> list, @NotNull JTree tree, AsyncPromise<Void> promise) {
    if (list != null && tree.isSelectionEmpty()) return true;
    if (promise != null) promise.setResult(null);
    return false;
  }

  private Promise<List<TreePath>> expand(@NotNull JTree tree) {
    if (TreeUtil.isBulkExpandCollapseSupported(tree) && tree instanceof Tree jbTree && Registry.is("ide.tree.bulk.expand.tree.state", false)) {
      var promise = new AsyncPromise<List<TreePath>>();
      var bulkExpandVisitor = new MultiplePathsVisitor(myExpandedPaths);
      TreeUtil.promiseVisit(tree, bulkExpandVisitor).onProcessed(lastPathFound -> {
        jbTree.expandPaths(bulkExpandVisitor.pathsFound);
        promise.setResult(bulkExpandVisitor.pathsFound);
      });
      return promise;
    }
    else {
      if (myPresentationData == null) {
        return TreeUtil.promiseExpand(tree, myExpandedPaths.stream().map(elements -> new SinglePathVisitor(elements)));
      }
      else {
        // If we have cached presentation data, then everything is already shown and expanded,
        // and if the user collapses one of those nodes, we don't want to expand it again here,
        // as that looks and feels weird.
        // So instead, only load these nodes so they can replace the cached ones.
        var promise = new AsyncPromise<List<TreePath>>();
        var visitor = new MultiplePathsVisitor(myExpandedPaths);
        TreeUtil.promiseVisit(tree, visitor).onProcessed(lastPathFound -> {
          promise.setResult(visitor.pathsFound);
        });
        return promise;
      }
    }
  }

  private Promise<List<TreePath>> select(@NotNull JTree tree) {
    return TreeUtil.promiseSelect(tree, mySelectedPaths.stream().map(elements -> new SinglePathVisitor(elements)));
  }

  private boolean visit(@NotNull JTree tree) {
    TreeModel model = tree.getModel();
    if (!(model instanceof TreeVisitor.Acceptor)) return false;

    var started = System.currentTimeMillis();
    expand(tree, promise -> expand(tree).onProcessed(expanded -> {
      if (LOG.isDebugEnabled() && expanded != null) {
        LOG.debug("Expanded " + expanded.size() + " paths in " + (System.currentTimeMillis() - started) + " ms");
      }
      if (tree instanceof @NotNull Tree jbTree) {
        jbTree.fireTreeStateRestoreFinished();
      }
      clearCachedPresentation(tree);
      if (isSelectionNeeded(expanded, tree, promise)) {
        select(tree).onProcessed(selected -> promise.setResult(null));
      }
    }));
    return true;
  }

  private static final class SinglePathVisitor implements TreeVisitor {
    private final @NotNull PathMatcher matcher;

    SinglePathVisitor(PathElement[] elements) {
      matcher = new PathMatcher(elements, null);
    }

    @Override
    public @NotNull TreeVisitor.VisitThread visitThread() {
      return VisitThread.BGT;
    }

    @Override
    public @NotNull Action visit(@NotNull TreePath path) {
      if (matcher.tryAdvance(path.getLastPathComponent())) {
        return matcher.fullyMatched() ? Action.INTERRUPT : Action.CONTINUE;
      }
      else {
        return Action.SKIP_CHILDREN;
      }
    }
  }

  private static final class MultiplePathsVisitor implements TreeVisitor {

    private static final class PathMatchState {

      private final @NotNull PathMatcher matcher;

      private PathMatchState(PathElement[] elements) { this.matcher = new PathMatcher(elements, null); }

      @NotNull PathMatch match(@NotNull TreePath path) {
        if (Objects.equals(path.getParentPath(), matcher.matchedPath())) {
          if (matcher.tryAdvance(path.getLastPathComponent())) {
            return matcher.fullyMatched() ? PathMatch.FULL : PathMatch.PARTIAL;
          }
          else {
            return PathMatch.NONE;
          }
        }
        else {
          return PathMatch.NONE;
        }
      }
    }

    private enum PathMatch {
      FULL,
      PARTIAL,
      NONE
    }

    private final List<PathMatchState> matchStates = new ArrayList<>();
    private final List<TreePath> pathsFound = new ArrayList<>();

    MultiplePathsVisitor(List<PathElement[]> paths) {
      for (PathElement[] path : paths) {
        matchStates.add(new PathMatchState(path));
      }
    }

    @Override
    public @NotNull TreeVisitor.VisitThread visitThread() {
      return VisitThread.BGT;
    }

    @Override
    public @NotNull Action visit(@NotNull TreePath path) {
      boolean foundPartialMatch = false;
      for (Iterator<PathMatchState> iterator = matchStates.iterator(); iterator.hasNext(); ) {
        PathMatchState state = iterator.next();
        switch (state.match(path)) {
          case FULL -> {
            pathsFound.add(path);
            iterator.remove();
          }
          case PARTIAL -> {
            foundPartialMatch = true;
          }
        }
      }
      if (matchStates.isEmpty()) {
        return Action.INTERRUPT;
      }
      else if (foundPartialMatch) {
        return Action.CONTINUE;
      }
      else {
        return Action.SKIP_CHILDREN;
      }
    }
  }
}

