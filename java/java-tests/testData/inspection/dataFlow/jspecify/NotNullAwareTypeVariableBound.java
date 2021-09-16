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

class NotNullAwareTypeVariableBound {
  class UnspecBounded1<T> {
    @NullMarked
    abstract class Nested {
      abstract T get();
    }
  }

  class UnspecBounded2<T extends Object> {
    @NullMarked
    abstract class Nested {
      abstract T get();
    }
  }

  class UnspecBounded3<T extends @NullnessUnspecified Object> {
    @NullMarked
    abstract class Nested {
      abstract T get();
    }
  }

  class NullBounded<T extends @Nullable Object> {
    @NullMarked
    abstract class Nested {
      abstract T get();
    }
  }

  @NullMarked
  class Callers {
    Object x0(UnspecBounded1<?>.Nested x) {
      // jspecify_nullness_not_enough_information
      return x.get();
    }

    Object x0(UnspecBounded2<?>.Nested x) {
      // jspecify_nullness_not_enough_information
      return x.get();
    }

    Object x0(UnspecBounded3<?>.Nested x) {
      // jspecify_nullness_not_enough_information
      return x.get();
    }

    Object x0(NullBounded<?>.Nested x) {
      // jspecify_nullness_mismatch
      return x.get();
    }
  }
}
