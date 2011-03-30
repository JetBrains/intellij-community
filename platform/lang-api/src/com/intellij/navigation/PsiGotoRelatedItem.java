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
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public class PsiGotoRelatedItem extends GotoRelatedItem {

  private final NavigatablePsiElement myElement;

  public PsiGotoRelatedItem(@NotNull NavigatablePsiElement element) {
    myElement = element;
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
    return myElement.getIcon(0);
  }

  @Override
  public PsiFile getContainingFile() {
    return myElement instanceof PsiFile ? null : myElement.getContainingFile();
  }

  @TestOnly
  public NavigatablePsiElement getElement() {
    return myElement;
  }
}
