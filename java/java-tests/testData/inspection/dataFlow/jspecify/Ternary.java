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
abstract class Ternary {
  Object x0() {
    return b() ? object() : object();
  }

  Object x1() {
    // jspecify_nullness_not_enough_information
    return b() ? object() : objectUnspec();
  }

  Object x2() {
    // jspecify_nullness_mismatch
    return b() ? object() : objectUnionNull();
  }

  Object x3() {
    // jspecify_nullness_not_enough_information
    return b() ? objectUnspec() : objectUnspec();
  }

  Object x4() {
    // jspecify_nullness_mismatch
    return b() ? objectUnspec() : objectUnionNull();
  }

  Object x5() {
    // jspecify_nullness_mismatch
    return b() ? objectUnionNull() : objectUnionNull();
  }

  @Nullable
  Object x6() {
    return b() ? objectUnspec() : objectUnspec();
  }

  abstract boolean b();

  abstract Object object();

  abstract @NullnessUnspecified Object objectUnspec();

  abstract @Nullable Object objectUnionNull();
}
