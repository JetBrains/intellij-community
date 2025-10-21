/*
 * Copyright 2023 The JSpecify Authors.
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
package org.jspecify.conformance.tests;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

// Assertions about recognizing irrelevant annotations: those that have no or contradictory meaning
// in their context.
class Irrelevant {
  // Primitive types cannot be nullable.
  // test:irrelevant-annotation:Nullable
  @Nullable int nullablePrimitive() {
    return 0;
  }

  // Type parameters cannot be nullable.
  // test:irrelevant-annotation:Nullable
  <@Nullable T> void nullableTypeParameter(T param) {}

  // Type parameters cannot be non-null.
  // test:irrelevant-annotation:NonNull
  <@NonNull T> void nonNullTypeParameter(T param) {}

  // The same element cannot be both non-null and nullable.
  // test:irrelevant-annotation:NonNull
  // test:irrelevant-annotation:Nullable
  void bothNullableAndNonNull(@Nullable @NonNull String param) {}

  // The same element cannot be both null-marked and null-unmarked.
  // test:irrelevant-annotation:NullMarked
  @NullMarked
  // test:irrelevant-annotation:NullUnmarked
  @NullUnmarked
  static class BothNullMarkedAndNullUnmarked {}
}
