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
import org.jspecify.annotations.NullnessUnspecified;

@NullMarked
interface ExtendsVsExtendsNullable {
  interface Supplier<T extends @Nullable Object> {}

  interface Other<T> {}

  default <T> void x(Supplier<? extends T> s, Other<T> o) {
    useNonNullBounded(s, o);
    useUnspecBounded(s, o);
    useUnionNullBounded(s, o);
  }

  default <T> void x1(Supplier<? extends @NullnessUnspecified T> s, Other<T> o) {
    // jspecify_nullness_not_enough_information
    useNonNullBounded(s, o);
    // jspecify_nullness_not_enough_information
    useUnspecBounded(s, o);
    useUnionNullBounded(s, o);
  }

  default <T> void x2(Supplier<? extends @Nullable T> s, Other<T> o) {
    // jspecify_nullness_mismatch
    useNonNullBounded(s, o);
    // jspecify_nullness_not_enough_information
    useUnspecBounded(s, o);
    useUnionNullBounded(s, o);
  }

  <T> void useNonNullBounded(Supplier<? extends T> s, Other<T> o);

  <T> void useUnspecBounded(Supplier<? extends @NullnessUnspecified T> s, Other<T> o);

  <T> void useUnionNullBounded(Supplier<? extends @Nullable T> s, Other<T> o);
}
