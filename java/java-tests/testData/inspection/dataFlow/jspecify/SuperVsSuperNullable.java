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
interface SuperVsSuperNullable {
  interface Receiver<T extends @Nullable Object> {}

  interface Other<T> {}

  default <T> void x(Receiver<? super T> r, Other<T> o) {
    useNonNullBounded(r, o);
    // jspecify_nullness_not_enough_information
    useUnspecBounded(r, o);
    // jspecify_nullness_mismatch
    useUnionNullBounded(r, o);
  }

  default <T> void x1(Receiver<? super @NullnessUnspecified T> r, Other<T> o) {
    useNonNullBounded(r, o);
    // jspecify_nullness_not_enough_information
    useUnspecBounded(r, o);
    // jspecify_nullness_not_enough_information
    useUnionNullBounded(r, o);
  }

  default <T> void x2(Receiver<? super @Nullable T> r, Other<T> o) {
    useNonNullBounded(r, o);
    useUnspecBounded(r, o);
    useUnionNullBounded(r, o);
  }

  <T> void useNonNullBounded(Receiver<? super T> r, Other<T> o);

  <T> void useUnspecBounded(Receiver<? super @NullnessUnspecified T> r, Other<T> o);

  <T> void useUnionNullBounded(Receiver<? super @Nullable T> r, Other<T> o);
}
