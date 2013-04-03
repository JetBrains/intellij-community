package com.intellij.openapi.externalSystem.ui;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.externalSystem.model.project.change.ExternalProjectStructureChange;
import com.intellij.openapi.externalSystem.model.project.id.ProjectEntityId;
import com.intellij.openapi.externalSystem.settings.ExternalSystemTextAttributes;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.MutableTreeNode;
import java.util.*;

/**
 * Not thread-safe.
 *
 * @param <T> type of the target entity {@link ProjectStructureNodeDescriptor#getElement() associated} with the current node
 * @author Denis Zhdanov
 * @since 8/23/11 3:50 PM
 */
public class ProjectStructureNode<T extends ProjectEntityId> extends DefaultMutableTreeNode
  implements Iterable<ProjectStructureNode<?>> {

  private final Set<ExternalProjectStructureChange> myConflictChanges = ContainerUtilRt.newHashSet();
  private final List<Listener>                      myListeners       = ContainerUtil.createLockFreeCopyOnWriteList();

  @NotNull private final Comparator<ProjectStructureNode<?>> myComparator;
  @NotNull private final ProjectStructureNodeDescriptor<T>   myDescriptor;

  private boolean mySkipNotification;

  /**
   * Creates new <code>ProjectStructureNode</code> object with the given descriptor and 'compare-by-name' comparator.
   *
   * @param descriptor target node descriptor to use within the current node
   */
  public ProjectStructureNode(@NotNull ProjectStructureNodeDescriptor<T> descriptor) {
    this(descriptor, new Comparator<ProjectStructureNode<?>>() {
      @Override
      public int compare(ProjectStructureNode<?> o1, ProjectStructureNode<?> o2) {
        return o1.getDescriptor().getName().compareTo(o2.getDescriptor().getName());
      }
    });
  }

  /**
   * Creates new <code>ProjectStructureNode</code> object with the given descriptor and comparator to use for organising child nodes.
   *
   * @param descriptor target node descriptor to use within the current node
   * @param comparator comparator to use for organising child nodes of the current node
   */
  public ProjectStructureNode(@NotNull ProjectStructureNodeDescriptor<T> descriptor,
                              @NotNull Comparator<ProjectStructureNode<?>> comparator)
  {
    super(descriptor);
    myDescriptor = descriptor;
    myComparator = comparator;
  }

  @NotNull
  public ProjectStructureNodeDescriptor<T> getDescriptor() {
    return myDescriptor;
  }

  @Override
  public ProjectStructureNode<?> getChildAt(int index) {
    return (ProjectStructureNode)super.getChildAt(index);
  }

  @Override
  public ProjectStructureNode<?> getParent() {
    return (ProjectStructureNode)super.getParent();
  }

  @Override
  public void add(MutableTreeNode newChild) {
    for (int i = 0; i < getChildCount(); i++) {
      ProjectStructureNode<?> node = getChildAt(i);
      if (myComparator.compare((ProjectStructureNode<?>)newChild, node) <= 0) {
        insert(newChild, i); // Assuming that the node listeners are notified during the nested call.
        return;
      }
    }
    super.add(newChild); // Assuming that the node listeners are notified during the nested call to 'insert()'.
  }

  @Override
  public void insert(MutableTreeNode newChild, int childIndex) {
    super.insert(newChild, childIndex);
    onNodeAdded((ProjectStructureNode<?>)newChild, childIndex);
  }

  @Override
  public void remove(int childIndex) {
    final ProjectStructureNode<?> child = getChildAt(childIndex);
    super.remove(childIndex);
    onNodeRemoved(child, childIndex);
  }

  @Override
  public void remove(MutableTreeNode aChild) {
    boolean b = mySkipNotification;
    mySkipNotification = true;
    final int index = getIndex(aChild);
    try {
      super.remove(aChild);
    }
    finally {
      mySkipNotification = b;
    }
    onNodeRemoved((ProjectStructureNode<?>)aChild, index);
  }

  /**
   * Asks current node to ensure that given child node is at the 'right position' (according to the {@link #myComparator}.
   * <p/>
   * Does nothing if given node is not a child of the current node.
   *
   * @param child target child node
   * @return <code>true</code> if child position was changed; <code>false</code> otherwise
   */
  public boolean correctChildPositionIfNecessary(@NotNull ProjectStructureNode<?> child) {
    int currentPosition = -1;
    int desiredPosition = -1;
    for (int i = 0; i < getChildCount(); i++) {
      ProjectStructureNode<?> node = getChildAt(i);
      if (node == child) {
        currentPosition = i;
        continue;
      }
      if (desiredPosition < 0 && myComparator.compare(child, node) <= 0) {
        desiredPosition = i;
        if (currentPosition >= 0) {
          break;
        }
      }
    }
    if (currentPosition < 0) {
      // Given node is not a child of the current node.
      return false;
    }
    if (desiredPosition < 0) {
      desiredPosition = getChildCount();
    }
    if (currentPosition < desiredPosition) {
      desiredPosition--;
    }
    if (currentPosition == desiredPosition) {
      return false;
    }
    remove(currentPosition);
    insert(child, desiredPosition);
    return true;
  }

  /**
   * Asks current module to ensure that its children are ordered in accordance with the {@link #myComparator pre-configured comparator}.
   */
  @SuppressWarnings("unchecked")
  public void sortChildren() {
    List<ProjectStructureNode<?>> nodes = new ArrayList<ProjectStructureNode<?>>(children);
    Collections.sort(nodes, myComparator);
    if (nodes.equals(children)) {
      return;
    }

    mySkipNotification = true;
    try {
      removeAllChildren();
      for (ProjectStructureNode<?> node : nodes) {
        add(node);
      }
    }
    finally {
      mySkipNotification = false;
    }
    int[] indices = new int[nodes.size()];
    for (int i = 0; i < indices.length; i++) {
      indices[i] = i;
    }
    onChildrenChange(indices);
  }

  /**
   * Registers given change within the given node assuming that it is
   * {@link ExternalSystemTextAttributes#CHANGE_CONFLICT 'conflict change'}. We need to track number of such changes per-node because
   * of the following possible situation:
   * <pre>
   * <ol>
   *   <li>There are two 'conflict changes' for particular node;</li>
   *   <li>
   *     One of those changes is resolved but the node still should be marked as 'conflict' because there is still one conflict change;
   *   </li>
   *   <li>The second conflict change is removed. The node should be marked as 'no change' now;</li>
   * </ol>
   * </pre>
   *
   * @param change conflict change to register for the current node
   */
  public void addConflictChange(@NotNull ExternalProjectStructureChange change) {
    myConflictChanges.add(change);
    if (myConflictChanges.size() != 1) {
      return;
    }
    final TextAttributesKey key = myDescriptor.getAttributes();
    boolean localNode = key == ExternalSystemTextAttributes.EXTERNAL_SYSTEM_LOCAL_CHANGE || key == ExternalSystemTextAttributes.IDE_LOCAL_CHANGE;
    if (!localNode) {
      myDescriptor.setAttributes(ExternalSystemTextAttributes.CHANGE_CONFLICT);
      onNodeChanged(this);
    }
  }

  @NotNull
  public Set<ExternalProjectStructureChange> getConflictChanges() {
    return myConflictChanges;
  }

  /**
   * Performs reverse operation to {@link #addConflictChange(ExternalProjectStructureChange)}.
   *
   * @param change conflict change to de-register from the current node
   */
  public void removeConflictChange(@NotNull ExternalProjectStructureChange change) {
    myConflictChanges.remove(change);
    if (myConflictChanges.isEmpty()) {
      myDescriptor.setAttributes(ExternalSystemTextAttributes.NO_CHANGE);
      onNodeChanged(this);
    }
  }

  /**
   * Allows to query current node for all children that are associated with the entity of the given type.
   *
   * @param clazz target entity type
   * @param <C>   target entity type
   * @return all children nodes that are associated with the entity of the given type if any;
   *         empty collection otherwise
   */
  @SuppressWarnings("unchecked")
  @NotNull
  public <C extends ProjectEntityId> Collection<ProjectStructureNode<C>> getChildren(@NotNull Class<C> clazz) {
    List<ProjectStructureNode<C>> result = null;
    for (int i = 0; i < getChildCount(); i++) {
      final ProjectStructureNode<?> child = getChildAt(i);
      final Object element = child.getDescriptor().getElement();
      if (!clazz.isInstance(element)) {
        continue;
      }
      if (result == null) {
        result = new ArrayList<ProjectStructureNode<C>>();
      }
      result.add((ProjectStructureNode<C>)child);
    }
    if (result == null) {
      result = Collections.emptyList();
    }
    return result;
  }

  @NotNull
  @Override
  public Iterator<ProjectStructureNode<?>> iterator() {
    return new Iterator<ProjectStructureNode<?>>() {

      private int i;

      @Override
      public boolean hasNext() {
        return i < getChildCount();
      }

      @Override
      public ProjectStructureNode<?> next() {
        return getChildAt(i++);
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }

  public void setAttributes(@NotNull TextAttributesKey key) {
    myDescriptor.setAttributes(key);
    final ProjectStructureNode<?> parent = getParent();
    if (parent == null) {
      onNodeChanged(this);
      return;
    }
    boolean positionChanged = parent.correctChildPositionIfNecessary(this);
    if (!positionChanged) {
      onNodeChanged(this);
    }
  }

  public void addListener(@NotNull Listener listener) {
    myListeners.add(listener);
  }

  private void onNodeAdded(@NotNull ProjectStructureNode<?> node, int index) {
    if (mySkipNotification) {
      return;
    }
    for (Listener listener : myListeners) {
      listener.onNodeAdded(node, index);
    }
  }

  private void onNodeRemoved(@NotNull ProjectStructureNode<?> node, int removedChildIndex) {
    if (mySkipNotification) {
      return;
    }
    for (Listener listener : myListeners) {
      listener.onNodeRemoved(this, node, removedChildIndex);
    }
  }

  private void onNodeChanged(@NotNull ProjectStructureNode<?> node) {
    if (mySkipNotification) {
      return;
    }
    for (Listener listener : myListeners) {
      listener.onNodeChanged(node);
    }
  }

  private void onChildrenChange(@NotNull int[] indices) {
    if (mySkipNotification) {
      return;
    }
    for (Listener listener : myListeners) {
      listener.onNodeChildrenChanged(this, indices);
    }
  }

  public interface Listener {
    void onNodeAdded(@NotNull ProjectStructureNode<?> node, int index);

    void onNodeRemoved(@NotNull ProjectStructureNode<?> parent,
                       @NotNull ProjectStructureNode<?> removedChild,
                       int removedChildIndex);

    void onNodeChanged(@NotNull ProjectStructureNode<?> node);

    void onNodeChildrenChanged(@NotNull ProjectStructureNode<?> parent, int[] childIndices);
  }
}
