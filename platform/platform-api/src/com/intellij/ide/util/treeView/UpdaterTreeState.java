// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.treeView;

import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.util.*;

public class UpdaterTreeState {
  private final AbstractTreeUi myUi;
  private final Map<Object, Object> myToSelect = new WeakHashMap<>();
  private Map<Object, Condition> myAdjustedSelection = new WeakHashMap<>();
  private final Map<Object, Object> myToExpand = new WeakHashMap<>();
  private int myProcessingCount;

  private boolean myCanRunRestore = true;

  private final WeakHashMap<Object, Object> myAdjustmentCause2Adjustment = new WeakHashMap<>();

  UpdaterTreeState(AbstractTreeUi ui) {
    this(ui, false);
  }

  private UpdaterTreeState(AbstractTreeUi ui, boolean isEmpty) {
    myUi = ui;

    if (!isEmpty) {
      final JTree tree = myUi.getTree();
      putAll(addPaths(tree.getSelectionPaths()), myToSelect);
      putAll(addPaths(tree.getExpandedDescendants(new TreePath(tree.getModel().getRoot()))), myToExpand);
    }
  }

  private static void putAll(final Set<Object> source, final Map<Object, Object> target) {
    for (Object o : source) {
      target.put(o, o);
    }
  }

  private Set<Object> addPaths(Object[] elements) {
    Set<Object> set = new HashSet<>();
    if (elements != null) {
      ContainerUtil.addAll(set, elements);
    }

    return addPaths(set);
  }

  private Set<Object> addPaths(Enumeration elements) {
    ArrayList<Object> elementArray = new ArrayList<>();
    if (elements != null) {
      while (elements.hasMoreElements()) {
        Object each = elements.nextElement();
        elementArray.add(each);
      }
    }

    return addPaths(elementArray);
  }

  private Set<Object> addPaths(Collection elements) {
    Set<Object> target = new HashSet<>();

    if (elements != null) {
      for (Object each : elements) {
        final Object node = ((TreePath)each).getLastPathComponent();
        if (node instanceof DefaultMutableTreeNode) {
          final Object descriptor = ((DefaultMutableTreeNode)node).getUserObject();
          if (descriptor instanceof NodeDescriptor) {
            final Object element = myUi.getElementFromDescriptor((NodeDescriptor)descriptor);
            if (element != null) {
              target.add(element);
            }
          }
        }
      }
    }
    return target;
  }

  public Object @NotNull [] getToSelect() {
    return ArrayUtil.toObjectArray(myToSelect.keySet());
  }

  public Object @NotNull [] getToExpand() {
    return ArrayUtil.toObjectArray(myToExpand.keySet());
  }

  public boolean process(@NotNull Runnable runnable) {
    try {
      setProcessingNow(true);
      runnable.run();
    }
    finally {
      setProcessingNow(false);
    }

    return isEmpty();
  }

  public boolean isEmpty() {
    return myToExpand.isEmpty() && myToSelect.isEmpty() && myAdjustedSelection.isEmpty();
  }


  boolean isProcessingNow() {
    return myProcessingCount > 0;
  }

  public void addAll(@NotNull UpdaterTreeState state) {
    myToExpand.putAll(state.myToExpand);

    Object[] toSelect = state.getToSelect();
    for (Object each : toSelect) {
      if (!myAdjustedSelection.containsKey(each)) {
        myToSelect.put(each, each);
      }
    }

    myCanRunRestore = state.myCanRunRestore;
  }

  public boolean restore(@Nullable DefaultMutableTreeNode actionNode) {
    if (isProcessingNow() || !myCanRunRestore || myUi.hasNodesToUpdate()) {
      return false;
    }

    invalidateToSelectWithRefsToParent(actionNode);

    setProcessingNow(true);

    final Object[] toSelect = getToSelect();
    final Object[] toExpand = getToExpand();


    final Map<Object, Condition> adjusted = new WeakHashMap<>(myAdjustedSelection);

    clearSelection();
    clearExpansion();

    final Set<Object> originallySelected = myUi.getSelectedElements();

    myUi._select(toSelect, new TreeRunnable("UpdaterTreeState.restore") {
      @Override
      public void perform() {
        processUnsuccessfulSelections(toSelect, o -> {
          if (myUi.getTree().isRootVisible() || !myUi.getTreeStructure().getRootElement().equals(o)) {
            addSelection(o);
          }
          return o;
        }, originallySelected);

        processAdjusted(adjusted, originallySelected).doWhenDone(new TreeRunnable("UpdaterTreeState.restore: on done") {
          @Override
          public void perform() {
            myUi.expand(toExpand, new TreeRunnable("UpdaterTreeState.restore: after on done") {
              @Override
              public void perform() {
                myUi.clearUpdaterState();
                setProcessingNow(false);
              }
            }, true);
          }
        });
      }
    });

    return true;
  }

  private void invalidateToSelectWithRefsToParent(DefaultMutableTreeNode actionNode) {
    if (actionNode != null) {
      Object readyElement = myUi.getElementFor(actionNode);
      if (readyElement != null) {
        Iterator<Object> toSelect = myToSelect.keySet().iterator();
        while (toSelect.hasNext()) {
          Object eachToSelect = toSelect.next();
          if (readyElement.equals(myUi.getTreeStructure().getParentElement(eachToSelect))) {
            List<Object> children = myUi.getLoadedChildrenFor(readyElement);
            if (!children.contains(eachToSelect)) {
              toSelect.remove();
              if (!myToSelect.containsKey(readyElement) && !myUi.getSelectedElements().contains(eachToSelect)) {
                addAdjustedSelection(eachToSelect, Conditions.alwaysFalse(), null);
              }
            }
          }
        }
      }
    }
  }

