// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.rhizomedb

fun interface ReadTrackingContext {
  fun witness(pattern: Pattern)
}

fun Q.withReadTrackingContext(readTrackingContext: ReadTrackingContext): ReadTrackingQueryAPI {
  if (this is ReadTrackingQueryAPI) {
    throw IllegalArgumentException()
  }
  return ReadTrackingQueryAPI(this, readTrackingContext)
}

class ReadTrackingQueryAPI(private val queryAPI: Q,
                           private val readTrackingContext: ReadTrackingContext) : Q by queryAPI {
  override fun <T> queryIndex(indexQuery: IndexQuery<T>): T {
    readTrackingContext.witness(indexQuery.patternHash())
    return queryAPI.queryIndex(indexQuery)
  }

  override fun <T> DbContext<Q>.cachedQuery(query: CachedQuery<T>): CachedQueryResult<T> {
    val res = queryAPI.run { cachedQuery(query) }
    res.patterns.forEach { readTrackingContext.witness(Pattern.fromHash(it)) }
    return res
  }
}

