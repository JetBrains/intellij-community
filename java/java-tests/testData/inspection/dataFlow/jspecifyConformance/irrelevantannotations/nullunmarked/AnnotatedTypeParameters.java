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
package org.jspecify.conformance.tests.irrelevantannotations.nullunmarked;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

/**
 * {@link Nullable @Nullable} and {@link NonNull @NonNull} annotations are unrecognized when applied
 * to type parameters themselves (as opposed to their bounds).
 *
 * @see <a
 *     href="https://jspecify.dev/docs/spec#recognized-locations-for-type-use-annotations">Recognized
 *     locations for type-use annotations</a> in the JSpecify specification.
 */
@NullUnmarked
public interface AnnotatedTypeParameters {

  // test:name:Nullable on simple type parameter on method
  // test:irrelevant-annotation:Nullable
  <@Nullable T> void nParameter();

  // test:name:NonNull on simple type parameter on method
  // test:irrelevant-annotation:NonNull
  <@NonNull T> void rParameter();

  // test:name:Nullable on bounded type parameter on method
  // test:irrelevant-annotation:Nullable
  <@Nullable T extends Object> void nParameterWithBound();

  // test:name:NonNull on bounded type parameter on method
  // test:irrelevant-annotation:NonNull
  <@NonNull T extends Object> void rParameterWithBound();

  // test:name:NonNull on annotated-bounded type parameter on method
  // test:irrelevant-annotation:NonNull
  <@NonNull T extends @Nullable Object> void rParameterWithNBound();

  // test:name:Nullable on annotated-bounded type parameter on method
  // test:irrelevant-annotation:Nullable
  <@Nullable T extends @NonNull Object> void nParameterWithRBound();

  // test:name:Nullable on simple type parameter on class
  // test:irrelevant-annotation:Nullable
  interface NParameter<@Nullable T> {}

  // test:name:NonNull on simple type parameter on class
  // test:irrelevant-annotation:NonNull
  interface RParameter<@NonNull T> {}

  // test:name:Nullable on bounded type parameter on class
  // test:irrelevant-annotation:Nullable
  interface NParameterWithBound<@Nullable T extends Object> {}

  // test:name:NonNull on bounded type parameter on class
  // test:irrelevant-annotation:NonNull
  interface RParameterWithBound<@NonNull T extends Object> {}

  // test:name:NonNull on annotated-bounded type parameter on class
  // test:irrelevant-annotation:NonNull
  interface RParameterWithNBound<@NonNull T extends @Nullable Object> {}

  // test:name:Nullable on annotated-bounded type parameter on class
  // test:irrelevant-annotation:Nullable
  interface NParameterWithRBound<@Nullable T extends @NonNull Object> {}
}
