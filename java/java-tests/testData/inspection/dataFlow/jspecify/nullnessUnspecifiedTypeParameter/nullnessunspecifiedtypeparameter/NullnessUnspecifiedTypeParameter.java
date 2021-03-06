/*
 * Copyright 2020 The jspecify Authors.
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

package nullnessunspecifiedtypeparameter;

import org.jspecify.annotations.DefaultNonNull;
import org.jspecify.annotations.Nullable;

@DefaultNonNull
public class NullnessUnspecifiedTypeParameter<T> {
  public void foo(T t) {}

  public void bar(Test s, T t) {}
}

class Test {}

@DefaultNonNull
class Instances {
  static final NullnessUnspecifiedTypeParameter<Object> A1 =
      new NullnessUnspecifiedTypeParameter<>();
  // jspecify_nullness_mismatch
  static final NullnessUnspecifiedTypeParameter<@Nullable Object> A2 =
      new NullnessUnspecifiedTypeParameter<>();
  static final Test X = new Test();
}

class Use extends Instances {
  void main() {
    // jspecify_nullness_mismatch
    A1.foo(null);
    A1.foo(1);

    // jspecify_nullness_mismatch
    A2.foo(null);
    A2.foo(1);

    // jspecify_nullness_mismatch
    A1.bar(null, null);
    // jspecify_nullness_mismatch
    A1.bar(X, null);
    A1.bar(X, 1);

    // jspecify_nullness_mismatch
    A2.bar(null, null);
    // jspecify_nullness_mismatch
    A2.bar(X, null);
    A2.bar(X, 1);
  }
}
