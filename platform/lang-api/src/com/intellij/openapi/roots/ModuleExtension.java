/*
 * User: anna
 * Date: 27-Dec-2007
 */
package com.intellij.openapi.roots;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

public abstract class ModuleExtension<T extends ModuleExtension> implements JDOMExternalizable, Disposable, Comparable<ModuleExtension> {
  public static final ExtensionPointName<ModuleExtension> EP_NAME = ExtensionPointName.create("com.intellij.moduleExtension");

  public abstract ModuleExtension getModifiableModel(final boolean writable);

  public abstract void commit();

  public abstract boolean isChanged();

  @Nullable
  public VirtualFile[] getRootPaths(OrderRootType type) {
    return null;
  }

  @Nullable
  public String[] getRootUrls(OrderRootType type) {
    return null;
  }

  public int compareTo(final ModuleExtension o) {
    return getClass().getName().compareTo(o.getClass().getName());
  }
}