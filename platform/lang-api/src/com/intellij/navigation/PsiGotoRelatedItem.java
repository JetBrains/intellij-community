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

import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class PsiGotoRelatedItem extends GotoRelatedItem {
  private final NavigatablePsiElement myElement;
  private boolean myShowIcon;

  public static List<PsiGotoRelatedItem> createItems(@NotNull Collection<? extends NavigatablePsiElement> elements) {
    return createItems(elements, true);
  }

  public static List<PsiGotoRelatedItem> createItems(@NotNull Collection<? extends NavigatablePsiElement> elements,
                                                     final boolean showIcon) {
    List<PsiGotoRelatedItem> items = new ArrayList<PsiGotoRelatedItem>(elements.size());
    for (NavigatablePsiElement element : elements) {
      items.add(new PsiGotoRelatedItem(element, showIcon));
    }
    return items;
  }

  public PsiGotoRelatedItem(@NotNull NavigatablePsiElement element) {
    this(element, true);
  }

  public PsiGotoRelatedItem(@NotNull NavigatablePsiElement element, final boolean showIcon) {
    myElement = element;
    myShowIcon = showIcon;
  }

  @Override
  public void navigate() {
    myElement.navigate(true);
  }

  @NotNull
  @Override
  public String getText() {
    return myElement.getName();
  }

  @Override
  public Icon getIcon() {
    return myShowIcon ? myElement.getIcon(0) : null;
  }

  @Override
  public PsiFile getContainingFile() {
    return myElement instanceof PsiFile ? null : myElement.getContainingFile();
  }

  public NavigatablePsiElement getElement() {
    return myElement;
  }
}
