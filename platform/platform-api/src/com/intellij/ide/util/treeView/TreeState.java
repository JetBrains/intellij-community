/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ide.util.treeView;

import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Progressive;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringHash;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.reference.SoftReference;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * @see #createOn(JTree)
 * @see #createOn(JTree, DefaultMutableTreeNode)
 *
 * @see #applyTo(JTree)
 * @see #applyTo(JTree, Object)
 */
public class TreeState implements JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance(TreeState.class);

  public static final Key<WeakReference<ActionCallback>> CALLBACK = Key.create("Callback");

  private static final String EXPAND_TAG = "expand";
  private static final String SELECT_TAG = "select";
  private static final String PATH_TAG = "path";

  private enum Match {OBJECT, ID_TYPE}

  @Tag("item")
  static class PathElement {
    @Attribute("name")
    public String id;
    @Attribute("type")
    public String type;
    @Attribute("user")
    public String userStr;

    Object userObject;
    final int index;

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
      if (this.userObject != null && this.userObject.equals(userObject)) return Match.OBJECT;
      return Comparing.equal(this.id, calcId(userObject)) &&
             Comparing.equal(this.type, calcType(userObject)) ? Match.ID_TYPE : null;
    }
  }

  private final List<List<PathElement>> myExpandedPaths;
  private final List<List<PathElement>> mySelectedPaths;
  private boolean myScrollToSelection;

  private TreeState(List<List<PathElement>> expandedPaths, final List<List<PathElement>> selectedPaths) {
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

  private static void readExternal(Element root, List<List<PathElement>> list, String name) throws InvalidDataException {
    list.clear();
    for (Element element : root.getChildren(name)) {
      for (Element child : element.getChildren(PATH_TAG)) {
        PathElement[] path = XmlSerializer.deserialize(child, PathElement[].class);
        list.add(ContainerUtil.immutableList(path));
      }
    }
  }

  @NotNull
  public static TreeState createOn(JTree tree, final DefaultMutableTreeNode treeNode) {
    return new TreeState(createPaths(tree, TreeUtil.collectExpandedPaths(tree, new TreePath(treeNode.getPath()))),
                         createPaths(tree, TreeUtil.collectSelectedPaths(tree, new TreePath(treeNode.getPath()))));
  }

  @NotNull
  public static TreeState createOn(@NotNull JTree tree) {
    return new TreeState(createPaths(tree, TreeUtil.collectExpandedPaths(tree)), new ArrayList<>());
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
  public void writeExternal(Element element) throws WriteExternalException {
    writeExternal(element, myExpandedPaths, EXPAND_TAG);
    writeExternal(element, mySelectedPaths, SELECT_TAG);
  }

  private static void writeExternal(Element element, List<List<PathElement>> list, String name) throws WriteExternalException {
    Element root = new Element(name);
    for (List<PathElement> path : list) {
      Element e = XmlSerializer.serialize(path.toArray());
      e.setName(PATH_TAG);
      root.addContent(e);
    }
    element.addContent(root);
  }

  private static List<List<PathElement>> createPaths(JTree tree, List<TreePath> paths) {
    ArrayList<List<PathElement>> result = new ArrayList<>();
    for (TreePath path : paths) {
      if (tree.isRootVisible() || path.getPathCount() > 1) {
        ContainerUtil.addIfNotNull(result, createPath(tree.getModel(), path));
      }
    }
    return result;
  }

  @NotNull
  private static List<PathElement> createPath(@NotNull TreeModel model, @NotNull TreePath treePath) {
    ArrayList<PathElement> result = new ArrayList<>();
    Object prev = null;
    for (int i = 0; i < treePath.getPathCount(); i++) {
      Object cur = treePath.getPathComponent(i);
      Object userObject = TreeUtil.getUserObject(cur);
      int childIndex = prev == null ? 0 : model.getIndexOfChild(prev, cur);
      PathElement pe = new PathElement(calcId(userObject), calcType(userObject), childIndex, userObject);
      result.add(pe);
      prev = cur;
    }
    return result;
  }

  @NotNull
  private static String calcId(@Nullable Object userObject) {
    if (userObject == null) return "";
    Object value =
      userObject instanceof NodeDescriptorProvidingKey ? ((NodeDescriptorProvidingKey)userObject).getKey() :
      userObject instanceof AbstractTreeNode ? ((AbstractTreeNode)userObject).getValue() :
      userObject;
    if (value instanceof NavigationItem) {
      try {
        String name = ((NavigationItem)value).getName();
        return name != null ? name : StringUtil.notNullize(value.toString());
      }
      catch (Exception ignored) {
      }
    }
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
    if (rootPath.getPathCount() <= 0) return;

    for (List<PathElement> path : myExpandedPaths) {
      if (path.isEmpty()) continue;
      int index = rootPath.getPathCount() - 1;
      if (!path.get(index).isMatchTo(rootPath.getPathComponent(index))) continue;
      expandImpl(0, path, rootPath, tree, indicator);
    }
  }

  private void applySelectedTo(@NotNull JTree tree) {
    List<TreePath> selection = new ArrayList<>();
    for (List<PathElement> path : mySelectedPaths) {
      TreeModel model = tree.getModel();
      TreePath treePath = new TreePath(model.getRoot());
      for (int i = 1; treePath != null && i < path.size(); i++) {
        treePath = findMatchedChild(model, treePath, path.get(i));
      }
      ContainerUtil.addIfNotNull(selection, treePath);
    }
    if (selection.isEmpty()) return;
    for (TreePath treePath : selection) {
      tree.setSelectionPath(treePath);
    }
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
                                 List<PathElement> path,
                                 TreePath treePath,
                                 TreeFacade tree,
                                 ProgressIndicator indicator) {
    tree.expand(treePath).doWhenDone(new TreeRunnable("TreeState.applyTo") {
      @Override
      public void perform() {
        indicator.checkCanceled();

        PathElement next = positionInPath == path.size() - 1 ? null : path.get(positionInPath + 1);
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

  static abstract class TreeFacade {

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
      WeakReference<ActionCallback> ref = UIUtil.getClientProperty(tree, CALLBACK);
      ActionCallback callback = SoftReference.dereference(ref);
      if (callback != null) return callback;
      return ActionCallback.DONE;
    }

    @Override
    public void batch(Progressive progressive) {
      progressive.run(new EmptyProgressIndicator());
    }
  }

  static class BuilderFacade extends TreeFacade {

    private final AbstractTreeBuilder myBuilder;

    BuilderFacade(AbstractTreeBuilder builder) {
      super(ObjectUtils.notNull(builder.getTree()));
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
      Object userObject = TreeUtil.getUserObject(treePath.getLastPathComponent());
      if (!(userObject instanceof NodeDescriptor)) return ActionCallback.REJECTED;

      NodeDescriptor desc = (NodeDescriptor)userObject;
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
}

