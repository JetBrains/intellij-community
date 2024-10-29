// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

internal fun <T> JvmAtomicReference(value: T) = object : AtomicRef<T> {
  private val reference = java.util.concurrent.atomic.AtomicReference(value)

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
