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
 *
 * @author nik
 */
public abstract class FrameworkGroup<V extends FrameworkVersion> implements FrameworkOrGroup {
  private final String myId;

  public FrameworkGroup(String id) {
    myId = id;
  }

  @Override
  @NotNull
  public final String getId() {
    return myId;
  }

  @NotNull
  public abstract Icon getIcon();

  /**
   * Returns the list of known versions of the framework group. The list is shown to the user
   * and allows them to choose the version used in their project.
   */
  @NotNull
  public List<V> getGroupVersions() {
    return Collections.emptyList();
  }

  public FrameworkRole getRole() {
    return new FrameworkRole(myId);
  }
}
