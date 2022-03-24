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
class TypeVariableUnspecToObjectUnionNull<
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
  Object x0(@NullnessUnspecified Never1T x) {
    return x;
  }

  @Nullable
  Object x1(@NullnessUnspecified ChildOfNever1T x) {
    return x;
  }

  @Nullable
  Object x2(@NullnessUnspecified UnspecChildOfNever1T x) {
    return x;
  }

  @Nullable
  Object x3(@NullnessUnspecified NullChildOfNever1T x) {
    return x;
  }

  @Nullable
  Object x4(@NullnessUnspecified Never2T x) {
    return x;
  }

  @Nullable
  Object x5(@NullnessUnspecified ChildOfNever2T x) {
    return x;
  }

  @Nullable
  Object x6(@NullnessUnspecified UnspecChildOfNever2T x) {
    return x;
  }

  @Nullable
  Object x7(@NullnessUnspecified NullChildOfNever2T x) {
    return x;
  }

  @Nullable
  Object x8(@NullnessUnspecified UnspecT x) {
    return x;
  }

  @Nullable
  Object x9(@NullnessUnspecified ChildOfUnspecT x) {
    return x;
  }

  @Nullable
  Object x10(@NullnessUnspecified UnspecChildOfUnspecT x) {
    return x;
  }

  @Nullable
  Object x11(@NullnessUnspecified NullChildOfUnspecT x) {
    return x;
  }

  @Nullable
  Object x12(@NullnessUnspecified ParametricT x) {
    return x;
  }

  @Nullable
  Object x13(@NullnessUnspecified ChildOfParametricT x) {
    return x;
  }

  @Nullable
  Object x14(@NullnessUnspecified UnspecChildOfParametricT x) {
    return x;
  }

  @Nullable
  Object x15(@NullnessUnspecified NullChildOfParametricT x) {
    return x;
  }
}
