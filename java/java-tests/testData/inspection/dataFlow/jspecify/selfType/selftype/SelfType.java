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
package selftype;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

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

// test:cannot-convert:AK? to SelfType!<AK!>
class AKN extends SelfType<@Nullable AK> {}

class BK extends B {}

// jspecify_nullness_not_enough_information
class CK extends C<CK> {}

@NullMarked
// test:cannot-convert:CK? to C!<CK!>
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
    // test:cannot-convert:null? to AK!
    ak().foo(null);

    // test:cannot-convert:null? to AK!
    akn().foo(null);

    bk().foo(bk());
    // test:cannot-convert:null? to B!
    bk().foo(null);

    ck().foo(ck());
    // test:cannot-convert:null? to CK!
    ck().foo(null);

    // test:cannot-convert:null? to CK!
    ckn().foo(null);
  }
}
