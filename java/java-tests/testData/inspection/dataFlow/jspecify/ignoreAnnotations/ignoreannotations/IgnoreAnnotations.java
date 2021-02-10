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

package ignoreannotations;

import org.jspecify.annotations.DefaultNonNull;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullnessUnspecified;

@DefaultNonNull
public class IgnoreAnnotations {
  public @Nullable Derived field = null;

  public @Nullable Derived foo(Derived x, @NullnessUnspecified Base y) {
    return null;
  }

  public Derived everythingNotNullable(Derived x) {
    // jspecify_nullness_mismatch
    return null;
  }

  public @Nullable Derived everythingNullable(@Nullable Derived x) {
    return null;
  }

  public @NullnessUnspecified Derived everythingUnknown(@NullnessUnspecified Derived x) {
    // jspecify_nullness_not_enough_information
    return null;
  }
}

class Base {
  void foo() {}
}

class Derived extends Base {}

@DefaultNonNull
class Instances {
  static final IgnoreAnnotations IGNORE_ANNOTATIONS = new IgnoreAnnotations();
  static final Derived DERIVED = new Derived();
}

class Use {
  static void main() {
    IgnoreAnnotations a = Instances.IGNORE_ANNOTATIONS;
    Derived x = Instances.DERIVED;

    // jspecify_nullness_mismatch
    a.foo(x, null).foo();
    // jspecify_nullness_mismatch
    a.foo(null, x).foo();

    // jspecify_nullness_mismatch
    a.field.foo();

    // jspecify_nullness_mismatch
    a.everythingNotNullable(null).foo();
    a.everythingNotNullable(x).foo();

    // jspecify_nullness_mismatch
    a.everythingNullable(null).foo();

    // jspecify_nullness_not_enough_information
    a.everythingUnknown(null).foo();
  }
}
