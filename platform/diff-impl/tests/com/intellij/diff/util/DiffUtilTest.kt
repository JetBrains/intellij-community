/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.diff.util

import com.intellij.diff.DiffTestCase
import com.intellij.util.containers.ContainerUtil

class `DiffUtil getSortedIndexes() Test` : DiffTestCase() {
  fun testSmth() {
    doTest(1, 2, 3, 4, 5, 6, 7, 8) { v1, v2 -> v1 - v2 }

    doTest(8, 7, 6, 5, 4, 3, 2, 1) { v1, v2 -> v1 - v2 }

    doTest(1, 3, 5, 7, 8, 6, 4, 2) { v1, v2 -> v1 - v2 }

    doTest(1, 2, 3, 4, 5, 6, 7, 8) { v1, v2 -> v2 - v1 }

    doTest(8, 7, 6, 5, 4, 3, 2, 1) { v1, v2 -> v2 - v1 }

    doTest(1, 3, 5, 7, 8, 6, 4, 2) { v1, v2 -> v2 - v1 }
  }

  private fun <T> doTest(vararg values: T, comparator: (T, T) -> Int) {
    val list = values.toList()

    val sortedIndexes = DiffUtil.getSortedIndexes(list, comparator);
    val expected = ContainerUtil.sorted(list, comparator);
    val actual = (0..values.size - 1).map { values[sortedIndexes[it]] }

    assertOrderedEquals(actual, expected)
    assertEquals(sortedIndexes.toSet().size, list.size)
  }
}
