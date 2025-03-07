// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.multiplatform.shims

import fleet.util.multiplatform.Actual

@fleet.util.multiplatform.Actual("atomicRefImpl")
internal fun atomicRefImplWasmJs(value: Any?): AtomicRef<Any?> = atomicRef(value)

private fun <T> atomicRef(value: T) = object : AtomicRef<T> {
  private var _value: T = value

  override fun get(): T {
    return _value
  }

  override fun set(value: T) {
    _value = value
  }

  override fun getAndSet(value: T): T {
    val result = _value
    _value = value
    return result
  }

  override fun updateAndGet(f: (T) -> T): T {
    _value = f(_value)
    return _value
  }

  override fun getAndUpdate(f: (T) -> T): T {
    val result = _value
    _value = f(_value)
    return result
  }

  override fun compareAndSet(old: T, new: T): Boolean {
    return if (_value == old) {
      _value = new
      true
    } else {
      false
    }
  }

  override fun compareAndExchange(old: T, new: T): T {
    val result = _value
    if (_value == old) {
      _value = new
    }
    return result
  }
}