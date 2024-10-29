// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.rhizomedb

import org.jetbrains.annotations.TestOnly
import kotlin.coroutines.cancellation.CancellationException

//fun getStack(): Throwable = Throwable("dbcontext creation stack")

/**
 * Holds the current pipeline of [Q] or [Mut].
 * Usually it is thread-bound.
 * This is where all the db reads and writes are directed to.
 * */
class DbContext<out QQ : Q>(
  var _private_value: Any,
  val dbSource: Any?
  //var stack: Throwable? = getStack()
) {
  val impl: QQ
    get() {
      val q = _private_value
      return when {
        q is CancellationException -> throw CancellationException("DBContext is poisoned", q)
        q is Throwable -> throw RuntimeException("DBContext is poisoned", q)
        else -> q as QQ
      }
    }

  fun set(q: Q) {
    //    stack = getStack()
    _private_value = q
  }

  fun setPoison(x: Throwable) {
    _private_value = x
  }

  companion object {
    @JvmStatic
    val threadLocal: ThreadLocal<DbContext<*>?> = ThreadLocal()

    /**
     * Current context, associated with the thread.
     * */
    val threadBound: DbContext<Q>
      get() =
        threadLocal.get() ?: throw OutOfDbContext()
    
    /**
     * Current context, associated with the thread.
     * */
    val threadBoundOrNull: DbContext<Q>?
      get() =
        threadLocal.get()

    @TestOnly
    fun isBound(): Boolean = 
      threadLocal.get().let {
        it != null && it.impl !is Throwable
      }

    fun clearThreadBoundDbContext() {
      threadLocal.set(null)
    }

    inline fun <T, U : Q> bind(facade: DbContext<U>, f: DbContext<U>.() -> T): T {
      val old = threadLocal.get()
      threadLocal.set(facade)
      try {
        return facade.f()
      }
      finally {
        threadLocal.set(old)
      }
    }
  }

  inline fun <T, U : Q> alter(dbContextPrime: U, f: DbContext<U>.() -> T): T {
    val oldContext = _private_value
    _private_value = dbContextPrime
    return try {
      (this as DbContext<U>).f()
    }
    finally {
      _private_value = oldContext
    }
  }

  inline fun <T> ensureMutable(f: DbContext<Mut>.() -> T): T {
    return if (_private_value is Mut) {
      (this as DbContext<Mut>).f()
    }
    else {
      throw OutOfMutableDbContext()
    }
  }
}

/**
 * Set up db to be used by entities.
 *
 * Entity object holds no data on its own and requires context db to access its properties. This is true both for getters and setters.
 * One object can be used to read different versions of db and changes made to this object will be reflected only by current db.
 *
 * ```
 * interface Foo : LegacyEntity {
 *   var value : String
 * }
 *
 * var foo : Foo? = null
 * val db1 = emptyDb().tx { foo = new(Foo::class) { value = "1" } }.after
 * val db2 = emptyDb().tx { foo.value = "2" }.after
 * asOf(db1) {
 *   assertEquals("1", foo.value)
 * }
 * asOf(db2) {
 *   assertEquals("2", foo.value)
 * }
 * assertThrows<Throwable>{ foo.value }
 * ```
 */
fun <T, U : Q> asOf(queryAPI: U, f: DbContext<U>.() -> T): T =
  DbContext.bind(DbContext<U>(queryAPI, null)) { f() }
