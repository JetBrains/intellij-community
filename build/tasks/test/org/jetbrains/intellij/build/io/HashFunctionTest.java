/*
 * Copyright 2014 Higher Frequency Trading http://www.higherfrequencytrading.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.intellij.build.io;

import com.dynatrace.hash4j.hashing.Hashing;
import com.intellij.util.lang.Xxh3;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

final class HashFunctionTest {
  public static void test(byte[] data, long eh) {
    int len = data.length;
    testArrays(data, eh, len);
  }

  private static void testArrays(byte[] data, long eh, int len) {
    assertThat(Hashing.xxh3_64().hashBytesToLong(data)).isEqualTo(eh);

    byte[] data2 = new byte[len + 2];
    System.arraycopy(data, 0, data2, 1, len);
    assertThat(Xxh3.hash(data2, 1, len)).isEqualTo(eh);
  }
}
