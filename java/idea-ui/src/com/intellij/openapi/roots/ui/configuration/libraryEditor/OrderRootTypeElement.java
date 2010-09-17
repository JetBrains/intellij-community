/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.openapi.roots.OrderRootType;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class OrderRootTypeElement extends LibraryTableTreeContentElement {
  private final OrderRootType myRootType;

  public OrderRootTypeElement(@NotNull OrderRootType rootType) {
    myRootType = rootType;
  }

  @NotNull
  public OrderRootType getOrderRootType() {
    return myRootType;
  }

  @Override
  public int hashCode() {
    return myRootType.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof OrderRootTypeElement && ((OrderRootTypeElement)obj).getOrderRootType().equals(myRootType);
  }
}
