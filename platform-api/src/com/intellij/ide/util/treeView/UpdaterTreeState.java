package com.intellij.ide.util.treeView;

import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Condition;
import com.intellij.util.Function;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.util.*;

public class UpdaterTreeState {

  private AbstractTreeUi myUi;
  protected WeakHashMap<Object, Object> myToSelect = new WeakHashMap<Object, Object>();
  protected WeakHashMap<Object, Condition> myAdjustedSelection = new WeakHashMap<Object, Condition>();
  protected WeakHashMap<Object, Object> myDisposedElements = new WeakHashMap<Object, Object>();
  protected WeakHashMap<Object, Object> myToExpand = new WeakHashMap<Object, Object>();
  private boolean myProcessingNow;

  private boolean myCanRunRestore = true;

  public UpdaterTreeState(AbstractTreeUi ui) {
    myUi = ui;

    final JTree tree = myUi.getTree();
    putAll(addPaths(tree.getSelectionPaths()), myToSelect);
    putAll(addPaths(tree.getExpandedDescendants(new TreePath(tree.getModel().getRoot()))), myToExpand);
  }

  private static void putAll(final Set<Object> source, final Map<Object, Object> target) {
    for (Object o : source) {
      target.put(o, o);
    }
  }

  private static Set<Object> addPaths(Object[] elements) {
    Set<Object> set = new HashSet<Object>();
    if (elements != null) {
      set.addAll(Arrays.asList(elements));
    }

    return addPaths(set);
  }

  private static Set<Object> addPaths(Enumeration elements) {
    ArrayList<Object> elementArray = new ArrayList<Object>();
    if (elements != null) {
      while (elements.hasMoreElements()) {
        Object each = elements.nextElement();
        elementArray.add(each);
      }
    }

    return addPaths(elementArray);
  }

  private static Set<Object> addPaths(Collection elements) {
    Set<Object> target = new HashSet<Object>();

    if (elements != null) {
      for (Object each : elements) {
        final Object node = ((TreePath)each).getLastPathComponent();
        if (node instanceof DefaultMutableTreeNode) {
          final Object descriptor = ((DefaultMutableTreeNode)node).getUserObject();
          if (descriptor instanceof NodeDescriptor) {
            final Object element = ((NodeDescriptor)descriptor).getElement();
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
    boolean oldValue = myProcessingNow;
    try {
      myProcessingNow = true;
      runnable.run();
    }
    finally {
      if (!oldValue) {
        myProcessingNow = false;
      }
    }

    return isEmpty();
  }

  public boolean isEmpty() {
    return myToExpand.isEmpty() && myToSelect.isEmpty() && myAdjustedSelection.isEmpty();
  }


  public boolean isProcessingNow() {
    return myProcessingNow;
  }

  public void addAll(final UpdaterTreeState state) {
    myToExpand.putAll(state.myToExpand);

    final Iterator<Object> toSelect = state.myToSelect.keySet().iterator();
    while (toSelect.hasNext()) {
      Object each = toSelect.next();
      if (!myAdjustedSelection.containsKey(each)) {
        myToSelect.put(each, each);
      }
    }

    myCanRunRestore = state.myCanRunRestore;
  }

  public boolean restore() {
    if (isProcessingNow() || !myCanRunRestore) return false;

    myProcessingNow = true;

    final Object[] toSelect = getToSelect();
    final Object[] toExpand = getToExpand();


    final Map<Object, Condition> adjusted = new WeakHashMap<Object, Condition>();
    adjusted.putAll(myAdjustedSelection);

    clearSelection();
    clearExpansion();

    myUi._select(toSelect, new Runnable() {
      public void run() {
        processUnsuccessfulSelections(toSelect, new Function<Object, Object>() {
          public Object fun(final Object o) {
            addSelection(o);
            return o;
          }
        });

        processAjusted(adjusted).doWhenDone(new Runnable() {
          public void run() {
            myUi.expand(toExpand, new Runnable() {
              public void run() {
                if (!isEmpty()) {
                  myCanRunRestore = false;
                  myUi.setUpdaterState(UpdaterTreeState.this);
                }
                myProcessingNow = false;
              }
            }, true);
          }
        });
      }
    }, false, true, true);

    return true;
  }

  void beforeSubtreeUpdate() {
    myCanRunRestore = true;
  }

  private void processUnsuccessfulSelections(final Object[] toSelect, Function<Object, Object> restore) {
    final Set<Object> selected = myUi.getSelectedElements();
    for (Object eachToSelect : toSelect) {
      if (!selected.contains(eachToSelect)) {
        restore.fun(eachToSelect);
      }
    }
  }

  private ActionCallback processAjusted(final Map<Object, Condition> adjusted) {
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

    final Object[] newSelection = toSelect.toArray(new Object[toSelect.size()]);

    myUi._select(newSelection, new Runnable() {
      public void run() {
        processUnsuccessfulSelections(newSelection, new Function<Object, Object>() {
          public Object fun(final Object o) {
            addAdjustedSelection(o, adjusted.get(o));
            return null;
          }
        });
        result.setDone();
      }
    }, true, true, true);

    return result;
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

  public void addAdjustedSelection(final Object element, Condition isExpired) {
    myAdjustedSelection.put(element, isExpired);
  }

}