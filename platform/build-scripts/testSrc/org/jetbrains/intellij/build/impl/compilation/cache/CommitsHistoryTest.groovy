// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.compilation.cache

import org.junit.Test

class CommitsHistoryTest {
  @Test
  void 'commit-history json union test'() {
    def union = new CommitsHistory('''{
      "remote1" : [ "commit1.1", "commit1.2" ],
      "remote2" : [ "commit2.1", "commit2.2" ]
    }''') + new CommitsHistory('''{
      "remote1" : [ "commit1.3", "commit1.3" ],
      "remote3" : [ "commit3.1" ]
    }''')
    assert (union.commitsForRemote('remote1') - ['commit1.1', 'commit1.2', 'commit1.3']).isEmpty()
    assert (union.commitsForRemote('remote2') - ['commit2.1', 'commit2.2']).isEmpty()
    assert (union.commitsForRemote('remote3') - ['commit3.1']).isEmpty()
  }
}