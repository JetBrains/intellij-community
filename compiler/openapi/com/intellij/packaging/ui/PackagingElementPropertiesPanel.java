package com.intellij.packaging.ui;

import com.intellij.packaging.elements.PackagingElement;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author nik
 */
public abstract class PackagingElementPropertiesPanel<E extends PackagingElement<?>> {

  @NotNull
  public abstract JComponent getComponent();

  public boolean isAvailable(@NotNull E element) {
    return true;
  }

  public abstract void loadFrom(@NotNull E element);

  public abstract boolean isModified(@NotNull E original);

  public abstract void saveTo(@NotNull E element);
}
