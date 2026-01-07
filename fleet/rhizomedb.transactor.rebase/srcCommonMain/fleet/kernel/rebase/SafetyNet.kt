// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel.rebase

import com.jetbrains.rhizomedb.*
import fleet.kernel.*
import fleet.fastutil.ints.IntList

internal fun Mut.preventReadsFromLocal(): Mut = intersectingPartitions(IntList.of(SchemaPart, SharedPart, CommonPart))
  .cachedQueryWithParts(IntList.of(SchemaPart, SharedPart, CommonPart))
  .expandAndMutateWithParts(AllParts)

internal fun Mut.preventRefsFromShared(): Mut =
  processingNovelty { novelty, _ ->
    for (datom in novelty) {
      val (e, a, v, _, added) = datom
      if (added &&
          a.schema.isRef &&
          a != Entity.Type.attr &&
          partition(e) == SharedPart &&
          partition(v as EID) != SharedPart) {
        error("trying to add a local reference to shared partition ${displayDatom(datom)}")
      }
    }
  }
