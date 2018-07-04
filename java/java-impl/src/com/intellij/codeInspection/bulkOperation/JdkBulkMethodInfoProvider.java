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
package com.intellij.codeInspection.bulkOperation;

import com.intellij.psi.CommonClassNames;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.stream.Stream;

public class JdkBulkMethodInfoProvider implements BulkMethodInfoProvider {
  private static final BulkMethodInfo[] INFOS = {
    new BulkMethodInfo(CommonClassNames.JAVA_UTIL_COLLECTION, "add", "addAll")
  };

  @NotNull
  @Override
  public Stream<BulkMethodInfo> consumers() {
    return Arrays.stream(INFOS);
  }
}
