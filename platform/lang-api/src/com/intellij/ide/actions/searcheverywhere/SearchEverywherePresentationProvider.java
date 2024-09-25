// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.platform.backend.presentation.TargetPresentation;


/**
 * This interface is designed to be implemented by {@link javax.swing.ListCellRenderer} that are returned by {@link SearchEverywhereContributor}.
 * It mainly caters to the cases where returned items utilize operations that are slow in execution, and hence, need to be computed
 * in the background to prevent freezing of the interface. ListCellRenderers are required to implement this interface when the items returned
 * are not implementations of either the PsiElement or the NavigationItem. Implementing this interface ensures that the correct TargetPresentation
 * is returned for such items.
 */
public interface SearchEverywherePresentationProvider<T> {
  TargetPresentation getTargetPresentation(T element);
}
