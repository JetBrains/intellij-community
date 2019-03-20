// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.largeFilesEditor.changes;

import com.intellij.util.containers.EmptyIterator;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

public class PageValidChangesList<E extends Change> implements Iterable<E> {

  private MarkedLinkedList<E> list;

  PageValidChangesList(MarkedLinkedList<E> list) {
    this.list = list;
  }

  @NotNull
  @Override
  public Iterator<E> iterator() {
    return list != null ? list.iteratorTillMarkedElement() : new EmptyIterator<>();
  }
}