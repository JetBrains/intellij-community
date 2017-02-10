/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.externalSystem.service.project;

import com.intellij.openapi.externalSystem.service.project.ExternalLibraryPathTypeMapper;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.OrderRootType;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.externalSystem.model.project.LibraryPathType;

import java.util.EnumMap;
import java.util.Map;

/**
 * @author Denis Zhdanov
 * @since 1/17/13 3:55 PM
 */
public class ExternalLibraryPathTypeMapperImpl implements ExternalLibraryPathTypeMapper {

  private static final Map<LibraryPathType, OrderRootType> MAPPINGS = new EnumMap<>(LibraryPathType.class);

  static {
    MAPPINGS.put(LibraryPathType.BINARY, OrderRootType.CLASSES);
    MAPPINGS.put(LibraryPathType.SOURCE, OrderRootType.SOURCES);
    MAPPINGS.put(LibraryPathType.DOC, JavadocOrderRootType.getInstance());
    assert LibraryPathType.values().length == MAPPINGS.size();
  }

  @NotNull
  @Override
  public OrderRootType map(@NotNull LibraryPathType type) {
    return MAPPINGS.get(type);
  }
}
