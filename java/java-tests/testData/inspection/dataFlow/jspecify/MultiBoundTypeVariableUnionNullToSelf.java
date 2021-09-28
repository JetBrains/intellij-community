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
class MultiBoundTypeVariableUnionNullToSelf {
  <T extends Object & Lib> T x0(@Nullable T x) {
    // jspecify_nullness_mismatch
    return x;
  }

  <T extends Object & @NullnessUnspecified Lib> T x1(@Nullable T x) {
    // jspecify_nullness_mismatch
    return x;
  }

  <T extends Object & @Nullable Lib> T x2(@Nullable T x) {
    // jspecify_nullness_mismatch
    return x;
  }

  <T extends @NullnessUnspecified Object & Lib> T x3(@Nullable T x) {
    // jspecify_nullness_mismatch
    return x;
  }

  <T extends @NullnessUnspecified Object & @NullnessUnspecified Lib> T x4(@Nullable T x) {
    // jspecify_nullness_mismatch
    return x;
  }

  <T extends @NullnessUnspecified Object & @Nullable Lib> T x5(@Nullable T x) {
    // jspecify_nullness_mismatch
    return x;
  }

  <T extends @Nullable Object & Lib> T x6(@Nullable T x) {
    // jspecify_nullness_mismatch
    return x;
  }

  <T extends @Nullable Object & @NullnessUnspecified Lib> T x7(@Nullable T x) {
    // jspecify_nullness_mismatch
    return x;
  }

  <T extends @Nullable Object & @Nullable Lib> T x8(@Nullable T x) {
    // jspecify_nullness_mismatch
    return x;
  }

  interface Lib {}
}
