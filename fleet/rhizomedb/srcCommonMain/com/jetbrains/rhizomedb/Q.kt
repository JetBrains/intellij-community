package com.jetbrains.rhizomedb

/**
 * Main interface for queries.
 *
 * It exists to abstract DB and MutableDb and allow for various interceptions and customizations.
 * */
interface Q {
  /**
   * Indexes access. Allows to implement and intercept all of the data access.
   * i.e. used to implement read-tracking
   * @see IndexQuery
   * */
  fun <T> queryIndex(indexQuery: IndexQuery<T>): T

  /**
   * Performs cached query. [CachedQuery] is read-tracked and memoized based of the trace.
   * Part of the query result is a read-trace of the query.
   * On each transaction, stale values are persistently evicted from cache.
   * Previous cache is untouched. The cache acts like a value.
   * Other eviction strategies are implementation details.
   * */
  fun <T> DbContext<Q>.cachedQuery(query: CachedQuery<T>): CachedQueryResult<T>

  /**
   * Separate way to assert entity existence. It exists as a separate method from [queryIndex] because it should not be tracked as a regular read,
   * because it may yield exactly one result.
   * */
  fun assertEntityExists(eid: EID, accessedAttribute: Attribute<*>?, referenceAttribute: Attribute<*>?): Nothing?

  /**
   * A way to reset the pipeline.
   * [original] is guaranteed to be either [DB] or [MutableDb]
   * */
  val original: Q
}

/**
 * Result of [Q.cachedQuery]
 * */
class CachedQueryResult<T>(val result: T, val patterns: LongArray)

/**
 * Implementation serves as a cache key, should be equal when arguments of the query are equal
 */
interface CachedQuery<out T> {
  fun DbContext<Q>.query(): T
}
