/*
 * Copyright 2020 The JSpecify Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

// Covered by
// conformance-tests/src/assertions/java/org/jspecify/conformance/tests/irrelevantannotations/notnullmarked/Other.java
@NullMarked
abstract class UnrecognizedLocationsMisc {
  interface Super {}

  static class Sub
      // jspecify_nullness_intrinsically_not_nullable
      extends @Nullable Object
      // jspecify_nullness_intrinsically_not_nullable
      implements @Nullable Super {
    // jspecify_nullness_intrinsically_not_nullable
    @Nullable Sub() {}
  }

  void foo() throws Exception {
    try {
      // jspecify_unrecognized_location
      @Nullable Object o;

      @Nullable Object[] a0;

      // jspecify_unrecognized_location
      Object @Nullable [] a1;

      // jspecify_unrecognized_location
      @Nullable Object @Nullable [] a2;

      // jspecify_unrecognized_location
    } catch (@Nullable Exception e) {
    }

    // jspecify_unrecognized_location
    try (@Nullable AutoCloseable a = get()) {}
  }

  abstract AutoCloseable get();
}
