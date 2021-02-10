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

import org.jspecify.annotations.DefaultNonNull;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullnessUnspecified;

@DefaultNonNull
interface IntersectionSupertype {
  <T extends Object & Lib> void x0(T t);

  <T extends @NullnessUnspecified Object & Lib> void x1(T t);

  <T extends @Nullable Object & Lib> void x2(T t);

  <T extends Object & @NullnessUnspecified Lib> void x3(T t);

  <T extends @NullnessUnspecified Object & @NullnessUnspecified Lib> void x4(T t);

  <T extends @Nullable Object & @NullnessUnspecified Lib> void x5(T t);

  <T extends Object & @Nullable Lib> void x6(T t);

  <T extends @NullnessUnspecified Object & @Nullable Lib> void x7(T t);

  <T extends @Nullable Object & @Nullable Lib> void x8(T t);

  default void useLib(Lib lib) {
    x0(lib);
    x1(lib);
    x2(lib);
    x3(lib);
    x4(lib);
    x5(lib);
    x6(lib);
    x7(lib);
    x8(lib);
  }

  default void useLibUnspec(@NullnessUnspecified Lib lib) {
    // jspecify_nullness_not_enough_information
    x0(lib);
    // jspecify_nullness_not_enough_information
    x1(lib);
    // jspecify_nullness_not_enough_information
    x2(lib);
    // jspecify_nullness_not_enough_information
    x3(lib);
    // jspecify_nullness_not_enough_information
    x4(lib);
    // jspecify_nullness_not_enough_information
    x5(lib);
    // jspecify_nullness_not_enough_information
    x6(lib);
    // jspecify_nullness_not_enough_information
    x7(lib);
    this.<@Nullable Lib>x8(lib);
  }

  default void useLibUnionNull(@Nullable Lib lib) {
    // jspecify_nullness_mismatch
    x0(lib);
    // jspecify_nullness_mismatch
    x1(lib);
    // jspecify_nullness_mismatch
    x2(lib);
    // jspecify_nullness_mismatch
    x3(lib);
    // jspecify_nullness_not_enough_information
    x4(lib);
    // jspecify_nullness_not_enough_information
    x5(lib);
    // jspecify_nullness_mismatch
    x6(lib);
    // jspecify_nullness_not_enough_information
    x7(lib);
    x8(lib);
  }

  interface Lib {}
}
