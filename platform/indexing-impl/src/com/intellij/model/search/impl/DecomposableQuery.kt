// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search.impl

import com.intellij.util.Query

internal interface DecomposableQuery<R> : Query<R> {

  fun decompose(): Requests<R>
}
