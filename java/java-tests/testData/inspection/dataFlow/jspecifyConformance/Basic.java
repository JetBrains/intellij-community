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

import java.util.List;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

class Basic {
  @NonNull Object cannotConvertNullableToNonNull(@Nullable Object nullable) {
    // test:expression-type:Object?:nullable
    // test:sink-type:Object!:return
    // test:cannot-convert:Object? to Object!
    return nullable;
  }

  @Nullable Object canConvertNonNullToNullable(@NonNull Object nonNull) {
    // test:expression-type:Object!:nonNull
    // test:sink-type:Object?:return
    return nonNull;
  }

  @Nullable Object nullableObject;

  void testSinkType(@NonNull String nonNullString) {
    // test:sink-type:Object?:nullableObject
    nullableObject = nonNullString;
    // test:sink-type:String!:testSinkType#nonNullString
    testSinkType("aString");
  }

  @NullMarked
  Object testWildcard(List<? extends @Nullable String> nullableStrings) {
    // test:expression-type:List!<capture of ? extends String?>:nullableStrings
    return nullableStrings;
  }
}
