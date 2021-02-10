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

package annotatedboundsofwildcard;

import org.jspecify.annotations.DefaultNonNull;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullnessUnspecified;

@DefaultNonNull
public class AnnotatedBoundsOfWildcard {
  public void superAsIs(
      // jspecify_nullness_not_enough_information
      Test<? super Base, ? super @Nullable Base, ? super @NullnessUnspecified Base> a) {}

  public void superNotNull(Test<? super Base, ? super Base, ? super Base> a) {}

  public void superNullable(
      // jspecify_nullness_not_enough_information
      Test<? super @Nullable Base, ? super @Nullable Base, ? super @Nullable Base> a) {}

  public void extendsAsIs(
      Test<? extends Base, ? extends @Nullable Base, ? extends @NullnessUnspecified Base> a) {}

  public void extendsNotNull(Test<? extends Base, ? extends Base, ? extends Base> a) {}

  public void extendsNullable(
      Test<? extends @Nullable Base, ? extends @Nullable Base, ? extends @Nullable Base> a) {}

  public void noBounds(
      Test<
              ? extends @NullnessUnspecified Object,
              ? extends @NullnessUnspecified Object,
              ? extends @NullnessUnspecified Object>
          a) {}
}

class Base {}

class Derived extends Base {}

@DefaultNonNull
class Test<T extends Object, E extends @Nullable Object, F extends @NullnessUnspecified Object> {}

@DefaultNonNull
class Use {
  public void main(
      Test<Derived, Derived, Derived> aNotNullNotNullNotNull,
      // jspecify_nullness_not_enough_information
      Test<Derived, Derived, @Nullable Derived> aNotNullNotNullNull,
      Test<Derived, @Nullable Derived, Derived> aNotNullNullNotNull,
      // jspecify_nullness_not_enough_information
      Test<Derived, @Nullable Derived, @Nullable Derived> aNotNullNullNull,
      //
      Test<Object, Object, Object> aObjectNotNullNotNullNotNull,
      // jspecify_nullness_not_enough_information
      Test<Object, Object, @Nullable Object> aObjectNotNullNotNullNull,
      Test<Object, @Nullable Object, Object> aObjectNotNullNullNotNull,
      // jspecify_nullness_not_enough_information
      Test<Object, @Nullable Object, @Nullable Object> aObjectNotNullNullNull,
      //
      AnnotatedBoundsOfWildcard b) {
    // jspecify_nullness_mismatch
    b.superAsIs(aObjectNotNullNotNullNotNull);
    // jspecify_nullness_mismatch
    b.superAsIs(aObjectNotNullNotNullNull);
    // jspecify_nullness_not_enough_information
    b.superAsIs(aObjectNotNullNullNotNull);
    b.superAsIs(aObjectNotNullNullNull);

    b.superNotNull(aObjectNotNullNotNullNotNull);
    b.superNotNull(aObjectNotNullNotNullNull);
    b.superNotNull(aObjectNotNullNullNotNull);
    b.superNotNull(aObjectNotNullNullNull);

    // jspecify_nullness_mismatch
    b.superNullable(aObjectNotNullNotNullNotNull);
    // jspecify_nullness_mismatch
    b.superNullable(aObjectNotNullNotNullNull);
    // jspecify_nullness_mismatch
    b.superNullable(aObjectNotNullNullNotNull);
    // jspecify_nullness_mismatch
    b.superNullable(aObjectNotNullNullNull);

    b.extendsAsIs(aNotNullNotNullNotNull);
    // jspecify_nullness_not_enough_information
    b.extendsAsIs(aNotNullNotNullNull);
    b.extendsAsIs(aNotNullNullNotNull);
    // jspecify_nullness_not_enough_information
    b.extendsAsIs(aNotNullNullNull);

    b.extendsNotNull(aNotNullNotNullNotNull);
    // jspecify_nullness_mismatch
    b.extendsNotNull(aNotNullNotNullNull);
    // jspecify_nullness_mismatch
    b.extendsNotNull(aNotNullNullNotNull);
    // jspecify_nullness_mismatch
    b.extendsNotNull(aNotNullNullNull);

    b.extendsNullable(aNotNullNotNullNotNull);
    b.extendsNullable(aNotNullNotNullNull);
    b.extendsNullable(aNotNullNullNotNull);
    b.extendsNullable(aNotNullNullNull);

    b.noBounds(aNotNullNotNullNotNull);
    // jspecify_nullness_not_enough_information
    b.noBounds(aNotNullNotNullNull);
    // jspecify_nullness_not_enough_information
    b.noBounds(aNotNullNullNotNull);
    // jspecify_nullness_not_enough_information
    b.noBounds(aNotNullNullNull);
  }
}
