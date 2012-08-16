package org.jetbrains.jps.model.impl;

import com.intellij.openapi.util.Condition;
import com.intellij.util.SmartList;
import com.intellij.util.containers.FilteringIterator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.*;

import java.util.*;

/**
 * @author nik
 */
public class JpsElementCollectionImpl<E extends JpsElement> extends JpsElementBase<JpsElementCollectionImpl<E>> implements JpsElementCollection<E> {
  private final List<E> myElements;
  private final Map<E, E> myCopyToOriginal;
  private final JpsElementChildRole<E> myChildRole;

  public JpsElementCollectionImpl(JpsElementChildRole<E> role) {
    myChildRole = role;
    myElements = new SmartList<E>();
    myCopyToOriginal = null;
  }

  public JpsElementCollectionImpl(JpsElementCollectionImpl<E> original) {
    myChildRole = original.myChildRole;
    myElements = new SmartList<E>();
    myCopyToOriginal = new HashMap<E, E>();
    for (E e : original.myElements) {
      //noinspection unchecked
      final E copy = (E)e.getBulkModificationSupport().createCopy();
      setParent(copy, this);
      myElements.add(copy);
      myCopyToOriginal.put(copy, e);
    }
  }

  @Override
  public List<E> getElements() {
    return myElements;
  }

  public <X extends E, P extends JpsElement> Iterable<X> getElementsOfType(@NotNull final JpsElementType<P> type) {
    return new Iterable<X>() {
      @Override
      public Iterator<X> iterator() {
        return new FilteringIterator<E, X>(myElements.iterator(), new Condition<E>() {
          @Override
          public boolean value(E e) {
            return e instanceof JpsTypedElement && ((JpsTypedElement<?>)e).getType().equals(type);
          }
        });
      }
    };
  }

  @NotNull
  @Override
  public E addChild(@NotNull JpsElementCreator<E> creator) {
    return addChild(creator.create());
  }

  @Override
  public <X extends E> X addChild(X element) {
    myElements.add(element);
    setParent(element, this);
    final JpsEventDispatcher eventDispatcher = getEventDispatcher();
    if (eventDispatcher != null) {
      eventDispatcher.fireElementAdded(element, myChildRole);
    }
    return element;
  }

  @Override
  public void removeChild(@NotNull E element) {
    final boolean removed = myElements.remove(element);
    if (removed) {
      final JpsEventDispatcher eventDispatcher = getEventDispatcher();
      if (eventDispatcher != null) {
        eventDispatcher.fireElementRemoved(element, myChildRole);
      }
      setParent(element, null);
    }
  }

  public void removeAllChildren() {
    List<E> elements = new ArrayList<E>(myElements);
    for (E element : elements) {
      removeChild(element);
    }
  }

  @NotNull
  @Override
  public JpsElementCollectionImpl<E> createCopy() {
    return new JpsElementCollectionImpl<E>(this);
  }

  public void applyChanges(@NotNull JpsElementCollectionImpl<E> modified) {
    Set<E> toRemove = new LinkedHashSet<E>(myElements);
    List<E> toAdd = new ArrayList<E>();
    final Map<E, E> copyToOriginal = modified.myCopyToOriginal;
    for (E element : modified.myElements) {
      final E original = copyToOriginal != null ? copyToOriginal.get(element) : null;
      if (original != null) {
        //noinspection unchecked
        ((BulkModificationSupport<E>)original.getBulkModificationSupport()).applyChanges(element);
        toRemove.remove(original);
      }
      else {
        //noinspection unchecked
        final E copy = (E)element.getBulkModificationSupport().createCopy();
        toAdd.add(copy);
      }
    }
    for (E e : toRemove) {
      removeChild(e);
    }
    for (E e : toAdd) {
      addChild(e);
    }
  }
}
