// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.rhizomedb.impl

import com.jetbrains.rhizomedb.EID
import com.jetbrains.rhizomedb.MAX_PART
import com.jetbrains.rhizomedb.Part
import com.jetbrains.rhizomedb.withPart
import fleet.multiplatform.shims.ConcurrentHashMap
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.incrementAndFetch

sealed interface EidGen {
  companion object : EidGen {
    private val eidGens: Array<AtomicInt> = Array(MAX_PART + 1) { AtomicInt(0) }.also {
      it[0].store(17)
    }
    private val eidMemo: ConcurrentHashMap<String, EID> = ConcurrentHashMap()

    override fun freshEID(part: Part): EID =
      withPart(eidGens[part].incrementAndFetch(), part)

    override fun memoizedEID(part: Part, ident: String): EID =
      eidMemo.computeIfAbsent(ident) { _ -> freshEID(part) }
  }

  fun freshEID(part: Part): EID
  fun memoizedEID(part: Part, ident: String): EID
}
