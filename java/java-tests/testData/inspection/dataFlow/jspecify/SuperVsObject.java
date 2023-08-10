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
class SuperVsObject {
  interface Receiver<T extends @Nullable Object> {}

  <T extends @Nullable Object> Receiver<? super T> x0(Receiver<Object> r) {
    // jspecify_nullness_mismatch
    return r;
  }

  <T extends @NullnessUnspecified Object> Receiver<? super T> x1(Receiver<Object> r) {
    // jspecify_nullness_not_enough_information
    return r;
  }

  <T extends Object> Receiver<? super T> x2(Receiver<Object> r) {
    return r;
  }
}
