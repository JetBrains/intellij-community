// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.treeView;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Progressive;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringHash;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.reference.SoftReference;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.tree.TreeVisitor;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * @see #createOn(JTree)
 * @see #createOn(JTree, DefaultMutableTreeNode)
 * @see #applyTo(JTree)
 * @see #applyTo(JTree, Object)
 */
public final class TreeState implements JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance(TreeState.class);

  public static final Key<WeakReference<ActionCallback>> CALLBACK = Key.create("Callback");
  private static final Key<Promise<Void>> EXPANDING = Key.create("TreeExpanding");

  private static final String EXPAND_TAG = "expand";
  private static final String SELECT_TAG = "select";
  private static final String PATH_TAG = "path";

  private enum Match {OBJECT, ID_TYPE}

  @Tag("item")
  static final class PathElement {
    @Attribute("name")
    public String id;
    @Attribute("type")
    public String type;
    @Attribute("user")
    public String userStr;

    transient Object userObject;
    transient final int index;

    @SuppressWarnings("unused")
    PathElement() {
      this(null, null, -1, null);
    }

    PathElement(String itemId, String itemType, int itemIndex, Object userObject) {
      id = itemId;
      type = itemType;

      index = itemIndex;
      userStr = userObject instanceof String ? (String)userObject : null;
      this.userObject = userObject;
    }

    @Override
    public String toString() {
      return id + ": " + type;
    }

    private boolean isMatchTo(Object object) {
      return getMatchTo(object) != null;
    }

    private Match getMatchTo(Object object) {
      Object userObject = TreeUtil.getUserObject(object);
      if (this.userObject != null && this.userObject.equals(userObject)) {
        return Match.OBJECT;
      }
      return Objects.equals(id, calcId(userObject)) &&
             Objects.equals(type, calcType(userObject)) ? Match.ID_TYPE : null;
    }
  }

  @XCollection(style = XCollection.Style.v2)
  private final List<PathElement[]> myExpandedPaths;
  @XCollection(style = XCollection.Style.v2)
  private final List<PathElement[]> mySelectedPaths;
  private boolean myScrollToSelection;

  // xml deserialization
  @SuppressWarnings("unused")
  TreeState() {
    this(new SmartList<>(), new SmartList<>());
  }

  private TreeState(List<PathElement[]> expandedPaths, List<PathElement[]> selectedPaths) {
    myExpandedPaths = expandedPaths;
    mySelectedPaths = selectedPaths;
    myScrollToSelection = true;
  }

  public boolean isEmpty() {
    return myExpandedPaths.isEmpty() && mySelectedPaths.isEmpty();
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    readExternal(element, myExpandedPaths, EXPAND_TAG);
    readExternal(element, mySelectedPaths, SELECT_TAG);
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

  @NotNull
  public static TreeState createOn(@NotNull JTree tree, @NotNull DefaultMutableTreeNode treeNode) {
    return createOn(tree, new TreePath(treeNode.getPath()));
  }

  @NotNull
  public static TreeState createOn(@NotNull JTree tree, @NotNull TreePath rootPath) {
    return new TreeState(createPaths(tree, TreeUtil.collectExpandedPaths(tree, rootPath)),
                         createPaths(tree, TreeUtil.collectSelectedPaths(tree, rootPath)));
  }

  @NotNull
  public static TreeState createOn(@NotNull JTree tree) {
    return createOn(tree, true, false);
  }

  @NotNull
  public static TreeState createOn(@NotNull JTree tree, boolean persistExpand, boolean persistSelect) {
    List<PathElement[]> expandedPaths = persistExpand
      ? createPaths(tree, TreeUtil.collectExpandedPaths(tree))
      : new ArrayList<>();
    List<PathElement[]> selectedPaths = persistSelect
      ? createPaths(tree, TreeUtil.collectSelectedPaths(tree))
      : new ArrayList<>();
    return new TreeState(expandedPaths, selectedPaths);
  }

  @NotNull
  public static TreeState createFrom(@Nullable Element element) {
    TreeState state = new TreeState(new ArrayList<>(), new ArrayList<>());
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

  private static List<PathElement[]> createPaths(@NotNull JTree tree, @NotNull List<? extends TreePath> paths) {
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
    PathElement[] result = new PathElement[count];
    for (int i = 0; i < count; i++) {
      Object cur = treePath.getPathComponent(i);
      Object userObject = TreeUtil.getUserObject(cur);
      int childIndex = prev == null ? 0 : model.getIndexOfChild(prev, cur);
      result[i] = new PathElement(calcId(userObject), calcType(userObject), childIndex, userObject);
      prev = cur;
    }
    return result;
  }

  @NotNull
  private static String calcId(@Nullable Object userObject) {
    if (userObject == null) return "";
    // There used to be a lot of code here that all started in 2005 with IDEA-29734 (back then IDEADEV-2150),
    // which later was modified many times, but in the end all it did was to invoke some slow operations on EDT
    // (IDEA-270843, IDEA-305055), and IDEA-29734 was still broken. It's a very edge case anyway (two folders
    // with the same name under the same parent) and should be fixed in a better way. For now just stick to
    // the good old way of doing this which should be fast enough for EDT unless the tree model is too slow by
    // itself, in which case we've got much bigger problems to worry about anyway!
    return StringUtil.notNullize(userObject.toString());
  }

  @NotNull
  private static String calcType(@Nullable Object userObject) {
    if (userObject == null) return "";
    String name = userObject.getClass().getName();
    return Integer.toHexString(StringHash.murmur(name, 31)) + ":" + StringUtil.getShortName(name);
  }

  public void applyTo(@NotNull JTree tree) {
    applyTo(tree, tree.getModel().getRoot());
  }

  public void applyTo(@NotNull JTree tree, @Nullable Object root) {
    LOG.debug(new IllegalStateException("restore paths"));
    if (visit(tree)) return; // AsyncTreeModel#accept
    if (root == null) return;
    TreeFacade facade = TreeFacade.getFacade(tree);
    ActionCallback callback = facade.getInitialized().doWhenDone(new TreeRunnable("TreeState.applyTo: on done facade init") {
      @Override
      public void perform() {
        facade.batch(indicator -> applyExpandedTo(facade, new TreePath(root), indicator));
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

  private void applyExpandedTo(@NotNull TreeFacade tree, @NotNull TreePath rootPath, @NotNull ProgressIndicator indicator) {
    indicator.checkCanceled();

    for (PathElement[] path : myExpandedPaths) {
      if (path.length == 0) continue;
      int index = rootPath.getPathCount() - 1;
      if (!path[index].isMatchTo(rootPath.getPathComponent(index))) continue;
      expandImpl(0, path, rootPath, tree, indicator);
    }
  }

  private void applySelectedTo(@NotNull JTree tree) {
    List<TreePath> selection = new ArrayList<>();
    for (PathElement[] path : mySelectedPaths) {
      TreeModel model = tree.getModel();
      TreePath treePath = new TreePath(model.getRoot());
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


  @Nullable
  private static TreePath findMatchedChild(@NotNull TreeModel model, @NotNull TreePath treePath, @NotNull PathElement pathElement) {
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

  private static void expandImpl(int positionInPath,
                                 PathElement[] path,
                                 TreePath treePath,
                                 TreeFacade tree,
                                 ProgressIndicator indicator) {
    tree.expand(treePath).doWhenDone(new TreeRunnable("TreeState.applyTo") {
      @Override
      public void perform() {
        indicator.checkCanceled();

        PathElement next = positionInPath == path.length - 1 ? null : path[positionInPath + 1];
        if (next == null) return;

        Object parent = treePath.getLastPathComponent();
        TreeModel model = tree.tree.getModel();
        int childCount = model.getChildCount(parent);
        for (int j = 0; j < childCount; j++) {
          Object child = tree.tree.getModel().getChild(parent, j);
          if (next.isMatchTo(child)) {
            expandImpl(positionInPath + 1, path, treePath.pathByAddingChild(child), tree, indicator);
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

    abstract void batch(Progressive progressive);

    static TreeFacade getFacade(JTree tree) {
      AbstractTreeBuilder builder = AbstractTreeBuilder.getBuilderFor(tree);
      return builder != null ? new BuilderFacade(builder) : new JTreeFacade(tree);
    }
  }

  static class JTreeFacade extends TreeFacade {

    JTreeFacade(JTree tree) {
      super(tree);
    }

    @Override
    public ActionCallback expand(@NotNull TreePath treePath) {
      tree.expandPath(treePath);
      return ActionCallback.DONE;
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

  static final class BuilderFacade extends TreeFacade {
    private final AbstractTreeBuilder myBuilder;

    BuilderFacade(AbstractTreeBuilder builder) {
      super(Objects.requireNonNull(builder.getTree()));
      myBuilder = builder;
    }

    @Override
    public ActionCallback getInitialized() {
      return myBuilder.getReady(this);
    }

    @Override
    public void batch(Progressive progressive) {
      myBuilder.batch(progressive);
    }

    @Override
    public ActionCallback expand(TreePath treePath) {
      NodeDescriptor<?> desc = TreeUtil.getLastUserObject(NodeDescriptor.class, treePath);
      if (desc == null) {
        return ActionCallback.REJECTED;
      }
      Object element = myBuilder.getTreeStructureElement(desc);
      ActionCallback result = new ActionCallback();
      myBuilder.expand(element, result.createSetDoneRunnable());

      return result;
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
    return TreeUtil.promiseExpand(tree, myExpandedPaths.stream().map(elements -> new Visitor(elements)));
  }

  private Promise<List<TreePath>> select(@NotNull JTree tree) {
    return TreeUtil.promiseSelect(tree, mySelectedPaths.stream().map(elements -> new Visitor(elements)));
  }

  private boolean visit(@NotNull JTree tree) {
    TreeModel model = tree.getModel();
    if (!(model instanceof TreeVisitor.Acceptor)) return false;

    expand(tree, promise -> expand(tree).onProcessed(expanded -> {
      if (isSelectionNeeded(expanded, tree, promise)) {
        select(tree).onProcessed(selected -> promise.setResult(null));
      }
    }));
    return true;
  }

  private static final class Visitor implements TreeVisitor {
    private final PathElement[] elements;

    Visitor(PathElement[] elements) {
      this.elements = elements;
    }

    @NotNull
    @Override
    public Action visit(@NotNull TreePath path) {
      int count = path.getPathCount();
      if (count > elements.length) return Action.SKIP_CHILDREN;
      boolean matches = elements[count - 1].isMatchTo(path.getLastPathComponent());
      return !matches ? Action.SKIP_CHILDREN : (count < elements.length ? Action.CONTINUE : Action.INTERRUPT);
    }
  }
}

