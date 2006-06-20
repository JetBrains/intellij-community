/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.compiler.impl;

import com.intellij.util.containers.StringInterner;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.Stack;

/**
 * @author Eugene Zhuravlev
 *         Date: Jun 18, 2006
 */
public class TreeBasedMap<T> {
  private Node<T> myRoot = new Node<T>();
  private StringInterner myInterner;
  private final char mySeparator;
  private int mySize = 0;

  public TreeBasedMap(StringInterner table, final char separator) {
    myInterner = table;
    mySeparator = separator;
  }

  private class Node<T> {
    private boolean myMappingExists = false;
    private T myValue = null;
    private @Nullable THashMap<String, Node<T>> myChildren = null;

    public void setValue(T value) {
      myValue = value;
      myMappingExists = true;
    }

    public T getValue() {
      return myValue;
    }

    public void removeValue() {
      myValue = null;
      myMappingExists = false;
    }

    public boolean mappingExists() {
      return myMappingExists;
    }

    @Nullable
    public Node<T> findRelative(String text, boolean create, final StringInterner table) {
      return findRelative(text, 0, create, table);
    }

    @Nullable
    private Node<T> findRelative(final String text, final int nameStartIndex, final boolean create, final StringInterner table) {
      if (myChildren == null && !create) {
        return null;
      }

      final int textLen = text.length();
      final int separatorIdx = text.indexOf(mySeparator, nameStartIndex);
      final int childNameEnd = separatorIdx >= 0 ? separatorIdx : textLen;

      if (myChildren != null) {
        final Node<T> child = myChildren.get(text.substring(nameStartIndex, childNameEnd));
        if (child != null) {
          if (separatorIdx < 0) {
            return child;
          }
          return child.findRelative(text, childNameEnd + 1, create, table);
        }
      }

      if (create) {
        return addChild(table, text, nameStartIndex, childNameEnd);
      }

      return null;
    }

    @NotNull
    private Node<T> addChild(final StringInterner table, final String text, final int nameStartIndex, final int nameEndIndex) {
      if (myChildren == null) {
        myChildren = new THashMap<String, Node<T>>(3, 0.95f);
      }

      Node<T> newChild = new Node<T>();
      final String key = table.intern(text.substring(nameStartIndex, nameEndIndex));
      myChildren.put(key, newChild);

      if (nameEndIndex == text.length()) {
        return newChild;
      }

      Node<T> restNodes = newChild.findRelative(text, nameEndIndex + 1, true, table);
      assert restNodes != null;
      return restNodes;
    }
  }

  public void put(String key, T value) {
    final Node<T> node = myRoot.findRelative(key, true, myInterner);
    assert node != null;
    node.setValue(value);
    mySize++;
  }

  public void remove(String key) {
    final Node node = myRoot.findRelative(key, false, myInterner);
    if (node != null && node.mappingExists()) {
      node.removeValue();
      mySize--;
    }
  }

  public int size() {
    return mySize;
  }

  public T get(String key) {
    final Node<T> node = myRoot.findRelative(key, false, myInterner);
    return node != null ? node.getValue() : null;
  }

  public boolean containsKey(String key) {
    final Node<T> node = myRoot.findRelative(key, false, myInterner);
    return node != null && node.mappingExists();
  }

  public void removeAll() {
    myRoot = new Node<T>();
  }

  public Iterator<String> getKeysIterator() {
    return new KeysIterator();
  }


  private class KeysIterator implements Iterator<String> {
    private Stack<PathElement<T>> myCurrentNodePath = new Stack<PathElement<T>>();
    private StringBuilder myCurrentName = new StringBuilder();

    public KeysIterator() {
      pushNode("", myRoot);
      findNextNode();
    }

    public boolean hasNext() {
      return myCurrentNodePath.size() > 0;
    }

    public String next() {
      final String key = myCurrentName.toString();
      popNode();
      findNextNode();
      return key;
    }

    public void remove() {
      throw new UnsupportedOperationException("Remove not supported");
    }

    private boolean pushNode(final @NotNull String name, @NotNull Node<T> node) {
      final THashMap<String, Node<T>> childrenMap = node.myChildren;
      if ((childrenMap != null && childrenMap.size() > 0) || node.mappingExists()) {
        final Set<String> keys = childrenMap != null? childrenMap.keySet() : Collections.<String>emptySet();
        myCurrentNodePath.push(new PathElement<T>(node, keys.size() > 0? keys.iterator() : EMPTY_ITERATOR));
        if (myCurrentName.length() > 0) {
          myCurrentName.append(mySeparator);
        }
        myCurrentName.append(name);
        return true;
      }
      return false;
    }

    private void popNode() {
      myCurrentNodePath.pop();
      final int separatorIndex = myCurrentName.lastIndexOf(String.valueOf(mySeparator));
      if (separatorIndex >= 0) {
        myCurrentName.replace(separatorIndex, myCurrentName.length(), "");
      }
      else {
        myCurrentName.setLength(0);
      }
    }

    private void findNextNode() {
      if (myCurrentNodePath.size() == 0) {
        return;
      }
      final PathElement<T> element = myCurrentNodePath.peek();
      while (element.iterator.hasNext()) {
        final String name = element.iterator.next();
        final Node<T> childNode = element.node.myChildren.get(name);
        if (pushNode(name, childNode)) {
          findNextNode();
          return;
        }
      }
      if (!element.node.mappingExists()) {
        popNode();
        findNextNode();
      }
    }
  }

  private class PathElement<T> {
    final @NotNull Iterator<String> iterator;
    final @NotNull Node<T> node;
    public PathElement(@NotNull final Node<T> node, Iterator<String> iterator) {
      this.node = node;
      this.iterator = iterator;
    }
  }

  public static final Iterator<String> EMPTY_ITERATOR = new Iterator<String>() {
    public boolean hasNext() {
      return false;
    }
    public String next() {
      return null;
    }
    public void remove() {
    }
  };

}
