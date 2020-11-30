// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.project;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.model.project.LibraryPathType;
import com.intellij.openapi.roots.OrderRootType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Strategy for mapping {@link LibraryPathType external library path types} to {@link OrderRootType ide library path types}.
 * <p/>
 * Is introduced because {@code 'JavadocOrderRootType.getInstance()'} assumes that project IoC is setup thus
 * make it ineligible for unit testing.
 * <p/>
 * Implementations of this interface are expected to be thread-safe.
 *
 * @author Denis Zhdanov
 */
public interface ExternalLibraryPathTypeMapper {
  @Nullable
  OrderRootType map(@NotNull LibraryPathType type);

  static ExternalLibraryPathTypeMapper getInstance() {
    return ApplicationManager.getApplication().getService(ExternalLibraryPathTypeMapper.class);
  }
}
