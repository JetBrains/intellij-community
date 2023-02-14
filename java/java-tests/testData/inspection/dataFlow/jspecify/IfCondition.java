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
abstract class IfCondition {
  abstract int i();

  abstract int j();

  abstract boolean b();

  abstract Boolean boxed();

  abstract @NullnessUnspecified Boolean boxedUnspec();

  abstract @Nullable Boolean boxedUnionNull();

  void go() {
    if (i() == j()) {}

    if (b()) {}

    if (boxed()) {}

    // jspecify_nullness_not_enough_information
    if (boxedUnspec()) {}

    // jspecify_nullness_mismatch
    if (boxedUnionNull()) {}
  }
}
