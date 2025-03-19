// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.multiplatform.shims

import fleet.util.multiplatform.Actual
import java.util.concurrent.atomic.AtomicReference

@Actual("atomicRefImpl")
internal fun atomicRefImplJvm(value: Any?): AtomicRef<Any?> = atomicRef(value)

private fun <T> atomicRef(value: T) = object : AtomicRef<T> {
  private val reference = AtomicReference(value)

  override fun get(): T {
    return reference.get()
  }

  override fun set(value: T) {
    reference.set(value)
  }

  override fun updateAndGet(f: (T) -> T): T {
    return reference.updateAndGet(f)
  }

  override fun getAndUpdate(f: (T) -> T): T {
    return reference.getAndUpdate(f)
  }

  override fun getAndSet(value: T): T {
    return reference.getAndSet(value)
  }

  override fun compareAndSet(old: T, new: T): Boolean {
    return reference.compareAndSet(old, new)
  }

  override fun compareAndExchange(old: T, new: T): T {
    return reference.compareAndExchange(old, new)
  }
}
