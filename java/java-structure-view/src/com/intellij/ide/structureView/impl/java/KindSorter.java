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
package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.util.treeView.smartTree.ActionPresentation;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

public class KindSorter implements Sorter {
  public static final Sorter INSTANCE = new KindSorter(false);
  public static final Sorter POPUP_INSTANCE = new KindSorter(true);

  public KindSorter(boolean isPopup) {
    this.isPopup = isPopup;
  }

  @NonNls public static final String ID = "KIND";
  private final boolean isPopup;

  private final Comparator COMPARATOR = new Comparator() {
    @Override
    public int compare(final Object o1, final Object o2) {
      return getWeight(o1) - getWeight(o2);
    }

    private int getWeight(final Object value) {
      if (value instanceof JavaAnonymousClassTreeElement) {
        return 55;
      }
      if (value instanceof JavaClassTreeElement) {
        return isPopup ? 53 : 10;
      }
      if (value instanceof ClassInitializerTreeElement) {
        return 15;
      }
      if (value instanceof SuperTypeGroup) {
        return 20;
      }
      if (value instanceof PsiMethodTreeElement) {
        final PsiMethodTreeElement methodTreeElement = (PsiMethodTreeElement)value;
        final PsiMethod method = methodTreeElement.getMethod();

        return method.isConstructor() ? 30 : 35;
      }
      if (value instanceof PropertyGroup) {
        return 40;
      }
      if (value instanceof PsiFieldTreeElement) {
        return 50;
      }
      return 60;
    }
  };

  @Override
  @NotNull
  public Comparator getComparator() {
    return COMPARATOR;
  }

  @Override
  public boolean isVisible() {
    return false;
  }

  @Override
  @NotNull
  public ActionPresentation getPresentation() {
    throw new IllegalStateException();
  }

  @Override
  @NotNull
  public String getName() {
    return ID;
  }
}
