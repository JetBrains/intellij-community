// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.compilation.cache

import groovy.transform.CompileStatic

@CompileStatic
class CommitsHistory {
  private static final String MASTER_BRANCH = 'master'
  private static final String COMMIT_HISTORY_FILE = 'commit_history.json'

  final String path
  final String defaultBranchPath

  final boolean isDefaultBranch

  CommitsHistory(String branch, String defaultBranch = MASTER_BRANCH) {
    isDefaultBranch = branch == defaultBranch

    defaultBranchPath = defaultBranch == MASTER_BRANCH ? COMMIT_HISTORY_FILE : "$defaultBranch/$COMMIT_HISTORY_FILE"
    path = (branch == null || isDefaultBranch) ? defaultBranchPath : "$branch/$COMMIT_HISTORY_FILE"
  }
}
