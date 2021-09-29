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

import org.jspecify.nullness.NullMarked;
import org.jspecify.nullness.Nullable;
import org.jspecify.nullness.NullnessUnspecified;

@NullMarked
class PrimitiveAnnotationsUnspec {
  void foo(
      // jspecify_nullness_intrinsically_not_nullable
      @NullnessUnspecified int x1,
      // jspecify_nullness_intrinsically_not_nullable
      @NullnessUnspecified int[] x2,
      int x3,
      int[] x4,
      int @NullnessUnspecified [] x5,

      // jspecify_nullness_intrinsically_not_nullable
      Lib<@NullnessUnspecified int[]> x6,
      Lib<int @NullnessUnspecified []> x7) {}

  class Lib<T extends @Nullable Object> {}
}
