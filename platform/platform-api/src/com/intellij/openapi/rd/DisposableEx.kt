// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.rd

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.lifetime.isAlive
import com.jetbrains.rd.util.lifetime.onTermination

inline fun using(disposable: Disposable, block: () -> Unit) {
  try {
    block()
  } finally {
    Disposer.dispose(disposable)
  }
}

fun Disposable.defineNestedLifetime(): LifetimeDefinition {
  val lifetimeDefinition = Lifetime.Eternal.createNested()
  this.attach { if (lifetimeDefinition.lifetime.isAlive) lifetimeDefinition.terminate() }
  return lifetimeDefinition
}

fun Disposable.createLifetime(): Lifetime = this.defineNestedLifetime().lifetime

fun Disposable.doIfAlive(action: (Lifetime) -> Unit) {
  val disposableLifetime: Lifetime?
  if(Disposer.isDisposed(this)) return

  try {
    disposableLifetime = this.createLifetime()
  } catch(t : Throwable){
    //do nothing, there is no other way to handle disposables
    return
  }

  action(disposableLifetime)
}

fun Lifetime.createNestedDisposable(debugName: String = "lifetimeToDisposable"): Disposable {
  val d = Disposer.newDisposable(debugName)

  this.onTermination {
    Disposer.dispose(d)
  }
  return d
}

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
