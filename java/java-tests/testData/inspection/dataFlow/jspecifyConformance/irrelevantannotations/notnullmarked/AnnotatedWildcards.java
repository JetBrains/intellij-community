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
package org.jspecify.conformance.tests.irrelevantannotations.notnullmarked;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.jspecify.conformance.deps.nullmarked.NHolder;

/**
 * {@link Nullable @Nullable} and {@link NonNull @NonNull} annotations are unrecognized when applied
 * to wildcards themselves (as opposed to their bounds).
 *
 * @see <a
 *     href="https://jspecify.dev/docs/spec#recognized-locations-for-type-use-annotations">Recognized
 *     locations for type-use annotations</a> in the JSpecify specification.
 */
public interface AnnotatedWildcards {

  // test:name:Nullable on unbounded wildcard
  // test:irrelevant-annotation:Nullable
  NHolder<@Nullable ?> nWildcard();

  // test:name:NonNull on unbounded wildcard
  // test:irrelevant-annotation:NonNull
  NHolder<@NonNull ?> rWildcard();

  // test:name:Nullable on bounded wildcard
  // test:irrelevant-annotation:Nullable
  NHolder<@Nullable ? extends Object> nWildcardWithBound();

  // test:name:NonNull on bounded wildcard
  // test:irrelevant-annotation:NonNull
  NHolder<@NonNull ? extends Object> rWildcardWithBound();

  // test:name:NonNull on annotated-bounded wildcard
  // test:irrelevant-annotation:NonNull
  NHolder<@NonNull ? extends @Nullable Object> rWildcardWithNBound();

  // test:name:Nullable on annotated-bounded wildcard
  // test:irrelevant-annotation:Nullable
  NHolder<@Nullable ? extends @NonNull Object> nWildcardWithRBound();
}
