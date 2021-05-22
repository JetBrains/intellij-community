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

package defaults;

import org.jspecify.annotations.DefaultNonNull;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullnessUnspecified;

@DefaultNonNull
public class Defaults {
  // jspecify_nullness_mismatch
  public Foo defaultField = null;
  public @Nullable Foo field = null;

  public Foo everythingNotNullable(Foo x) {
    // jspecify_nullness_mismatch
    return null;
  }

  public @Nullable Foo everythingNullable(@Nullable Foo x) {
    return null;
  }

  public @NullnessUnspecified Foo everythingUnknown(@NullnessUnspecified Foo x) {
    // jspecify_nullness_not_enough_information
    return null;
  }

  public @Nullable Foo mixed(Foo x) {
    return null;
  }

  public Foo explicitlyNullnessUnspecified(@NullnessUnspecified Foo x) {
    // jspecify_nullness_mismatch
    return null;
  }
}

class Foo {
  public Object foo() {
    // jspecify_nullness_not_enough_information
    return null;
  }
}

@DefaultNonNull
class Instances {
  static final Defaults DEFAULTS = new Defaults();
  static final Foo FOO = new Foo();
}

class Use {
  static void main() {
    Defaults a = Instances.DEFAULTS;
    Foo x = Instances.FOO;

    // jspecify_nullness_mismatch
    a.everythingNotNullable(null).foo();
    a.everythingNotNullable(x).foo();

    // jspecify_nullness_mismatch
    a.everythingNullable(null).foo();

    // jspecify_nullness_not_enough_information
    a.everythingUnknown(null).foo();

    // jspecify_nullness_mismatch
    a.mixed(null).foo();
    // jspecify_nullness_mismatch
    a.mixed(x).foo();

    a.explicitlyNullnessUnspecified(x).foo();
    // jspecify_nullness_not_enough_information
    a.explicitlyNullnessUnspecified(null).foo();

    a.defaultField.foo();

    // jspecify_nullness_mismatch
    a.field.foo();
  }
}
