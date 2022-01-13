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

package selftype;

import org.jspecify.nullness.NullMarked;
import org.jspecify.nullness.Nullable;

@NullMarked
public class SelfType<T extends SelfType<T>> {
  public void foo(T t) {}
}

// jspecify_nullness_not_enough_information
class B extends SelfType<B> {}

@NullMarked
class C<E extends C<E>> extends SelfType<E> {}

// jspecify_nullness_not_enough_information
class AK extends SelfType<AK> {}

// jspecify_nullness_mismatch
class AKN extends SelfType<@Nullable AK> {}

class BK extends B {}

// jspecify_nullness_not_enough_information
class CK extends C<CK> {}

@NullMarked
// jspecify_nullness_mismatch
abstract class Super extends C<@Nullable CK> {
  abstract AK ak();

  abstract AKN akn();

  abstract BK bk();

  abstract CK ck();

  abstract CKN ckn();
}

abstract class CKN extends Super {
  public void main() {
    ak().foo(ak());
    // jspecify_nullness_mismatch
    ak().foo(null);

    // jspecify_nullness_mismatch
    akn().foo(null);

    bk().foo(bk());
    // jspecify_nullness_mismatch
    bk().foo(null);

    ck().foo(ck());
    // jspecify_nullness_mismatch
    ck().foo(null);

    // jspecify_nullness_mismatch
    ckn().foo(null);
  }
}
