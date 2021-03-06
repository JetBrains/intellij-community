// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.util.ui.update.ComparableObject;
import com.intellij.util.ui.update.ComparableObjectCheck;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class UiActivity implements ComparableObject {
  private final List<String> myElements = new ArrayList<>();

  public UiActivity(@NonNls String @NotNull ... elements) {
    this(elements, null);
  }

  protected UiActivity(@NonNls String @NotNull [] elements1, @NonNls String @Nullable [] elements2) {
    myElements.addAll(Arrays.asList(elements1));
    if (elements2 != null) {
      myElements.addAll(Arrays.asList(elements2));
    }
  }

  @Override
  public Object @NotNull [] getEqualityObjects() {
    return new Object[] {myElements};
  }

  @Override
  public String toString() {
    return "UiActivity: " + myElements;
  }

  @Override
  public int hashCode() {
    return ComparableObjectCheck.hashCode(this, super.hashCode());
  }

  @Override
  public boolean equals(Object obj) {
    return ComparableObjectCheck.equals(this, obj);
  }

  public final boolean isSameOrGeneralFor(UiActivity other) {
    if (myElements.size() > other.myElements.size()) return false;

    for (int i = 0; i < myElements.size(); i++) {
      if (!myElements.get(i).equals(other.myElements.get(i))) return false;
    }

    return true;
  }

  public static class Focus extends UiActivity {

    public Focus(String ... subOperation) {
      super(new String[] {"focus"}, subOperation);
    }

  }

  public static class AsyncBgOperation extends UiActivity {
    public AsyncBgOperation(String operationName) {
      super("asyncBgOperation", operationName);
    }
  }

  public static class Progress extends UiActivity {
    public Progress(String type, String id) {
      super("progress", type, id);
    }

    public static class Modal extends Progress {
      public Modal(String id) {
        super("modal", id);
      }
    }
  }
}
