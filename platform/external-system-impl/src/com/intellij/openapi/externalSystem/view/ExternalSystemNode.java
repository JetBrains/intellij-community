// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.view;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.externalSystem.ExternalSystemUiAware;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.dependencies.DependencyNode;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManager;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalSystemShortcutsManager;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalSystemTaskActivator;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.util.NlsContexts.Tooltip;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.awt.*;
import java.awt.event.InputEvent;
import java.util.List;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * @author Vladislav.Soroka
 */
public abstract class ExternalSystemNode<T> extends SimpleNode implements Comparable<ExternalSystemNode<T>> {

  public static final int BUILTIN_TASKS_DATA_NODE_ORDER = 10;
  public static final int BUILTIN_DEPENDENCIES_DATA_NODE_ORDER = BUILTIN_TASKS_DATA_NODE_ORDER + 10;
  public static final int BUILTIN_RUN_CONFIGURATIONS_DATA_NODE_ORDER = BUILTIN_DEPENDENCIES_DATA_NODE_ORDER + 10;
  public static final int BUILTIN_MODULE_DATA_NODE_ORDER = BUILTIN_RUN_CONFIGURATIONS_DATA_NODE_ORDER + 10;

  @NotNull public static final Comparator<? super ExternalSystemNode<?>> ORDER_AWARE_COMPARATOR = new Comparator<>() {
    @Override
    public int compare(@NotNull ExternalSystemNode<?> o1, @NotNull ExternalSystemNode<?> o2) {
      int order1 = getOrder(o1);
      int order2 = getOrder(o2);
      if (order1 == order2) return o1.compareTo(o2);
      return order1 < order2 ? -1 : 1;
    }

    private int getOrder(@NotNull ExternalSystemNode<?> o) {
      Order annotation = o.getClass().getAnnotation(Order.class);
      if (annotation != null) {
        return annotation.value();
      }
      return 0;
    }
  };


  protected static final ExternalSystemNode<?>[] NO_CHILDREN = new ExternalSystemNode[0];

  private static final List<ExternalSystemNode<?>> NO_CHILDREN_LIST = Collections.emptyList();
  private static final List<String> NO_ERRORS_LIST = Collections.emptyList();

  private final ExternalProjectsView myExternalProjectsView;
  private List<ExternalSystemNode<?>> myChildrenList = NO_CHILDREN_LIST;
  protected DataNode<T> myDataNode;
  @Nullable
  private ExternalSystemNode<?> myParent;
  private ExternalSystemNode<?>[] myChildren;
  private ExternalProjectsStructure.ErrorLevel myErrorLevel = ExternalProjectsStructure.ErrorLevel.NONE;
  private List<String> myErrors = NO_ERRORS_LIST;
  private ExternalProjectsStructure.ErrorLevel myTotalErrorLevel = null;

  public ExternalSystemNode(@NotNull ExternalProjectsView externalProjectsView,
                            @Nullable ExternalSystemNode<?> parent) {
    this(externalProjectsView, parent, null);
  }

  public ExternalSystemNode(@NotNull ExternalProjectsView externalProjectsView,
                            @Nullable ExternalSystemNode<?> parent,
                            @Nullable DataNode<T> dataNode) {
    super(externalProjectsView.getProject(), null);
    myExternalProjectsView = externalProjectsView;
    myDataNode = dataNode;
    myParent = parent;
  }

  @Override
  public boolean isAutoExpandNode() {
    SimpleNode parent = getParent();
    return parent != null && parent.getChildCount() == 1;
  }

  public void setParent(@Nullable ExternalSystemNode<?> parent) {
    myParent = parent;
  }

  @Nullable
  public T getData() {
    return myDataNode != null ? myDataNode.getData() : null;
  }

  public @Nullable DependencyNode getDependencyNode() {
    return null;
  }

  @Override
  public NodeDescriptor getParentDescriptor() {
    return myParent;
  }

  @Override
  public @NlsSafe String getName() {
    String displayName = getExternalProjectsView().getDisplayName(myDataNode);
    return displayName == null ? super.getName() : displayName;
  }

  protected ExternalProjectsView getExternalProjectsView() {
    return myExternalProjectsView;
  }

  protected ExternalSystemUiAware getUiAware() {
    return myExternalProjectsView.getUiAware();
  }

  protected ExternalProjectsStructure getStructure() {
    return myExternalProjectsView.getStructure();
  }

  protected ExternalSystemShortcutsManager getShortcutsManager() {
    return myExternalProjectsView.getShortcutsManager();
  }

  protected ExternalSystemTaskActivator getTaskActivator() {
    return myExternalProjectsView.getTaskActivator();
  }

  public @Nullable <DataType> DataType findNode(Class<DataType> aClass, @NotNull Function<? super ExternalSystemNode<?>, Object> map) {
    ExternalSystemNode<?> node = this;
    while (node != null) {
      var data = map.apply(node);
      if (aClass.isInstance(data)) {
        //noinspection unchecked
        return (DataType)data;
      }
      node = node.myParent;
    }
    return null;
  }

  public @Nullable <DataType extends ExternalSystemNode<?>> DataType findNode(Class<DataType> aClass) {
    return findNode(aClass, it -> it);
  }

