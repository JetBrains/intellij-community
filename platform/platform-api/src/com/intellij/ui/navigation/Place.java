/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.intellij.ui.navigation;

import com.intellij.openapi.util.ActionCallback;
import com.intellij.util.ui.update.ComparableObject;
import com.intellij.util.ui.update.ComparableObjectCheck;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.LinkedHashMap;

public class Place implements ComparableObject {

  private LinkedHashMap<String, Object> myPath = new LinkedHashMap<>();

  @Override
  @NotNull
  public final Object[] getEqualityObjects() {
    return new Object[] {myPath};
  }

  @Override
  public final boolean equals(final Object obj) {
    return ComparableObjectCheck.equals(this, obj);
  }

  @Override
  public final int hashCode() {
    return ComparableObjectCheck.hashCode(this, super.hashCode());
  }

  @NotNull
  public Place putPath(String name, Object value) {
    myPath.put(name, value);
    return this;
  }

  @Nullable
  public
  Object getPath(String name) {
    return myPath.get(name);
  }

  public Place cloneForElement(final String name, Object value) {
    final Place clone = new Place();
    clone.myPath = (LinkedHashMap<String, Object>)myPath.clone();
    clone.myPath.put(name, value);
    return clone;
  }

  public void copyFrom(final Place from) {
    myPath = (LinkedHashMap<String, Object>)from.myPath.clone();
  }

  public boolean isMoreGeneralFor(final Place place) {
    if (myPath.size() >= place.myPath.size()) return false;

    final Iterator<String> thisIterator = myPath.keySet().iterator();
    final Iterator<String> otherIterator = place.myPath.keySet().iterator();

    while (thisIterator.hasNext()) {
      String thisKey = thisIterator.next();
      String otherKey = otherIterator.next();
      if (thisKey == null || !thisKey.equals(otherKey)) return false;

      final Object thisValue = myPath.get(thisKey);
      final Object otherValue = place.myPath.get(otherKey);

      if (thisValue == null || !thisValue.equals(otherValue)) return false;

    }

    return true;
  }

  public interface Navigator {
    default void setHistory(History history) {
    }

    ActionCallback navigateTo(@Nullable Place place, final boolean requestFocus);

    default void queryPlace(@NotNull Place place) {
    }
  }

  public static ActionCallback goFurther(Object object, Place place, final boolean requestFocus) {
    if (object instanceof Navigator) {
      return ((Navigator)object).navigateTo(place, requestFocus);
    }
    return ActionCallback.DONE;
  }

  public static void queryFurther(final Object object, final Place place) {
    if (object instanceof Navigator) {
      ((Navigator)object).queryPlace(place);
    }
  }

}
