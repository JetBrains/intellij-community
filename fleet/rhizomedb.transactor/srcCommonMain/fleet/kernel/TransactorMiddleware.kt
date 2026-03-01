// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel

import com.jetbrains.rhizomedb.ChangeScope

/**
 * [TransactorMiddleware] intercepts all the changes happening to the [Transactor]
 **/
interface TransactorMiddleware {
  object Identity : TransactorMiddleware {
    override fun ChangeScope.performChange(next: ChangeScope.() -> Unit): Unit {
      return next()
    }

    context(cs: ChangeScope)
    override fun initDb() {
    }
  }

  /**
   * (f + g)(x) = f(g(x))
   * */
  operator fun plus(rhs: TransactorMiddleware): TransactorMiddleware {
    val lhs = this
    return object : TransactorMiddleware {
      override fun ChangeScope.performChange(next: ChangeScope.() -> Unit) {
        with(lhs) { performChange { with(rhs) { performChange(next) } } }
      }

      context(cs: ChangeScope)
      override fun initDb() {
        lhs.initDb()
        rhs.initDb()
      }
    }
  }

  fun ChangeScope.performChange(next: ChangeScope.() -> Unit)

  context(cs: ChangeScope)
  fun initDb()
}