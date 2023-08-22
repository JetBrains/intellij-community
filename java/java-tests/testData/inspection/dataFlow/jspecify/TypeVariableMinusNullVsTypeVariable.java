/*
 * Copyright 2021 The JSpecify Authors.
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

@NullMarked
class TypeVariableMinusNullVsTypeVariable {
  interface Supplier<T extends @Nullable Object> {}

  interface NonnullSupplier<T> extends Supplier<T> {
    NonnullSupplier<T> caching();
  }

  <T extends @Nullable Object> Supplier<T> cachingIfPossible(Supplier<T> supplier) {
    if (supplier instanceof NonnullSupplier) {
      // jspecify_nullness_mismatch
      NonnullSupplier<T> cast =
          // jspecify_nullness_mismatch
          (NonnullSupplier<T>) supplier;
      /*
       * TODO(cpovirk): Can/should we change the spec to make the following statement not be a
       * mismatch?
       *
       * I actually think I'm OK with the mismatch: The overall operation would still include a
       * mismatch (above). And the mismatch above makes sense: it's worth, I tested some similar
       * plain-Java code that uses Supplier<T> and NumberSupplier<N extends Number>, and its
       * reference to NumberSupplier<T> fails to compile.
       *
       * (A related bug different thing: Our checker currently *doesn't* issue an error merely if we
       * merely *cast* to NonnullSupplier<T>, only if we actually declare a variable of that type.
       * It might be interesting to see what upstream CF does. Note that the code this sample is
       * based on, Guava's Lists.java, has a cast without a local variable. So Guava sees no error
       * except the one below.)
       *
       * (Assuming that we keep the error here, there should be a workaround: Cast to
       * NonnullSupplier<?>, call caching(), and then unchecked-cast the result to Supplier<T>.)
       */
      // jspecify_nullness_mismatch
      return cast.caching();
    }
    return supplier;
  }
}
