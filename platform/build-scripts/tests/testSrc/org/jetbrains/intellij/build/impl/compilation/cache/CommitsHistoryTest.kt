// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.compilation.cache

import org.junit.Test

class CommitsHistoryTest {
  @Test
  fun `commit-history json union test`() {
    val union = CommitsHistory("""{
      "remote1" : [ "commit1.1", "commit1.2" ],
      "remote2" : [ "commit2.1", "commit2.2" ]
    }""") + CommitsHistory("""{
      "remote1" : [ "commit1.3", "commit1.3" ],
      "remote3" : [ "commit3.1" ]
    }""")
    assert((union.commitsForRemote("remote1") - listOf("commit1.1", "commit1.2", "commit1.3")).isEmpty())
    assert((union.commitsForRemote("remote2") - listOf("commit2.1", "commit2.2")).isEmpty())
    assert((union.commitsForRemote("remote3") - listOf("commit3.1")).isEmpty())
  }

  @Test
  fun `commit-history json subtraction test`() {
    val subtraction = CommitsHistory("""{
      "remote1" : [ "commit1.1", "commit1.2" ],
      "remote2" : [ "commit2.1", "commit2.2" ]
    }""") - CommitsHistory("""{
      "remote1" : [ "commit1.2", "commit1.3" ],
      "remote2" : [ "commit2.1", "commit2.3" ],
      "remote3" : [ "commit3.1" ]
    }""")
    assert((subtraction.commitsForRemote("remote1") - "commit1.1").isEmpty())
    assert((subtraction.commitsForRemote("remote2") - "commit2.2").isEmpty())
    assert(subtraction.commitsForRemote("remote3").isEmpty())
  }
}