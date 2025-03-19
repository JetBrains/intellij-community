// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.rhizomedb

import fleet.util.openmap.OpenMap

/**
 * The result of applying fn to a [DB] value
 * [dbBefore] - DB snapshot before change
 * [dbAfter] - DB snapshot right after the change
 * [novelty] - delta between the two in a form of Datoms:
 *             (e, a, v, added?) - added? is a flag whether the fact is asserted or retracted during the change
 * [meta] - arbitrary meta data associated with the change. Can be written by the change-function or by KernelMiddleware
 */
data class Change(val dbBefore: DB,
                  val dbAfter: DB,
                  val novelty: Novelty,
                  val meta: OpenMap<ChangeScope>)

/**
 * @see [DB.change]
 * */
fun<T> DB.changeAndReturn(defaultPart: Part = 1, f: ChangeScope.() -> T): Pair<T, Change> = run {
  var res: T? = null
  val change = change(defaultPart) {
    res = f()
  }
  @Suppress("UNCHECKED_CAST")
  res as T to change
}

/**
 * Performs transaction on a given [DB] value, by applying [f] to a mutable fork of receiver
 * and returns a corresponding [Change] object.
 * @param defaultPart is the future value of [ChangeScope.defaultPart]
 * */
fun DB.change(defaultPart: Part = 1, f: ChangeScope.() -> Unit): Change = let { dbBefore ->
  val mutableDb = dbBefore.mutable(defaultPart)
  val mutableNovelty = MutableNovelty()
  asOf(mutableDb.collectingNovelty(mutableNovelty::add)) {
    withChangeScope {
      meta[MutableNoveltyKey] = mutableNovelty
      f()
      meta.remove(MutableNoveltyKey)
    }
    setPoison(Throwable("change closed"))
  }
  Change(dbBefore = dbBefore,
         dbAfter = mutableDb.snapshot(),
         novelty = mutableNovelty.persistent(),
         meta = mutableDb.meta.persistent())
}

object MutableNoveltyKey : ChangeScopeKey<MutableNovelty>
