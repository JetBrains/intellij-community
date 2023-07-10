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
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class OutOfBoundsTypeVariable {
  interface User<F extends @Nullable Foo> {
    // jspecify_nullness_mismatch
    Lib<F> get();
  }

  interface Foo {}

  interface Lib<T> {
    T get();
  }

  <F extends @Nullable Foo> Object go(User<F> user) {
    return user.get();
  }
}
