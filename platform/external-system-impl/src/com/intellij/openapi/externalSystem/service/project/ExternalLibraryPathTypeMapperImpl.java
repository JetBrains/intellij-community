// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project;

import com.intellij.openapi.externalSystem.model.project.LibraryPathType;
import com.intellij.openapi.roots.AnnotationOrderRootType;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.OrderRootType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

import static java.util.Arrays.stream;

@ApiStatus.Internal
public class ExternalLibraryPathTypeMapperImpl implements ExternalLibraryPathTypeMapper {

  private static final Map<LibraryPathType, OrderRootType> MAPPINGS = new EnumMap<>(LibraryPathType.class);

  static {
    MAPPINGS.put(LibraryPathType.BINARY, OrderRootType.CLASSES);
    MAPPINGS.put(LibraryPathType.SOURCE, OrderRootType.SOURCES);
    OrderRootType docRootType = stream(OrderRootType.getAllTypes()).anyMatch(JavadocOrderRootType.class::isInstance)
                                ? JavadocOrderRootType.getInstance() : OrderRootType.DOCUMENTATION;
    MAPPINGS.put(LibraryPathType.DOC, docRootType);
    stream(OrderRootType.getAllTypes())
      .filter(AnnotationOrderRootType.class::isInstance)
      .findFirst()
      .ifPresent(type -> MAPPINGS.put(LibraryPathType.ANNOTATION, type));
  }

  @Override
  public @Nullable OrderRootType map(@NotNull LibraryPathType type) {
    return MAPPINGS.get(type);
  }
}
