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
class TypeVariableToParentUnionNull<
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
  @Nullable
  Never1T x0(ChildOfNever1T x) {
    return x;
  }

  @Nullable
  Never1T x1(UnspecChildOfNever1T x) {
    return x;
  }

  @Nullable
  Never1T x2(NullChildOfNever1T x) {
    return x;
  }

  @Nullable
  Never2T x3(ChildOfNever2T x) {
    return x;
  }

  @Nullable
  Never2T x4(UnspecChildOfNever2T x) {
    return x;
  }

  @Nullable
  Never2T x5(NullChildOfNever2T x) {
    return x;
  }

  @Nullable
  UnspecT x6(ChildOfUnspecT x) {
    return x;
  }

  @Nullable
  UnspecT x7(UnspecChildOfUnspecT x) {
    return x;
  }

  @Nullable
  UnspecT x8(NullChildOfUnspecT x) {
    return x;
  }

  @Nullable
  ParametricT x9(ChildOfParametricT x) {
    return x;
  }

  @Nullable
  ParametricT x10(UnspecChildOfParametricT x) {
    return x;
  }

  @Nullable
  ParametricT x11(NullChildOfParametricT x) {
    return x;
  }
}
