// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.lang;

import com.intellij.codeInspection.HTMLComposer;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.lang.Language;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An extension to output HTML (see {@link HTMLComposer}).
 */
public interface HTMLComposerExtension<T> {
  Key<T> getID();
  Language getLanguage();

  /**
   * Appends HTML corresponding to the {@link RefEntity} short name.
   */
  void appendShortName(RefEntity entity, @NotNull StringBuilder buf);

  /**
   * Appends HTML corresponding to the {@link RefEntity} location.
   */
  void appendLocation(RefEntity entity, @NotNull StringBuilder buf);

  /**
   * Returns the qualified name of the given {@link RefEntity}.
   */
  @Nullable
  String getQualifiedName(RefEntity entity);

  /**
   * Appends HTML presenting the {@link RefEntity}.
   */
  void appendReferencePresentation(RefEntity entity, @NotNull StringBuilder buf, final boolean isPackageIncluded);
}
