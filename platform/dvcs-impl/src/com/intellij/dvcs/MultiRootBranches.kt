// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("MultiRootBranches")
package com.intellij.dvcs

import com.intellij.dvcs.repo.Repository

fun <T> getCommonName(repositories: Collection<T>, nameSupplier: (T) -> String?): String? {
  return repositories.map { nameSupplier(it) }.distinct().singleOrNull()
}

/**
 * For the given repositories, returns the name of the current branch if it is the same for all repositories
 * (AKA "all repositories are on the same branch"), and `null` if they are on different branches,
 * in particular, if any of repositories is in the detached HEAD.
 */
fun Collection<Repository>.getCommonCurrentBranch(): String? {
  return getCommonName(this, Repository::getCurrentBranchName)
}

fun Collection<Repository>.diverged() = getCommonCurrentBranch() == null