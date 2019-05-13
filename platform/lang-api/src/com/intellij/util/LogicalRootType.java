/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.util;

import org.jetbrains.annotations.NonNls;

/**
 * @author spleaner
 */
public class LogicalRootType<T extends LogicalRoot> {
  public static final LogicalRootType<VirtualFileLogicalRoot> SOURCE_ROOT = create("Source root");
  private final String myName;


  private LogicalRootType(final String name) {
    myName = name;
  }

  @NonNls
  public String toString() {
    return "Logical root type:" + myName;
  }

  public static <T extends LogicalRoot> LogicalRootType<T> create(@NonNls String name) {
    return new LogicalRootType<>(name);
  }
}
