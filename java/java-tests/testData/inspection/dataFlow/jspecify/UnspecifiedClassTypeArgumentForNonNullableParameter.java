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

class UnspecifiedClassTypeArgumentForNonNullableParameter {
  @NullMarked
  interface ConsumerWithNonNullableBound<E> {
    void accept(E e);
  }

  void x(
      // jspecify_nullness_not_enough_information
      ConsumerWithNonNullableBound<String> c) {
    // jspecify_nullness_mismatch
    c.accept(null);
  }
}
