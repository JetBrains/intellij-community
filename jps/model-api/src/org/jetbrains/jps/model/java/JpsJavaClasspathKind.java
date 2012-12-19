/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.model.java;

/**
 * @author nik
 */
public enum JpsJavaClasspathKind {
  PRODUCTION_COMPILE, PRODUCTION_RUNTIME,
  TEST_COMPILE, TEST_RUNTIME;

  public boolean isTestsIncluded() {
    return this == TEST_COMPILE || this == TEST_RUNTIME;
  }

  public boolean isRuntime() {
    return this == PRODUCTION_RUNTIME || this == TEST_RUNTIME;
  }

  public static JpsJavaClasspathKind compile(boolean tests) {
    return tests ? TEST_COMPILE : PRODUCTION_COMPILE;
  }

  public static JpsJavaClasspathKind runtime(boolean tests) {
    return tests ? TEST_RUNTIME : PRODUCTION_RUNTIME;
  }
}
