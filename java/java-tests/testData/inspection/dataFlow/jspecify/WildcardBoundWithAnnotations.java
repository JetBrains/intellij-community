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
interface WildcardBoundWithAnnotations {
  <T> void use(Supplier<? extends T> s, Lib<T> l);

  <T> void useUnspec(Supplier<? extends @NullnessUnspecified T> s, Lib<T> l);

  <T> void useUnionNull(Supplier<? extends @Nullable T> s, Lib<T> l);

  default <T> void x0(Supplier<? extends T> s, Lib<T> l) {
    use(s, l);
    useUnspec(s, l);
    useUnionNull(s, l);
  }

  default <T> void x1(Supplier<? extends @NullnessUnspecified T> s, Lib<T> l) {
    // jspecify_nullness_not_enough_information
    use(s, l);
    // jspecify_nullness_not_enough_information
    useUnspec(s, l);
    useUnionNull(s, l);
  }

  default <T> void x2(Supplier<? extends @Nullable T> s, Lib<T> l) {
    // jspecify_nullness_mismatch
    use(s, l);
    // jspecify_nullness_not_enough_information
    useUnspec(s, l);
    useUnionNull(s, l);
  }

  interface Supplier<T extends @Nullable Object> {}

  interface Lib<T> {}
}
