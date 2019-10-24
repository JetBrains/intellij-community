// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.lang;

import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.lang.Language;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface HTMLComposerExtension<T> {
  Key<T> getID();
  Language getLanguage();

  void appendShortName(RefEntity entity, @NotNull StringBuilder buf);

  void appendLocation(RefEntity entity, @NotNull StringBuilder buf);

  @Nullable
  String getQualifiedName(RefEntity entity);

  void appendReferencePresentation(RefEntity entity, @NotNull StringBuilder buf, final boolean isPackageIncluded);
}
