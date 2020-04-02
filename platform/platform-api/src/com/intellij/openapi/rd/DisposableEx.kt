// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.rd

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use

@Deprecated("Use implementation from `intellij.platform.util.ex` instead.", ReplaceWith("disposable.use(block)"))
inline fun using(disposable: Disposable, block: () -> Unit): Unit = disposable.use { block() }

fun Disposable.attachChild(disposable: Disposable) {
  Disposer.register(this, disposable)
}

@Suppress("ObjectLiteralToLambda") // non-object lambdas fuck up the disposer
fun Disposable.attach(disposable: () -> Unit) {
  Disposer.register(this, object : Disposable {
    override fun dispose() {
      disposable()
    }
  })
}
