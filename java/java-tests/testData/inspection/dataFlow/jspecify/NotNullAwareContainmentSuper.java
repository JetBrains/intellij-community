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

import org.jspecify.nullness.Nullable;
import org.jspecify.nullness.NullnessUnspecified;

class NotNullAwareContainmentSuper {
  void x() {
    // jspecify_nullness_not_enough_information
    new Check<Lib<? super Foo>, Lib<? super Foo>>();

    // jspecify_nullness_not_enough_information
    new Check<Lib<? super Foo>, Lib<? super @NullnessUnspecified Foo>>();

    new Check<Lib<? super Foo>, Lib<? super @Nullable Foo>>();

    // jspecify_nullness_not_enough_information
    new Check<Lib<? super @NullnessUnspecified Foo>, Lib<? super Foo>>();

    // jspecify_nullness_not_enough_information
    new Check<Lib<? super @NullnessUnspecified Foo>, Lib<? super @NullnessUnspecified Foo>>();

    new Check<Lib<? super @NullnessUnspecified Foo>, Lib<? super @Nullable Foo>>();

    // jspecify_nullness_not_enough_information
    new Check<Lib<? super @Nullable Foo>, Lib<? super Foo>>();

    // jspecify_nullness_not_enough_information
    new Check<Lib<? super @Nullable Foo>, Lib<? super @NullnessUnspecified Foo>>();

    new Check<Lib<? super @Nullable Foo>, Lib<? super @Nullable Foo>>();
  }

  interface Lib<T extends @Nullable Object> {}

  interface Foo {}

  static class Check<F extends @Nullable Object, A extends @Nullable F> {}
}
