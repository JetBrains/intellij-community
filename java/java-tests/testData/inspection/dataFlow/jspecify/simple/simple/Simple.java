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

package simple;

import org.jspecify.annotations.DefaultNonNull;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullnessUnspecified;

@DefaultNonNull
public class Simple {
  public @Nullable Derived field = null;

  public @Nullable Derived foo(Derived x, @NullnessUnspecified Base y) {
    return null;
  }

  public Derived bar() {
    // jspecify_nullness_mismatch
    return null;
  }
}

class Base {}

class Derived extends Base {
  void foo() {}
}

@DefaultNonNull
class Use {
  public static void main(Simple a, Derived x) {
    // jspecify_nullness_mismatch
    a.foo(x, null).foo();
    // jspecify_nullness_mismatch
    a.foo(null, x).foo();

    a.bar().foo();

    // jspecify_nullness_mismatch
    a.field.foo();
  }
}
