/*
 * Copyright 2020 The jspecify Authors.
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

package typeparameterbounds;

import org.jspecify.annotations.DefaultNonNull;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullnessUnspecified;

@DefaultNonNull
class A<T> {
  public void foo(@NullnessUnspecified T t) {}

  public <E> void bar(E e) {}
}

@DefaultNonNull
class B<T> {
  public void foo(T t) {}

  public <E> void bar(E e) {}
}

class Test {}

@DefaultNonNull
public class TypeParameterBounds {
  <T extends Test> void main(
      // jspecify_nullness_mismatch
      A<@Nullable Object> a1, A<Test> a2, B<@Nullable Object> b1, B<Test> b2, T x) {
    a1.foo(null);
    // jspecify_nullness_mismatch
    a1.<@Nullable T>bar(null);
    a1.<T>bar(x);

    // jspecify_nullness_not_enough_information
    a2.foo(null);
    // jspecify_nullness_mismatch
    a2.<@Nullable T>bar(null);
    a2.<T>bar(x);

    // jspecify_nullness_mismatch
    b1.foo(null);
    // jspecify_nullness_mismatch
    b1.<@Nullable T>bar(null);
    b1.<T>bar(x);

    // jspecify_nullness_mismatch
    b2.foo(null);
    // jspecify_nullness_mismatch
    b2.<@Nullable T>bar(null);
    b2.<T>bar(x);
  }
}
