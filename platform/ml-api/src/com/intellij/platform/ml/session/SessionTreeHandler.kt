// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.session

internal fun interface SessionTreeHandler<T : DescribedSessionTree<R, P>, R, P> {
  fun handleTree(sessionTree: T)
}
