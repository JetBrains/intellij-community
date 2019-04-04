/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.framework;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author nik
 */
public abstract class FrameworkType {
  private final String myId;

  protected FrameworkType(@NotNull String id) {
    myId = id;
  }

  @NotNull
  public abstract String getPresentableName();

  @NotNull
  public abstract Icon getIcon();

  @NotNull
  public final String getId() {
    return myId;
  }

  @Override
  public final boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    return myId.equals(((FrameworkType)o).myId);
  }

  @Override
  public final int hashCode() {
    return myId.hashCode();
  }
}
