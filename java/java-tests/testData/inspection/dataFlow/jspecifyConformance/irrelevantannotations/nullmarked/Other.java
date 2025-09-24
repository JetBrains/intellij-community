/*
 * Copyright 2024 The JSpecify Authors.
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
package org.jspecify.conformance.tests.irrelevantannotations.nullmarked;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class Other {

  /**
   * {@link Nullable @Nullable} and {@link NonNull @NonNull} annotations are unrecognized when
   * applied to root types of local variables.
   *
   * @see <a
   *     href="https://jspecify.dev/docs/spec#recognized-locations-for-type-use-annotations">Recognized
   *     locations for type-use annotations</a> in the JSpecify specification.
   */
  void localVariables() {
    // test:name:Nullable local variable object
    // test:irrelevant-annotation:Nullable
    @Nullable Object n;

    // test:name:NonNull local variable object
    // test:irrelevant-annotation:NonNull
    @NonNull Object r;

    // test:name:Nullable local variable array
    // test:irrelevant-annotation:Nullable
    Object @Nullable [] nArray;

    // test:name:NonNull local variable array
    // test:irrelevant-annotation:NonNull
    Object @NonNull [] rArray;
  }

  /**
   * {@link Nullable @Nullable} and {@link NonNull @NonNull} annotations are unrecognized when
   * applied to catch parameter types.
   *
   * @see <a
   *     href="https://jspecify.dev/docs/spec#recognized-locations-for-type-use-annotations">Recognized
   *     locations for type-use annotations</a> in the JSpecify specification.
   */
  void catchParameters() {
    try {
      // test:name:Nullable exception parameter
      // test:irrelevant-annotation:Nullable
    } catch (@Nullable RuntimeException e) {
    }
    try {
      // test:name:NonNull exception parameter
      // test:irrelevant-annotation:NonNull
    } catch (@NonNull RuntimeException e) {
    }
    try {
    } catch (RuntimeException e) {
      // test:name:Intrinsically NonNull exception parameter cannot be assigned null
      // test:cannot-convert:null? to RuntimeException!
      e = null;
    }
  }

  /**
   * {@link Nullable @Nullable} and {@link NonNull @NonNull} annotations are unrecognized when
   * applied to try-with-resources parameter types.
   *
   * @see <a
   *     href="https://jspecify.dev/docs/spec#recognized-locations-for-type-use-annotations">Recognized
   *     locations for type-use annotations</a> in the JSpecify specification.
   */
  void tryWithResourcesParameters() throws Exception {
    // test:name:Nullable try-with-resources
    // test:irrelevant-annotation:Nullable
    try (@Nullable AutoCloseable a = () -> {}) {}
    // test:name:NonNull try-with-resources
    // test:irrelevant-annotation:NonNull
    try (@NonNull AutoCloseable a = () -> {}) {}
  }

  /**
   * {@link Nullable @Nullable} and {@link NonNull @NonNull} annotations are unrecognized when
   * applied to thrown types.
   *
   * @see <a
   *     href="https://jspecify.dev/docs/spec#recognized-locations-for-type-use-annotations">Recognized
   *     locations for type-use annotations</a> in the JSpecify specification.
   */
  interface ExceptionTypes {
    // test:name:Nullable exception type
    // test:irrelevant-annotation:Nullable
    void throwsN() throws @Nullable Exception;

    // test:name:NonNull exception type
    // test:irrelevant-annotation:NonNulll
    void throwsR() throws @NonNull Exception;
  }
}
