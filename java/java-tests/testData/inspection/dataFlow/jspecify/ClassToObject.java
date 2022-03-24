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
class ClassToObject {
  Object x0(ClassToObject x) {
    return x;
  }

  Object x1(@NullnessUnspecified ClassToObject x) {
    // jspecify_nullness_not_enough_information
    return x;
  }

  Object x2(@Nullable ClassToObject x) {
    // jspecify_nullness_mismatch
    return x;
  }

  @NullnessUnspecified
  Object x3(ClassToObject x) {
    return x;
  }

  @NullnessUnspecified
  Object x4(@NullnessUnspecified ClassToObject x) {
    // jspecify_nullness_not_enough_information
    return x;
  }

  @NullnessUnspecified
  Object x5(@Nullable ClassToObject x) {
    // jspecify_nullness_not_enough_information
    return x;
  }

  @Nullable
  Object x6(ClassToObject x) {
    return x;
  }

  @Nullable
  Object x7(@NullnessUnspecified ClassToObject x) {
    return x;
  }

  @Nullable
  Object x8(@Nullable ClassToObject x) {
    return x;
  }
}
