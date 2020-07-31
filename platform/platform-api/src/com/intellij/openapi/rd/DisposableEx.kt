// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("DisposableEx")

package com.intellij.openapi.rd

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.lifetime.isAlive
import com.jetbrains.rd.util.lifetime.onTermination
import org.jetbrains.annotations.ApiStatus

@ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
@Deprecated("Use version from `LifetimeDisposableEx`")
fun defineNestedLifetime(disposable: Disposable): LifetimeDefinition {
  val lifetimeDefinition = Lifetime.Eternal.createNested()
  if (Disposer.isDisposing(disposable) || Disposer.isDisposed(disposable)) {
    lifetimeDefinition.terminate()
    return lifetimeDefinition
  }

  disposable.attach { if (lifetimeDefinition.lifetime.isAlive) lifetimeDefinition.terminate() }
  return lifetimeDefinition
}

@ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
@Deprecated("Use version from `LifetimeDisposableEx`")
internal fun doIfAlive(disposable: Disposable, action: (Lifetime) -> Unit) {
  val disposableLifetime: Lifetime?
  if (Disposer.isDisposed(disposable)) {
    return
  }
  try {
    disposableLifetime = defineNestedLifetime(disposable).lifetime
  }
  catch (t: Throwable) {
    //do nothing, there is no other way to handle disposables
    return
  }

  action(disposableLifetime)
}

@ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
@Deprecated("Use version from `LifetimeDisposableEx`")
internal fun Lifetime.createNestedDisposable(debugName: String = "lifetimeToDisposable"): Disposable {
  val d = Disposer.newDisposable(debugName)
  this.onTermination {
    Disposer.dispose(d)
  }
  return d
}

@Suppress("ObjectLiteralToLambda")
inline fun Disposable.attach(crossinline disposable: () -> Unit) {
  Disposer.register(this, object : Disposable {
    override fun dispose() {
      disposable()
    }
  })
}