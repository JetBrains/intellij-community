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
class TypeVariableToObject<
    Never1T,
    ChildOfNever1T extends Never1T,
    UnspecChildOfNever1T extends @NullnessUnspecified Never1T,
    NullChildOfNever1T extends @Nullable Never1T,
    //
    Never2T extends Object,
    ChildOfNever2T extends Never2T,
    UnspecChildOfNever2T extends @NullnessUnspecified Never2T,
    NullChildOfNever2T extends @Nullable Never2T,
    //
    UnspecT extends @NullnessUnspecified Object,
    ChildOfUnspecT extends UnspecT,
    UnspecChildOfUnspecT extends @NullnessUnspecified UnspecT,
    NullChildOfUnspecT extends @Nullable UnspecT,
    //
    ParametricT extends @Nullable Object,
    ChildOfParametricT extends ParametricT,
    UnspecChildOfParametricT extends @NullnessUnspecified ParametricT,
    NullChildOfParametricT extends @Nullable ParametricT,
    //
    UnusedT> {
  Object x0(Never1T x) {
    return x;
  }

  Object x1(ChildOfNever1T x) {
    return x;
  }

  Object x2(UnspecChildOfNever1T x) {
    // jspecify_nullness_not_enough_information
    return x;
  }

  Object x3(NullChildOfNever1T x) {
    // jspecify_nullness_mismatch
    return x;
  }

  Object x4(Never2T x) {
    return x;
  }

  Object x5(ChildOfNever2T x) {
    return x;
  }

  Object x6(UnspecChildOfNever2T x) {
    // jspecify_nullness_not_enough_information
    return x;
  }

  Object x7(NullChildOfNever2T x) {
    // jspecify_nullness_mismatch
    return x;
  }

  Object x8(UnspecT x) {
    // jspecify_nullness_not_enough_information
    return x;
  }

  Object x9(ChildOfUnspecT x) {
    // jspecify_nullness_not_enough_information
    return x;
  }

  Object x10(UnspecChildOfUnspecT x) {
    // jspecify_nullness_not_enough_information
    return x;
  }

  Object x11(NullChildOfUnspecT x) {
    // jspecify_nullness_mismatch
    return x;
  }

  Object x12(ParametricT x) {
    // jspecify_nullness_mismatch
    return x;
  }

  Object x13(ChildOfParametricT x) {
    // jspecify_nullness_mismatch
    return x;
  }

  Object x14(UnspecChildOfParametricT x) {
    // jspecify_nullness_mismatch
    return x;
  }

  Object x15(NullChildOfParametricT x) {
    // jspecify_nullness_mismatch
    return x;
  }
}
