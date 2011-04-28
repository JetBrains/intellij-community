/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
 */
public class GotoRelatedItem {

  private final PsiElement myElement;

  public GotoRelatedItem(@NotNull PsiElement element) {
    myElement = element;
  }

  protected GotoRelatedItem() {
    myElement = null;
  }

  public void navigate() {
    PsiNavigateUtil.navigate(myElement);
  }

  @Nullable
  public String getCustomName() {
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

  public static List<GotoRelatedItem> createItems(@NotNull Collection<? extends PsiElement> elements) {
    List<GotoRelatedItem> items = new ArrayList<GotoRelatedItem>(elements.size());
    for (PsiElement element : elements) {
      items.add(new GotoRelatedItem(element));
    }
    return items;
  }
}
