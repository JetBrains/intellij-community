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
class NullCheck {
  // TODO(cpovirk): Soften README to permit flow-sensitive samples in moderation.
  Object x1(@NullnessUnspecified Object o) {
    if (o != null) {
      return o;
    } else {
      // jspecify_nullness_mismatch
      return o;
    }
  }

  Object x2(@Nullable Object o) {
    if (o != null) {
      return o;
    } else {
      // jspecify_nullness_mismatch
      return o;
    }
  }

  Object x4(@NullnessUnspecified Object o) {
    if (o == null) {
      // jspecify_nullness_mismatch
      return o;
    } else {
      return o;
    }
  }

  Object x5(@Nullable Object o) {
    if (o == null) {
      // jspecify_nullness_mismatch
      return o;
    } else {
      return o;
    }
  }
}
