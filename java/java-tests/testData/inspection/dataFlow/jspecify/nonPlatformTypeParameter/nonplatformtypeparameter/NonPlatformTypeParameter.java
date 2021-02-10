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

package nonplatformtypeparameter;

import org.jspecify.annotations.DefaultNonNull;
import org.jspecify.annotations.Nullable;

public class NonPlatformTypeParameter<T extends @Nullable Object> {
  public void foo(T t) {}

  public <E extends @Nullable Object> void bar(E e) {}
}

class Test {}

@DefaultNonNull
class Use {
  public <T extends Test> void main(
      NonPlatformTypeParameter<@Nullable Object> a1, NonPlatformTypeParameter<Test> a2, T x) {
    a1.foo(null);
    a1.<@Nullable Test>bar(null);
    /*
     * TODO(cpovirk): In similar existing JSpecify samples, we mark the following line as
     * not-enough-information. However, similar existing Kotlin samples treat it as a mismatch. We
     * need to resolve how defaulting works on type-variable usages.
     */
    // a1.<T>bar(null);
    a1.<T>bar(x);

    // TODO(cpovirk): See the TODO above.
    // a2.foo(null);
    a2.<@Nullable Test>bar(null);
    // TODO(cpovirk): See the TODO above.
    // a2.<T>bar(null);
    a2.<T>bar(x);
  }
}
