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
class CaptureConvertedToObjectUnspec {
  @NullnessUnspecified
  Object x0(ImplicitlyObjectBounded<? extends Lib> x) {
    return x.get();
  }

  @NullnessUnspecified
  Object x1(ImplicitlyObjectBounded<? extends @NullnessUnspecified Lib> x) {
    return x.get();
  }

  @NullnessUnspecified
  Object x2(ImplicitlyObjectBounded<? extends @Nullable Lib> x) {
    return x.get();
  }

  @NullnessUnspecified
  Object x3(ExplicitlyObjectBounded<? extends Lib> x) {
    return x.get();
  }

  @NullnessUnspecified
  Object x4(ExplicitlyObjectBounded<? extends @NullnessUnspecified Lib> x) {
    return x.get();
  }

  @NullnessUnspecified
  Object x5(ExplicitlyObjectBounded<? extends @Nullable Lib> x) {
    return x.get();
  }

  @NullnessUnspecified
  Object x6(UnspecBounded<? extends Lib> x) {
    return x.get();
  }

  @NullnessUnspecified
  Object x7(UnspecBounded<? extends @NullnessUnspecified Lib> x) {
    // jspecify_nullness_not_enough_information
    return x.get();
  }

  @NullnessUnspecified
  Object x8(UnspecBounded<? extends @Nullable Lib> x) {
    // jspecify_nullness_not_enough_information
    return x.get();
  }

  @NullnessUnspecified
  Object x9(NullableBounded<? extends Lib> x) {
    return x.get();
  }

  @NullnessUnspecified
  Object x10(NullableBounded<? extends @NullnessUnspecified Lib> x) {
    // jspecify_nullness_not_enough_information
    return x.get();
  }

  @NullnessUnspecified
  Object x11(NullableBounded<? extends @Nullable Lib> x) {
    // jspecify_nullness_not_enough_information
    return x.get();
  }

  interface ImplicitlyObjectBounded<T> {
    T get();
  }

  interface ExplicitlyObjectBounded<T extends Object> {
    T get();
  }

  interface UnspecBounded<T extends @NullnessUnspecified Object> {
    T get();
  }

  interface NullableBounded<T extends @Nullable Object> {
    T get();
  }

  interface Lib {}
}
