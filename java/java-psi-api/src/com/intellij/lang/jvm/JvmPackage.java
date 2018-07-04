// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a package.
 *
 * @see Package
 */
public interface JvmPackage extends JvmAnnotatedElement, JvmNamedElement {

  /**
   * @return the name, or {@code null} for the default package
   */
  @Nullable("default package")
  @Override
  String getName();

  /**
   * @return the fully qualified name, or an empty string for the default package
   */
  @NotNull
  String getQualifiedName();

  /**
   * @return the parent package, or {@code null} for the default package
   */
  @Nullable
  JvmPackage getParentPackage();
}
