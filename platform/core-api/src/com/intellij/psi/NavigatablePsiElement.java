// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.navigation.NavigationItem;

/**
 * @author yole
 */
public interface NavigatablePsiElement extends PsiElement, NavigationItem {
  NavigatablePsiElement[] EMPTY_NAVIGATABLE_ELEMENT_ARRAY = new NavigatablePsiElement[0];
}
