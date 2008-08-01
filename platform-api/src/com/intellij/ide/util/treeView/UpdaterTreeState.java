package com.intellij.ide.util.treeView;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.util.*;

public class UpdaterTreeState {

  private AbstractTreeUi myUi;
  protected WeakHashMap<Object, Object> myToSelect = new WeakHashMap<Object, Object>();
  protected WeakHashMap<Object, Object> myToExpand = new WeakHashMap<Object, Object>();
  private boolean myProcessingNow;

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

  public boolean process(final DefaultMutableTreeNode node, final JTree tree) {
    return process(new Runnable() {
      public void run() {
        final Object object = node.getUserObject();
        if (object == null) return;

        final boolean toExpand = myToExpand.containsKey(object);
        final boolean toSelect = myToSelect.containsKey(object);

        if (!toExpand && !toSelect) return;


        TreePath path = new TreePath(node.getPath());
        if (toExpand) {
          myToExpand.remove(object);
          tree.expandPath(path);
        }

        if (toSelect) {
          myToSelect.remove(object);
          tree.addSelectionPath(path);
        }
      }
    });
  }

  public boolean isEmpty() {
    return myToExpand.isEmpty() && myToSelect.isEmpty();
  }


  public boolean isProcessingNow() {
    return myProcessingNow;
  }

  public void addAll(final UpdaterTreeState state) {
    myToExpand.putAll(state.myToExpand);
    myToSelect.putAll(state.myToSelect);
  }

  public void restore() {
    final Object[] toSelect = getToSelect();
    final Object[] toExpand = getToExpand();

    clearExpansion();
    clearSelection();

    final Runnable expandRunnable = new Runnable() {
      public void run() {
        for (Object each : toExpand) {
          myUi.getBuilder().expand(each, null);
        }
      }
    };
    if (toSelect.length > 0) {
      myUi._select(toSelect, expandRunnable, false, true);
    } else {
      expandRunnable.run();
    }
  }

  public void clearExpansion() {
    myToExpand.clear();
  }

  public void clearSelection() {
    myToSelect.clear();
  }

  public void addSelection(final Object element) {
    myToSelect.put(element, element);
  }
}