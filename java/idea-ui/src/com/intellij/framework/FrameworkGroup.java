package com.intellij.framework;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public abstract class FrameworkGroup<V extends FrameworkGroupVersion> {
  private final String myId;

  public FrameworkGroup(String id) {
    myId = id;
  }

  @NotNull
  public final String getId() {
    return myId;
  }

  @NotNull
  public abstract String getPresentableName();

  @NotNull
  public abstract Icon getIcon();

  @NotNull
  public List<V> getGroupVersions() {
    return Collections.emptyList();
  }
}
