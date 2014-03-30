package com.intellij.openapi.externalSystem.model.project;

import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Comparator;

/**
 * @author Denis Zhdanov
 * @since 8/12/11 12:34 PM
 */
public interface Named {

  /**
   * please use {@link #getExternalName()} or {@link #getInternalName()} instead
   */
  @NotNull
  @Deprecated
  String getName();

  /**
   * please use {@link #setExternalName(String)} or {@link #setInternalName(String)} instead
   */
  @Deprecated
  void setName(@NotNull String name);

  @NotNull
  String getExternalName();
  void setExternalName(@NotNull String name);

  @NotNull
  String getInternalName();
  void setInternalName(@NotNull String name);
}