  public @Nullable <DataType extends DependencyNode> DataType findDependencyNode(Class<DataType> aClass) {
    return findNode(aClass, it -> it.getDependencyNode());
  }

  public @Nullable <DataType extends ExternalSystemNode<?>> DataType findParent(Class<DataType> aClass) {
    return ObjectUtils.doIfNotNull(myParent, p -> p.findNode(aClass));
  }

  public boolean isVisible() {
    return getDisplayKind() != ExternalProjectsStructure.DisplayKind.NEVER && !(isIgnored() && !myExternalProjectsView.getShowIgnored());
  }

  public boolean isIgnored() {
    if (myDataNode != null) {
      return myDataNode.isIgnored();
    }
    final SimpleNode parent = getParent();
    return parent instanceof ExternalSystemNode && ((ExternalSystemNode<?>)parent).isIgnored();
  }

  public void setIgnored(final boolean ignored) {
    if (myDataNode != null) {
      ExternalProjectsManager.getInstance(myExternalProjectsView.getProject()).setIgnored(myDataNode, ignored);
    }
  }

  public ExternalProjectsStructure.DisplayKind getDisplayKind() {
    Class<?>[] visibles = getStructure().getVisibleNodesClasses();
    if (visibles == null) return ExternalProjectsStructure.DisplayKind.NORMAL;
    for (Class<?> each : visibles) {
      if (each.isInstance(this)) return ExternalProjectsStructure.DisplayKind.ALWAYS;
    }
    return ExternalProjectsStructure.DisplayKind.NEVER;
  }

  @Override
  public final ExternalSystemNode<?> @NotNull [] getChildren() {
    if (myChildren == null) {
      myChildren = buildChildren();
      onChildrenBuilt();
    }
    return myChildren;
  }

  protected void onChildrenBuilt() {
  }

  private ExternalSystemNode<?> @NotNull [] buildChildren() {
    List<? extends ExternalSystemNode<?>> newChildrenCandidates = new ArrayList<ExternalSystemNode<?>>(doBuildChildren());
    if (newChildrenCandidates.isEmpty()) return NO_CHILDREN;

    addAll(newChildrenCandidates, true);
    sort(myChildrenList);
    List<ExternalSystemNode<?>> visibleNodes = new ArrayList<>();
    for (ExternalSystemNode<?> each : myChildrenList) {
      if (each.isVisible()) visibleNodes.add(each);
    }
    return visibleNodes.toArray(new ExternalSystemNode[0]);
  }

  public void cleanUpCache() {
    myChildren = null;
    myChildrenList = NO_CHILDREN_LIST;
    myTotalErrorLevel = null;
  }

  protected ExternalSystemNode<?> @Nullable [] getCached() {
    return myChildren;
  }

  protected void sort(List<? extends ExternalSystemNode<?>> list) {
    if (!list.isEmpty()) {
      list.sort(ORDER_AWARE_COMPARATOR);
    }
  }

  public boolean addAll(Collection<? extends ExternalSystemNode<?>> externalSystemNodes) {
    return addAll(externalSystemNodes, false);
  }

  private boolean addAll(Collection<? extends ExternalSystemNode<?>> externalSystemNodes, boolean silently) {
    if (externalSystemNodes.isEmpty()) return false;

    if (myChildrenList == NO_CHILDREN_LIST) {
      myChildrenList = new CopyOnWriteArrayList<>();
    }

    for (ExternalSystemNode<?> externalSystemNode : externalSystemNodes) {
      externalSystemNode.setParent(this);
      myChildrenList.add(externalSystemNode);
    }
    if (!silently) {
      childrenChanged();
    }

    return true;
  }

  public boolean add(ExternalSystemNode<?> externalSystemNode) {
    return addAll(Collections.singletonList(externalSystemNode));
  }

  public boolean removeAll(Collection<? extends ExternalSystemNode<?>> externalSystemNodes) {
    return removeAll(externalSystemNodes, false);
  }

  private boolean removeAll(Collection<? extends ExternalSystemNode<?>> externalSystemNodes, boolean silently) {
    if (externalSystemNodes.isEmpty()) return false;

    for (ExternalSystemNode<?> externalSystemNode : externalSystemNodes) {
      externalSystemNode.setParent(null);
      if (myChildrenList != NO_CHILDREN_LIST) myChildrenList.remove(externalSystemNode);
    }
    if (!silently) {
      childrenChanged();
    }

    return true;
  }

  public void remove(ExternalSystemNode<?> externalSystemNode) {
    removeAll(Collections.singletonList(externalSystemNode));
  }

  protected void childrenChanged() {
    ExternalSystemNode<?> each = this;
    while (each != null) {
      each.myTotalErrorLevel = null;
      each = (ExternalSystemNode<?>)each.getParent();
    }

    sort(myChildrenList);
    final List<ExternalSystemNode<?>> visibleNodes = ContainerUtil.filter(myChildrenList, node -> node.isVisible());
    myChildren = visibleNodes.toArray(new ExternalSystemNode[0]);
    myExternalProjectsView.updateUpTo(this);
  }

