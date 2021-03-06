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

/*
 * For the moment, we don't require support for parameter contravariance:
 * https://github.com/jspecify/jspecify/issues/49
 *
 * (If we change that, we should revise our samples README to explain that this is a case in which
 * we deviate from JLS rules:
 * https://github.com/jspecify/jspecify/blob/e55eb43f3bc1e7493b8b28a9dadd2b9b254e3335/samples/README.md#what-sample-inputs-demonstrate)
 */
@DefaultNonNull
class OverrideParameters {
  interface Super {
    void useObject(Object o);

    void useObjectUnspec(@NullnessUnspecified Object o);

    void useObjectUnionNull(@Nullable Object o);
  }

  interface SubObject extends Super {
    @Override
    void useObject(Object o);

    @Override
    // jspecify_nullness_not_enough_information
    void useObjectUnspec(Object o);

    @Override
    // jspecify_nullness_mismatch
    void useObjectUnionNull(Object o);
  }

  interface SubObjectUnspec extends Super {
    @Override
    // jspecify_nullness_not_enough_information
    void useObject(@NullnessUnspecified Object o);

    @Override
    // jspecify_nullness_not_enough_information
    void useObjectUnspec(@NullnessUnspecified Object o);

    @Override
    // jspecify_nullness_not_enough_information
    void useObjectUnionNull(@NullnessUnspecified Object o);
  }

  interface SubObjectUnionNull extends Super {
    @Override
    // jspecify_nullness_mismatch
    void useObject(@Nullable Object o);

    @Override
    // jspecify_nullness_not_enough_information
    void useObjectUnspec(@Nullable Object o);

    @Override
    void useObjectUnionNull(@Nullable Object o);
  }
}
