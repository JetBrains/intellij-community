// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.compilation.cache

import org.assertj.core.api.BDDAssertions.then
import org.jetbrains.intellij.build.jpsCache.CommitHistory
import org.junit.Test

class CommitHistoryTest {
  @Test
  fun `commit-history json union test`() {
    val union = CommitHistory("""{
      "remote1" : [ "commit1.1", "commit1.2" ],
      "remote2" : [ "commit2.1", "commit2.2" ]
    }""") + CommitHistory("""{
      "remote1" : [ "commit1.3", "commit1.3" ],
      "remote3" : [ "commit3.1" ]
    }""")
    then(union.commitsForRemote("remote1")).containsExactlyInAnyOrder("commit1.1", "commit1.2", "commit1.3")
    then(union.commitsForRemote("remote2")).containsExactlyInAnyOrder("commit2.1", "commit2.2")
    then(union.commitsForRemote("remote3")).containsExactlyInAnyOrder("commit3.1")
  }

  @Test
  fun `commit-history json subtraction test`() {
    val subtraction = CommitHistory("""{
      "remote1" : [ "commit1.1", "commit1.2" ],
      "remote2" : [ "commit2.1", "commit2.2" ]
    }""") - CommitHistory("""{
      "remote1" : [ "commit1.2", "commit1.3" ],
      "remote2" : [ "commit2.1", "commit2.3" ],
      "remote3" : [ "commit3.1" ]
    }""")
    then(subtraction.commitsForRemote("remote1")).containsExactlyInAnyOrder("commit1.1")
    then(subtraction.commitsForRemote("remote2")).containsExactlyInAnyOrder("commit2.2")
    then(subtraction.commitsForRemote("remote3")).isEmpty()
  }
}