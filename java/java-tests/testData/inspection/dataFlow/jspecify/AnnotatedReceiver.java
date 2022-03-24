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

import org.jspecify.nullness.NullMarked;
import org.jspecify.nullness.Nullable;
import org.jspecify.nullness.NullnessUnspecified;

@NullMarked
interface AnnotatedReceiver {

  void foo(AnnotatedReceiver this);

  // jspecify_nullness_intrinsically_not_nullable
  void nullableReceiverInvalid(@Nullable AnnotatedReceiver this);

  // jspecify_nullness_intrinsically_not_nullable
  void unspecifiedReceiverInvalid(@NullnessUnspecified AnnotatedReceiver this);

  // TODO(#157,#158): figure out bar(AnnotatedReceiver<@Nullable T, @NullnessUnspecified U> this)
}
