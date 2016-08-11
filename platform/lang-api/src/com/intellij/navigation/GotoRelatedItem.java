/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.navigation;

import com.intellij.psi.PsiElement;
import com.intellij.util.PsiNavigateUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Dmitry Avdeev
 * @author Konstantin Bulenkov
 */
public class GotoRelatedItem {
  private final String myGroup;
  private final int myMnemonic;
  private final PsiElement myElement;
  public static final String DEFAULT_GROUP_NAME = "";

  protected GotoRelatedItem(@Nullable PsiElement element, String group, final int mnemonic) {
    myElement = element;
    myGroup = group;
    myMnemonic = mnemonic;
  }
  
  public GotoRelatedItem(@NotNull PsiElement element, String group) {
    this(element, group, -1);
  }

  public GotoRelatedItem(@NotNull PsiElement element) {
    this(element, DEFAULT_GROUP_NAME);
  }

  public void navigate() {
    PsiNavigateUtil.navigate(myElement);
  }

  @Nullable
  public String getCustomName() {
    return null;
  }

  @Nullable
  public String getCustomContainerName() {
    return null;
  }

  @Nullable
  public Icon getCustomIcon() {
    return null;
  }

  @Nullable
  public PsiElement getElement() {
    return myElement;
  }

  public int getMnemonic() {
    return myMnemonic;
  }
  public static List<GotoRelatedItem> createItems(@NotNull Collection<? extends PsiElement> elements) {
    return createItems(elements, DEFAULT_GROUP_NAME);
  }

  public static List<GotoRelatedItem> createItems(@NotNull Collection<? extends PsiElement> elements, String group) {
    List<GotoRelatedItem> items = new ArrayList<>(elements.size());
    for (PsiElement element : elements) {
      items.add(new GotoRelatedItem(element, group));
    }
    return items;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GotoRelatedItem item = (GotoRelatedItem)o;

    if (myElement != null ? !myElement.equals(item.myElement) : item.myElement != null) return false;

    return true;
  }

  public String getGroup() {
    return myGroup;
  }

  @Override
  public int hashCode() {
    return myElement != null ? myElement.hashCode() : 0;
  }
}
