/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide;

import com.intellij.util.ui.update.ComparableObject;
import com.intellij.util.ui.update.ComparableObjectCheck;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class UiActivity implements ComparableObject{
  
  private final List<String> myElements = new ArrayList<>();

  public UiActivity(@NotNull String ... elements) {
    this(elements, null);
  }

  protected UiActivity(@NotNull String[] elements1, @Nullable String[] elements2) {
    myElements.addAll(Arrays.asList(elements1));
    if (elements2 != null) {
      myElements.addAll(Arrays.asList(elements2));
    }
  }

  @NotNull
  @Override
  public Object[] getEqualityObjects() {
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
