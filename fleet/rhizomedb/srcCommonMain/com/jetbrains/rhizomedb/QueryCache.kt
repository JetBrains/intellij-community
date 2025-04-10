// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.rhizomedb

import fleet.fastutil.longs.LongArrayList
import fleet.fastutil.longs.toArray
import fleet.util.computeShim
import fleet.util.updateAndGet
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.persistentHashSetOf
import kotlin.concurrent.atomics.AtomicReference

class QueryCache private constructor(private val cache: AtomicReference<QueryCacheData>) {

  private data class QueryCacheData(
    val patternToQuery: PersistentMap<Long, PersistentSet<CachedQuery<*>>>,
    val queryToResult: PersistentMap<CachedQuery<*>, CachedQueryResult<*>>
  ) {

    fun <T> insert(query: CachedQuery<T>, result: CachedQueryResult<T>): QueryCacheData {
      val oldPatterns = queryToResult[query]?.patterns
      val patternToQueryPrime = patternToQuery.builder()
      oldPatterns?.forEach { oldPattern ->
        if (!result.patterns.contains(oldPattern)) {
          val queries = patternToQueryPrime[oldPattern]!!
          val queriesPrime = queries.remove(query)
          when {
            queriesPrime.isEmpty() -> patternToQueryPrime.remove(oldPattern)
            else -> patternToQueryPrime[oldPattern] = queriesPrime
          }
        }
      }

      result.patterns.forEach { newPattern ->
        if (oldPatterns == null || !oldPatterns.contains(newPattern)) {
          patternToQueryPrime.computeShim(newPattern) { _, queries ->
            (queries ?: persistentHashSetOf()).add(query)
          }
        }
      }
      return QueryCacheData(patternToQuery = patternToQueryPrime.build(),
                            queryToResult = queryToResult.put(query, result))
    }

    fun invalidate(novelty: Iterable<Datom>): QueryCacheData {
      val patternToQueryPrime = patternToQuery.builder()
      val queryToResultPrime = queryToResult.builder()
      for (datom in novelty) {
        val patternHashes = Pattern.patternHashes(datom.eid, datom.attr, datom.value)
        for (hash in patternHashes) {
          patternToQueryPrime.remove(hash)
          patternToQuery[hash]?.forEach { query ->
            queryToResultPrime.remove(query)
          }
        }
      }
      return QueryCacheData(patternToQueryPrime.build(),
                            queryToResultPrime.build())
    }

    fun find(query: CachedQuery<*>): CachedQueryResult<*>? =
      queryToResult[query]
  }


  companion object {
    fun empty(): QueryCache =
      QueryCache(kotlin.concurrent.atomics.AtomicReference(QueryCacheData(persistentHashMapOf(), persistentHashMapOf())))
  }

  fun <T> performQuery(query: CachedQuery<T>, compute: () -> CachedQueryResult<T>): CachedQueryResult<T> =
    @Suppress("UNCHECKED_CAST")
    (cache.load().find(query) as CachedQueryResult<T>?) ?: run {
      val res = compute()
      cache.updateAndGet { index ->
        val existing = index.find(query)
        when {
          existing == null -> index.insert(query, res)
          else -> index
        }
      }
      res
    }

  fun invalidate(novelty: Iterable<Datom>): QueryCache =
    QueryCache(AtomicReference(cache.load().invalidate(novelty)))
}

internal fun <T> DbContext<Q>.cachedQueryImpl(queryCache: QueryCache, query: CachedQuery<T>): CachedQueryResult<T> {
  return queryCache.performQuery(query) {
    val original = impl
    val patterns = LongArrayList()
    val result = alter(object : Q by impl {
      override fun <T> queryIndex(indexQuery: IndexQuery<T>): T {
        patterns.add(indexQuery.patternHash().hash)
        return original.queryIndex(indexQuery)
      }

      override fun <T> DbContext<Q>.cachedQuery(query: CachedQuery<T>): CachedQueryResult<T> {
        val res = with(original) {
          cachedQuery(query)
        }
        patterns.addElements(patterns.size, res.patterns, 0, res.patterns.size)
        return res
      }
    }) {
      with(query) { query() }
    }
    CachedQueryResult(result, patterns.toArray())
  }
}
