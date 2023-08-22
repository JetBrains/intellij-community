/*
 * Copyright (C) 2002-2020 Sebastiano Vigna
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
package com.intellij.psi.stubs;

final class Hash {
  static final float DEFAULT_LOAD_FACTOR = 0.75f;
  static final int DEFAULT_INITIAL_SIZE = 16;

  private static final int INT_PHI = 0x9E3779B9;

  static int arraySize(final int expected, final float f) {
    final long s = Math.max(2, nextPowerOfTwo((long)Math.ceil(expected / f)));
    if (s > (1 << 30)) throw new IllegalArgumentException("Too large (" + expected + " expected elements with load factor " + f + ")");
    return (int)s;
  }

  @SuppressWarnings("DuplicatedCode")
  private static long nextPowerOfTwo(long x) {
    if (x == 0) return 1;
    x--;
    x |= x >> 1;
    x |= x >> 2;
    x |= x >> 4;
    x |= x >> 8;
    x |= x >> 16;
    return (x | x >> 32) + 1;
  }

  static int maxFill(final int n, final float f) {
    /* We must guarantee that there is always at least
     * one free entry (even with pathological load factors). */
    return Math.min((int)Math.ceil(n * f), n - 1);
  }

  static int mix(final int x) {
    final int h = x * INT_PHI;
    return h ^ (h >>> 16);
  }
}
