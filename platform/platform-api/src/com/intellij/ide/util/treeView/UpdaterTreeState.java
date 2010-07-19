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
package com.intellij.ide.util.treeView;

import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Condition;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.util.*;

public class UpdaterTreeState {

  private final AbstractTreeUi myUi;
  protected WeakHashMap<Object, Object> myToSelect = new WeakHashMap<Object, Object>();
  protected WeakHashMap<Object, Condition> myAdjustedSelection = new WeakHashMap<Object, Condition>();
  protected WeakHashMap<Object, Object> myDisposedElements = new WeakHashMap<Object, Object>();
  protected WeakHashMap<Object, Object> myToExpand = new WeakHashMap<Object, Object>();
  private int myProcessingCount;

  private boolean myCanRunRestore = true;

  private final WeakHashMap<Object, Object> myAdjustmentCause2Adjustment = new WeakHashMap<Object, Object>();

  public UpdaterTreeState(AbstractTreeUi ui) {
    this(ui, false);
  }

  public UpdaterTreeState(AbstractTreeUi ui, boolean isEmpty) {
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
    Set<Object> set = new HashSet<Object>();
    if (elements != null) {
      ContainerUtil.addAll(set, elements);
    }

    return addPaths(set);
  }

  private Set<Object> addPaths(Enumeration elements) {
    ArrayList<Object> elementArray = new ArrayList<Object>();
    if (elements != null) {
      while (elements.hasMoreElements()) {
        Object each = elements.nextElement();
        elementArray.add(each);
      }
    }

    return addPaths(elementArray);
  }

  private Set<Object> addPaths(Collection elements) {
    Set<Object> target = new HashSet<Object>();

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

  public Object[] getToSelect() {
    return myToSelect.keySet().toArray(new Object[myToSelect.size()]);
  }

  public Object[] getToExpand() {
    return myToExpand.keySet().toArray(new Object[myToExpand.size()]);
  }

  public boolean process(Runnable runnable) {
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


  public boolean isProcessingNow() {
    return myProcessingCount > 0;
  }

  public void addAll(final UpdaterTreeState state) {
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


    final Map<Object, Condition> adjusted = new WeakHashMap<Object, Condition>();
    adjusted.putAll(myAdjustedSelection);

    clearSelection();
    clearExpansion();

    final Set<Object> originallySelected = myUi.getSelectedElements();

    myUi._select(toSelect, new Runnable() {
      public void run() {
        processUnsuccessfulSelections(toSelect, new Function<Object, Object>() {
          public Object fun(final Object o) {
            if (myUi.getTree().isRootVisible() || !myUi.getTreeStructure().getRootElement().equals(o)) {
              addSelection(o);
            }
            return o;
          }
        }, originallySelected);

        processAjusted(adjusted, originallySelected).doWhenDone(new Runnable() {
          public void run() {
            myUi.expand(toExpand, new Runnable() {
              public void run() {
                myUi.clearUpdaterState();
                setProcessingNow(false);
              }
            }, true);
          }
        });
      }
    }, false, true, true, false);

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
                addAdjustedSelection(eachToSelect, Condition.FALSE, null);
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
    if (toSelect.length > 0 && selected.size() > 0 && !originallySelected.containsAll(selected)) {
      final Set<Object> successfulSelections = new HashSet<Object>();
      ContainerUtil.addAll(successfulSelections, toSelect);

      successfulSelections.retainAll(selected);
      wasFullyRejected = successfulSelections.size() == 0;
    } else if (selected.size() == 0 && originallySelected.size() == 0) {
      wasFullyRejected = true;
    }

    if (wasFullyRejected && selected.size() > 0) return;

    for (Object eachToSelect : toSelect) {
      if (!selected.contains(eachToSelect)) {
        restore.fun(eachToSelect);
      }
    }
  }

  private ActionCallback processAjusted(final Map<Object, Condition> adjusted, final Set<Object> originallySelected) {
    final ActionCallback result = new ActionCallback();

    final Set<Object> allSelected = myUi.getSelectedElements();

    Set<Object> toSelect = new HashSet<Object>();
    for (Object each : adjusted.keySet()) {
      if (adjusted.get(each).value(each)) continue;

      for (final Object eachSelected : allSelected) {
        if (isParentOrSame(each, eachSelected)) continue;
        toSelect.add(each);
      }
      if (allSelected.size() == 0) {
        toSelect.add(each);
      }
    }

    final Object[] newSelection = ArrayUtil.toObjectArray(toSelect);

    if (newSelection.length > 0) {
      myUi._select(newSelection, new Runnable() {
        public void run() {
          final Set<Object> hangByParent = new HashSet<Object> ();
          processUnsuccessfulSelections(newSelection, new Function<Object, Object>() {
            public Object fun(final Object o) {
              if (myUi.isInStructure(o) && !adjusted.get(o).value(o)) {
                hangByParent.add(o);
              } else {
                addAdjustedSelection(o, adjusted.get(o), null);
              }
              return null;
            }
          }, originallySelected);

          processHangByParent(hangByParent).notify(result);
        }
      }, true, true, true);
    } else {
      result.setDone();
    }

    return result;
  }

  private ActionCallback processHangByParent(Set<Object> elements) {
    if (elements.size() == 0) return new ActionCallback.Done();

    ActionCallback result = new ActionCallback(elements.size());
    for (Iterator<Object> iterator = elements.iterator(); iterator.hasNext();) {
      Object hangElement = iterator.next();
      if (!myAdjustmentCause2Adjustment.containsKey(hangElement)) {
        processHangByParent(hangElement).notify(result);
      } else {
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
       myUi.select(nextElement, new Runnable() {
          public void run() {
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

  public void clearExpansion() {
    myToExpand.clear();
  }

  public void clearSelection() {
    myToSelect.clear();
    myAdjustedSelection = new WeakHashMap<Object, Condition>();
  }

  public void addSelection(final Object element) {
    myToSelect.put(element, element);
  }

  public void addAdjustedSelection(final Object element, Condition isExpired, @Nullable Object adjustmentCause) {
    myAdjustedSelection.put(element, isExpired);
    if (adjustmentCause != null) {
      myAdjustmentCause2Adjustment.put(adjustmentCause, element);
    }
  }

  @Override
  public String toString() {
    return "UpdaterState toSelect" + Arrays.asList(myToSelect) + " toExpand=" + Arrays.asList(myToExpand) + " processingNow=" + isProcessingNow() + " canRun=" + myCanRunRestore;
  }

  public void setProcessingNow(boolean processingNow) {
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
