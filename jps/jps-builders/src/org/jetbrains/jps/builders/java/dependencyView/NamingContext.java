/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.jps.builders.java.dependencyView;

import org.jetbrains.annotations.Nullable;

public interface NamingContext {

  /**
   * @param val internal integer representation of some name in dependency graph (class name, methot name, field name, etc)
   * @return string representation corresponding to the specified numeric representation
   */
  @Nullable
  String getValue(int val);

  /**
   * @param a string representing any name in dependency graph (class name, methot name, field name, etc)
   * @return unique integer value corresponding to the specified name string
   */
  int get(String s);
}
