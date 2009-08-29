package com.intellij.packaging.elements;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public interface RenameablePackagingElement {
  String getName();

  boolean canBeRenamed();

  void rename(@NotNull String newName);
}
