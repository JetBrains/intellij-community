// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.NullableFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Utility class used to preserve index during 'map' operations
 */
public final class ListSelection<T> {
  private static final Logger LOG = Logger.getInstance(ListSelection.class);

  @NotNull private final List<? extends T> myList;
  private final int mySelectedIndex;
  private final boolean myExplicitSelection;

  private ListSelection(@NotNull List<? extends T> list, int selectedIndex) {
    this(list, selectedIndex, false);
  }

  private ListSelection(@NotNull List<? extends T> list, int selectedIndex, boolean isExplicit) {
    myList = list;
    if (selectedIndex >= 0 && selectedIndex < list.size()) {
      mySelectedIndex = selectedIndex;
    }
    else {
      mySelectedIndex = 0;
    }
    myExplicitSelection = isExplicit;
  }

  @NotNull
  public static <V> ListSelection<V> createAt(@NotNull List<? extends V> list, int selectedIndex) {
    if (list.contains(null)) {
      LOG.error("List selection should not contain nulls");
    }
    return new ListSelection<>(list, selectedIndex);
  }

  @NotNull
  public static <V> ListSelection<V> create(@NotNull List<? extends V> list, @Nullable V selected) {
    return createAt(list, list.indexOf(selected));
  }

  @NotNull
  public static <V> ListSelection<V> create(V @NotNull [] array, V selected) {
    return create(Arrays.asList(array), selected);
  }

  @NotNull
  public static <V> ListSelection<V> createSingleton(@NotNull V element) {
    return createAt(Collections.singletonList(element), 0);
  }

  @NotNull
  public static <V> ListSelection<V> empty() {
    return new ListSelection<>(Collections.emptyList(), -1);
  }


  @NotNull
  public List<? extends T> getList() {
    return myList;
  }

  public int getSelectedIndex() {
    return mySelectedIndex;
  }

  public boolean isEmpty() {
    return myList.isEmpty();
  }

  /**
   * Map all elements in the list and remove elements for which converter returned null.
   * If the selected element was removed, select the last remaining element before it.
   */
  @NotNull
  public <V> ListSelection<V> map(@NotNull NullableFunction<? super T, ? extends V> convertor) {
    int newSelectionIndex = -1;
    List<V> result = new ArrayList<>();
    for (int i = 0; i < myList.size(); i++) {
      if (i == mySelectedIndex) newSelectionIndex = result.size();
      V out = convertor.fun(myList.get(i));
      if (out != null) result.add(out);
    }

    return new ListSelection<>(result, newSelectionIndex, myExplicitSelection);
  }

  @NotNull
  public ListSelection<T> asExplicitSelection() {
    return withExplicitSelection(true);
  }

  /**
   * Pass true if selection was performed explicitly (ex: via multiple selection in JTree)
   * <p>
   * Ex: see {@link com.intellij.openapi.vcs.changes.ui.VcsTreeModelData#getListSelectionOrAll(JTree)},
   * that might implicitly expand empty or single selection.
   */
  @NotNull
  public ListSelection<T> withExplicitSelection(boolean value) {
    return new ListSelection<>(myList, mySelectedIndex, value);
  }

  @NotNull
  public List<? extends T> getExplicitSelection() {
    if (myList.isEmpty()) return Collections.emptyList();
    if (myExplicitSelection) return myList;
    return Collections.singletonList(myList.get(mySelectedIndex));
  }

  public boolean isExplicitSelection() {
    return myExplicitSelection;
  }
}
