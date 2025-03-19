// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.framework;

import com.intellij.ide.util.frameworkSupport.FrameworkRole;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

/**
 * Represents a group of frameworks. in the frameworks tree ("Additional Libraries and Frameworks"
 * in the New Project dialog or the tree displayed in the "Add Framework Support" dialog).
 * Frameworks in a group can have a common version number.
 */
public abstract class FrameworkGroup<V extends FrameworkVersion> implements FrameworkOrGroup {
  private final String myId;

  public FrameworkGroup(String id) {
    myId = id;
  }

  @Override
  public final @NotNull String getId() {
    return myId;
  }

  @Override
  public abstract @NotNull Icon getIcon();

  /**
   * Returns the list of known versions of the framework group. The list is shown to the user
   * and allows them to choose the version used in their project.
   */
  public @NotNull List<V> getGroupVersions() {
    return Collections.emptyList();
  }

  public FrameworkRole getRole() {
    return new FrameworkRole(myId);
  }
}
