package org.jetbrains.jps.model.impl;

import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.*;

import java.util.*;

/**
 * @author nik
 */
public class JpsElementCollectionImpl<E extends JpsElement> extends JpsElementBase<JpsElementCollectionImpl<E>> implements JpsElementCollection<E> {
  private final List<E> myElements;
  private final JpsModel myModel;
  private final Map<E, E> myCopyToOriginal;
  private final JpsElementKind<E> myKind;

  public JpsElementCollectionImpl(JpsElementKind<E> kind, JpsModel model, JpsEventDispatcher eventDispatcher, JpsParentElement parent) {
    super(eventDispatcher, parent);
    myKind = kind;
    myModel = model;
    myElements = new SmartList<E>();
    myCopyToOriginal = null;
  }

  public JpsElementCollectionImpl(JpsElementCollectionImpl<E> original,
                                  JpsModel model,
                                  JpsEventDispatcher eventDispatcher,
                                  JpsParentElement parent) {
    super(original, eventDispatcher, parent);
    myKind = original.myKind;
    myModel = model;
    myElements = new SmartList<E>();
    myCopyToOriginal = new HashMap<E, E>();
    for (E e : original.myElements) {
      //noinspection unchecked
      final E copy = (E)e.getBulkModificationSupport().createCopy(model, eventDispatcher, parent);
      myElements.add(copy);
      myCopyToOriginal.put(copy, e);
    }
  }

  @Override
  public List<E> getElements() {
    return myElements;
  }

  @NotNull
  @Override
  public <P> E addChild(@NotNull JpsElementFactoryWithParameter<E, P> factory, @NotNull P param) {
    return addChild(factory.create(myModel, getEventDispatcher(), myParent, param));
  }

  @NotNull
  @Override
  public E addChild(@NotNull JpsElementFactory<E> factory) {
    return addChild(factory.create(myModel, getEventDispatcher(), myParent));
  }

  @Override
  public E addChild(E element) {
    myElements.add(element);
    getEventDispatcher().fireElementAdded(element, myKind);
    return element;
  }

  @Override
  public void removeChild(@NotNull E element) {
    final boolean removed = myElements.remove(element);
    if (removed) {
      getEventDispatcher().fireElementRemoved(element, myKind);
    }
  }

  @NotNull
  @Override
  public JpsElementCollectionImpl<E> createCopy(@NotNull JpsModel model,
                                                @NotNull JpsEventDispatcher eventDispatcher,
                                                JpsParentElement parent) {
    return new JpsElementCollectionImpl<E>(this, model, eventDispatcher, parent);
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
        final E copy = (E)element.getBulkModificationSupport().createCopy(myModel, getEventDispatcher(), myParent);
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
