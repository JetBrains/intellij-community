/*
 * Copyright 2022 The JSpecify Authors.
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
class SuperNullableForNonNullableTypeParameter {
  interface NonNullableLib<T> {}

  static class NullableLib<T extends @Nullable Object> {
    NullableLib(NonNullableLib<? super T> l, T t) {}
  }

  static <T, S extends @Nullable T> NullableLib<S> x(NonNullableLib<T> l, S s) {
    // jspecify_nullness_mismatch
    return new NullableLib<>(l, s);
  }
}
