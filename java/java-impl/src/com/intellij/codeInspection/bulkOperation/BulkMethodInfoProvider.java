/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInspection.bulkOperation;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

import java.util.stream.Stream;

public interface BulkMethodInfoProvider {
  ExtensionPointName<BulkMethodInfoProvider> KEY = ExtensionPointName.create("com.intellij.java.inspection.bulkMethodInfo");

  /**
   * @return stream of BulkMethodInfo structures which represent the consumers
   * Simple method should accept an element parameter and bulk method should accept Iterable or Collection of given elements
   */
  @NotNull
  Stream<BulkMethodInfo> consumers();
}