  void beforeSubtreeUpdate() {
    myCanRunRestore = true;
  }

  private void processUnsuccessfulSelections(final Object[] toSelect, Function<Object, Object> restore, Set<Object> originallySelected) {
    final Set<Object> selected = myUi.getSelectedElements();

    boolean wasFullyRejected = false;
    if (toSelect.length > 0 && !selected.isEmpty() && !originallySelected.containsAll(selected)) {
      final Set<Object> successfulSelections = new HashSet<>();
      ContainerUtil.addAll(successfulSelections, toSelect);

      successfulSelections.retainAll(selected);
      wasFullyRejected = successfulSelections.isEmpty();
    } else if (selected.isEmpty() && originallySelected.isEmpty()) {
      wasFullyRejected = true;
    }

    if (wasFullyRejected && !selected.isEmpty()) return;

    for (Object eachToSelect : toSelect) {
      if (!selected.contains(eachToSelect)) {
        restore.fun(eachToSelect);
      }
    }
  }

  private ActionCallback processAdjusted(final Map<Object, Condition> adjusted, final Set<Object> originallySelected) {
    final ActionCallback result = new ActionCallback();

    final Set<Object> allSelected = myUi.getSelectedElements();

    Set<Object> toSelect = new HashSet<>();
    for (Map.Entry<Object, Condition> entry : adjusted.entrySet()) {
      Condition condition = entry.getValue();
      Object key = entry.getKey();
      if (condition.value(key)) continue;

      for (final Object eachSelected : allSelected) {
        if (isParentOrSame(key, eachSelected)) continue;
        toSelect.add(key);
      }
      if (allSelected.isEmpty()) {
        toSelect.add(key);
      }
    }

    final Object[] newSelection = ArrayUtil.toObjectArray(toSelect);

    if (newSelection.length > 0) {
      myUi._select(newSelection, new TreeRunnable("UpdaterTreeState.processAjusted") {
        @Override
        public void perform() {
          final Set<Object> hangByParent = new HashSet<>();
          processUnsuccessfulSelections(newSelection, o -> {
            if (myUi.isInStructure(o) && !adjusted.get(o).value(o)) {
              hangByParent.add(o);
            } else {
              addAdjustedSelection(o, adjusted.get(o), null);
            }
            return null;
          }, originallySelected);

          processHangByParent(hangByParent).notify(result);
        }
      }, false, true);
    } else {
      result.setDone();
    }

    return result;
  }

  private ActionCallback processHangByParent(Set<Object> elements) {
    if (elements.isEmpty()) return ActionCallback.DONE;

    ActionCallback result = new ActionCallback(elements.size());
    for (Object hangElement : elements) {
      if (!myAdjustmentCause2Adjustment.containsKey(hangElement)) {
        processHangByParent(hangElement).notify(result);
      }
      else {
        result.setDone();
      }
    }
    return result;
  }

  private ActionCallback processHangByParent(Object each) {
    ActionCallback result = new ActionCallback();
    processNextHang(each, result);
    return result;
  }

  private void processNextHang(Object element, final ActionCallback callback) {
    if (element == null || myUi.getSelectedElements().contains(element)) {
      callback.setDone();
    } else {
      final Object nextElement = myUi.getTreeStructure().getParentElement(element);
      if (nextElement == null) {
        callback.setDone();
      } else {
       myUi.select(nextElement, new TreeRunnable("UpdaterTreeState.processNextHang") {
          @Override
          public void perform() {
            processNextHang(nextElement, callback);
          }
        }, true);
      }
    }
  }

  private boolean isParentOrSame(Object parent, Object child) {
    Object eachParent = child;
    while (eachParent != null) {
      if (parent.equals(eachParent)) return true;
      eachParent = myUi.getTreeStructure().getParentElement(eachParent);
    }

    return false;
  }

  void clearExpansion() {
    myToExpand.clear();
  }

  public void clearSelection() {
    myToSelect.clear();
    myAdjustedSelection = new WeakHashMap<>();
  }

  public void addSelection(final Object element) {
    myToSelect.put(element, element);
  }

  void addAdjustedSelection(final Object element, Condition isExpired, @Nullable Object adjustmentCause) {
    myAdjustedSelection.put(element, isExpired);
    if (adjustmentCause != null) {
      myAdjustmentCause2Adjustment.put(adjustmentCause, element);
    }
  }

  @NonNls
  @Override
  public String toString() {
    return "UpdaterState toSelect " +
           myToSelect + " toExpand=" +
           myToExpand + " processingNow=" + isProcessingNow() + " canRun=" + myCanRunRestore;
  }

  private void setProcessingNow(boolean processingNow) {
    if (processingNow) {
      myProcessingCount++;
    } else {
      myProcessingCount--;
    }
    if (!isProcessingNow()) {
      myUi.maybeReady();
    }
  }

  public void removeFromSelection(Object element) {
    myToSelect.remove(element);
    myAdjustedSelection.remove(element);
  }
}
