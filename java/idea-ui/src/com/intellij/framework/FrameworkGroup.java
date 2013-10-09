package com.intellij.framework;

import com.intellij.ide.util.frameworkSupport.FrameworkRole;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

/**
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

  @NotNull
  public List<V> getGroupVersions() {
    return Collections.emptyList();
  }

  public FrameworkRole getRole() {
    return new FrameworkRole(myId);
  }
}