  public boolean hasChildren() {
    return getChildren().length > 0;
  }

  @NotNull
  protected List<? extends ExternalSystemNode<?>> doBuildChildren() {
    if (myDataNode != null && !myDataNode.getChildren().isEmpty()) {
      final ExternalProjectsView externalProjectsView = getExternalProjectsView();
      return externalProjectsView.createNodes(externalProjectsView, this, myDataNode);
    }
    else {
      return myChildrenList;
    }
  }

  protected void setDataNode(DataNode<T> dataNode) {
    myDataNode = dataNode;
  }

  public ExternalProjectsStructure.ErrorLevel getTotalErrorLevel() {
    if (myTotalErrorLevel == null) {
      myTotalErrorLevel = calcTotalErrorLevel();
    }
    return myTotalErrorLevel;
  }

  private ExternalProjectsStructure.ErrorLevel calcTotalErrorLevel() {
    ExternalProjectsStructure.ErrorLevel childrenErrorLevel = getChildrenErrorLevel();
    return childrenErrorLevel.compareTo(myErrorLevel) > 0 ? childrenErrorLevel : myErrorLevel;
  }

  public ExternalProjectsStructure.ErrorLevel getChildrenErrorLevel() {
    if (myChildren == null && myDataNode != null) {
      return getExternalProjectsView().getErrorLevelRecursively(myDataNode);
    } else {
      ExternalProjectsStructure.ErrorLevel result = ExternalProjectsStructure.ErrorLevel.NONE;
      for (ExternalSystemNode<?> each : getChildren()) {
        ExternalProjectsStructure.ErrorLevel eachLevel = each.getTotalErrorLevel();
        if (eachLevel.compareTo(result) > 0) result = eachLevel;
        if (result == ExternalProjectsStructure.ErrorLevel.ERROR) break;
      }
      return result;
    }
  }

  public void setErrorLevel(ExternalProjectsStructure.ErrorLevel level, String... errors) {
    if (myErrorLevel == level) return;
    myErrorLevel = level;

    if (errors.length == 0) {
      myErrors = NO_ERRORS_LIST;
    }
    else {
      myErrors = Arrays.asList(errors);
    }
    myExternalProjectsView.updateUpTo(this);
  }

  @Override
  protected void doUpdate(@NotNull PresentationData presentation) {
    setNameAndTooltip(presentation, getName(), null);
  }

  protected void setNameAndTooltip(@NotNull PresentationData presentation, @NlsSafe String name, @Nullable @Tooltip String tooltip) {
    setNameAndTooltip(presentation, name, tooltip, (String)null);
  }

  protected void setNameAndTooltip(@NotNull PresentationData presentation, @NlsSafe String name, @Nullable @Tooltip String tooltip, @Nullable @NlsSafe String hint) {
    final boolean ignored = isIgnored();
    final SimpleTextAttributes textAttributes = ignored ? SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES : getPlainAttributes();
    setNameAndTooltip(presentation, name, tooltip, textAttributes);
    if (!StringUtil.isEmptyOrSpaces(hint)) {
      presentation.addText(" (" + hint + ")", ignored ? SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES : SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }

  protected void setNameAndTooltip(@NotNull PresentationData presentation, @NlsSafe String name, @Nullable @Tooltip String tooltip, SimpleTextAttributes attributes) {
    presentation.clearText();
    presentation.addText(name, prepareAttributes(attributes));
    final String s = (tooltip != null ? tooltip + "\n\r" : "") + StringUtil.join(myErrors, "\n\r");
    presentation.setTooltip(s);
  }

  private SimpleTextAttributes prepareAttributes(SimpleTextAttributes from) {
    ExternalProjectsStructure.ErrorLevel level = getTotalErrorLevel();
    Color waveColor = level == ExternalProjectsStructure.ErrorLevel.NONE ? null : JBColor.RED;
    int style = from.getStyle();
    if (waveColor != null) style |= SimpleTextAttributes.STYLE_WAVED;
    return new SimpleTextAttributes(from.getBgColor(), from.getFgColor(), waveColor, style);
  }

  @Nullable
  @NonNls
  protected String getActionId() {
    return null;
  }

  @Nullable
  @NonNls
  protected String getMenuId() {
    return null;
  }

  protected String message(@PropertyKey(resourceBundle = ExternalSystemBundle.PATH_TO_BUNDLE) @NotNull String key, Object @NotNull ... params) {
    return ExternalSystemBundle.message(key, params);
  }

  @Nullable
  public VirtualFile getVirtualFile() {
    return null;
  }

  @Nullable
  public Navigatable getNavigatable() {
    return null;
  }

  @Override
  public void handleDoubleClickOrEnter(SimpleTree tree, InputEvent inputEvent) {
    String actionId = getActionId();
    getExternalProjectsView().handleDoubleClickOrEnter(this, actionId, inputEvent);
  }

  @Override
  public int compareTo(@NotNull ExternalSystemNode node) {
    return StringUtil.compare(this.getName(), node.getName(), true);
  }

  public void mergeWith(ExternalSystemNode<T> node) {
    setDataNode(node.myDataNode);
  }
}
