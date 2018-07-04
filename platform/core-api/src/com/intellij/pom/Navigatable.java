// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.pom;

/**
 * Represents an instance which can be shown in the IDE (e.g. a file, a specific location inside a file, etc).
 * <p/>
 * Many {@link com.intellij.psi.PsiElement}s implement this interface (see {@link com.intellij.psi.NavigatablePsiElement}). To create an
 * instance which opens a file in editor and put caret to a specific location use {@link com.intellij.openapi.fileEditor.OpenFileDescriptor}.
 */
public interface Navigatable {
  /**
   * Open editor and select/navigate to the object there if possible.
   * Just do nothing if navigation is not possible like in case of a package
   *
   * @param requestFocus {@code true} if focus requesting is necessary
   */
  void navigate(boolean requestFocus);

  /**
   * Indicates whether this instance supports navigation of any kind.
   * Usually this method is called to ensure that navigation is possible.
   * Note that it is not called if navigation to source is supported,
   * i.e. {@link #canNavigateToSource()} returns {@code true}.
   * We assume that this method should return {@code true} in such case,
   * so implement this method respectively.
   *
   * @return {@code false} if navigation is not possible for any reason.
   */
  boolean canNavigate();

  /**
   * Indicates whether this instance supports navigation to source (that means some kind of editor).
   * Note that navigation can be supported even if this method returns {@code false}.
   * In such cases it is not recommended to do batch navigation for all navigatables
   * available via {@link com.intellij.openapi.actionSystem.CommonDataKeys#NAVIGATABLE_ARRAY},
   * because it may lead to opening several modal dialogs.
   * Use {@link com.intellij.util.OpenSourceUtil#navigate} to process such arrays correctly.
   *
   * @return {@code false} if navigation to source is not possible for any reason.
   */
  boolean canNavigateToSource();
}
