/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.platform.loader.repository;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public final class RuntimeModuleId {
  public static final String LIB_NAME_PREFIX = "lib.";
  private final String myStringId;

  private RuntimeModuleId(@NotNull String stringId) {
    myStringId = stringId;
  }

  @NotNull
  public String getStringId() {
    return myStringId;
  }

  public static RuntimeModuleId module(@NotNull String moduleName) {
    return new RuntimeModuleId(moduleName);
  }

  public static RuntimeModuleId moduleTests(@NotNull String moduleName) {
    return new RuntimeModuleId(moduleName + ".tests");
  }

  public static RuntimeModuleId projectLibrary(@NotNull String libraryName) {
    return new RuntimeModuleId(LIB_NAME_PREFIX + libraryName);
  }

  public static RuntimeModuleId moduleLibrary(@NotNull String moduleName, @NotNull String libraryName) {
    return new RuntimeModuleId(LIB_NAME_PREFIX + moduleName + "." + libraryName);
  }

  @Override
  public String toString() {
    return "RuntimeModuleId[" + myStringId + "]";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    return myStringId.equals(((RuntimeModuleId)o).myStringId);
  }

  @Override
  public int hashCode() {
    return myStringId.hashCode();
  }
}
