// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.largeFilesEditor.changes;

import java.util.Iterator;

@SuppressWarnings("WeakerAccess")
public class MarkedLinkedList<E> {
  private int size;
  private Node<E> firstElement;
  private Node<E> lastElement;
  private Node<E> markedElement;
  private int markedSize;

  public MarkedLinkedList() {
    size = 0;
    firstElement = null;
    lastElement = null;
    markedElement = null;
    markedSize = 0;
  }

  public void setMarkerToLastElement() {
    if (size == 0) {
      throw new IllegalStateException();
    }
    else {
      markedElement = lastElement;
      markedSize = size;
    }
  }

  public void setMarkerToFirstElement() {
    if (size == 0) {
      throw new IllegalStateException();
    }
    else {
      markedElement = firstElement;
      markedSize = 1;
    }
  }

  public void setMarkerToBegin() {
    markedElement = null;
    markedSize = 0;
  }

  public void setMarkerToEnd() {
    markedElement = lastElement;
    markedSize = size;
  }

  public E getMarkedElement() {
    return markedElement != null ? markedElement.item : null;
  }

  public int getMarkedSize() {
    return markedSize;
  }

  public void moveMarkerLeft() {
    if (markedElement == null) {
      throw new IllegalStateException();
    }

    markedElement = markedElement.prev;
    markedSize--;
  }

  public void moveMarkerRight() {
    if (markedElement == lastElement) {
      throw new IllegalStateException();
    }

    if (markedSize == 0) {
      markedElement = firstElement;
    }
    else {
      markedElement = markedElement.next;
    }
    markedSize++;
  }


  public E getFirstElement() {
    return firstElement != null ? firstElement.item : null;
  }

  public E getLastElement() {
    return lastElement != null ? lastElement.item : null;
  }

  public boolean isEmpty() {
    return size == 0;
  }

  public int getSize() {
    return size;
  }

  public void clear() {
    size = 0;
    firstElement = null;
    lastElement = null;
    markedElement = null;
    markedSize = 0;
  }

  public void addToEnd(E element) {
    if (size == 0) {
      firstElement = new Node<>(null, element, null);
      lastElement = firstElement;
      size = 1;
    }
    else {
      lastElement.next = new Node<>(lastElement, element, null);
      lastElement = lastElement.next;
      size++;
    }
  }

  public void addToMarkedWithCuttingTail(E element) {
    if (markedElement == null) {
      clear();
      addToEnd(element);
    }
    else {
      markedElement.next = new Node<>(markedElement, element, null);
      lastElement = markedElement.next;
      size = markedSize + 1;
    }
  }

  public void addToMarkedWithCuttingTailAndMoveMarkerRight(E element) {
    addToMarkedWithCuttingTail(element);
    moveMarkerRight();
  }

  public void removeNotMarkedLast() {
    if (size == 0) {
      throw new IllegalStateException("getSize is 0");
    }
    if (markedSize == size) {
      throw new IllegalStateException("last is marked");
    }

    lastElement = lastElement.prev;
    size--;
    if (size != 0) {
      lastElement.next = null;
    }
    else {
      firstElement = null;
    }
  }

  public Object[] toArrayTillTheEnd() {
    Object[] array = new Object[size];
    Node node = firstElement;
    for (int i = 0; i < size; i++) {
      array[i] = node.item;
      node = node.next;
    }
    return array;
  }

  public Object[] toArrayTillTheMarkedInclude() {
    Object[] array = new Object[markedSize];
    Node node = firstElement;
    for (int i = 0; i < markedSize; i++) {
      array[i] = node.item;
      node = node.next;
    }
    return array;
  }

  public Iterator<E> iteratorTillMarkedElement() {
    return new IteratorTillMarkedElement();
  }

  private class IteratorTillMarkedElement implements Iterator<E> {

    Node<E> cursor = null;
    int passedItemsAmount = 0;

    @Override
    public boolean hasNext() {
      return passedItemsAmount < MarkedLinkedList.this.markedSize;
    }

    @Override
    public E next() {
      if (passedItemsAmount == 0) {
        passedItemsAmount++;
        cursor = MarkedLinkedList.this.firstElement;
        return cursor.item;
      }
      else {
        passedItemsAmount++;
        cursor = cursor.next;
        return cursor.item;
      }
    }
  }

  private static class Node<E> {
    E item;
    Node<E> next;
    Node<E> prev;

    Node(Node<E> prev, E element, Node<E> next) {
      this.item = element;
      this.next = next;
      this.prev = prev;
    }
  }
}
