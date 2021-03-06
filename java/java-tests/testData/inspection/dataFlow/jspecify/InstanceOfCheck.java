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

@DefaultNonNull
class InstanceOfCheck {
  Object x0(Object o) {
    if (o instanceof Foo) {
      return o;
    } else {
      return o;
    }
  }

  Object x1(@NullnessUnspecified Object o) {
    if (o instanceof Foo) {
      return o;
    } else {
      // jspecify_nullness_not_enough_information
      return o;
    }
  }

  Object x2(@Nullable Object o) {
    if (o instanceof Foo) {
      return o;
    } else {
      // jspecify_nullness_mismatch
      return o;
    }
  }

  class Foo {}
}
