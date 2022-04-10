// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * Basic interface for requesting sending focus commands to {@code IdeFocusManager}
 */
public interface FocusRequestor extends Disposable {

  /**
   * Requests focus on a component. Effectively the same as {@link Component#requestFocus()}.
   * <p>
   * WARNING: Using this method is only acceptable in an immediate response to a direct user action (e.g. a mouse or a keyboard event), as
   * it can cause the activation of the component's owning window. If that's done e.g. at the end of some background activity, it can be
   * too unexpected and disruptive for the user. Consider using other, less 'harsh', alternatives, such as:
   * <ul>
   *   <li>{@link IdeFocusManager#requestFocusInProject(Component, Project)}: transfer focus only within the active project's windows</li>
   *   <li>{@link Component#requestFocusInWindow()}: transfer focus only within the active window</li>
   * </ul>
   * @param forced currently ignored
   * @return always {@link ActionCallback#DONE} currently
   */
  @NotNull
  ActionCallback requestFocus(@NotNull Component c, boolean forced);
}
