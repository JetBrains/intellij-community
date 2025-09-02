// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.concurrency.AsyncFuture
import com.intellij.concurrency.AsyncUtil
import com.intellij.util.containers.toArray
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval
import org.jetbrains.annotations.Contract
import org.jetbrains.annotations.Unmodifiable
import java.util.*
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate

interface Query<Result> : Iterable<Result> {

  /**
   * Get all the results in the [Collection]
   *
   * @return results in a collection or empty collection if no results found.
   */
  fun findAll(): @Unmodifiable Collection<Result>

  /**
   * Get the first result or `null` if no results have been found.
   *
   * @return first result of the search or `null` if no results.
   */
  fun findFirst(): Result?

  /**
   * Process search results one-by-one. All the results will be subsequently fed to a `consumer` passed.
   * The consumer might be called on different threads, but by default these calls are mutually exclusive, so no additional
   * synchronization inside consumer is necessary. If you need to process results in parallel, run `forEach()` on
   * the result of [allowParallelProcessing].
   *
   * @param consumer - a processor search results should be fed to.
   * @return `true` if the search was completed normally,
   * `false` if the occurrence processing was cancelled by the processor.
   */
  fun forEach(consumer: Processor<in Result>): Boolean

  @Deprecated("use {@link #forEach(Processor)} instead")
  fun forEachAsync(consumer: Processor<in Result>): @Suppress("DEPRECATION") AsyncFuture<Boolean> {
    @Suppress("DEPRECATION")
    return AsyncUtil.wrapBoolean(forEach(consumer))
  }

  fun toArray(a: Array<@UnsafeVariance Result>): Array<@UnsafeVariance Result> {
    return findAll().toArray(a)
  }

  /**
   * Checks whether predicate is satisfied for every result of this query.
   * This operation short-circuits once predicate returns false.
   * Technically it's equivalent to [forEach], but has better name.
   * Use this method only if your predicate is stateless and side-effect free.
   *
   * @param predicate predicate to test on query results
   * @return true if given predicate is satisfied for all query results.
   */
  @Contract(pure = true)
  fun allMatch(predicate: Predicate<in Result>): Boolean {
    return forEach(Processor { t ->
      predicate.test(t)
    })
  }

  /**
   * Checks whether predicate is satisfied for at least one result of this query.
   * This operation short-circuits once predicate returns true.
   * Use this method only if your predicate is stateless and side-effect free.
   *
   * @param predicate predicate to test on query results
   * @return true if given predicate is satisfied for at least one query result.
   */
  @Contract(pure = true)
  fun anyMatch(predicate: Predicate<in Result>): Boolean {
    return !forEach(Processor { t: Result ->
      !predicate.test(t)
    })
  }

  /**
   * @param transformation pure function
   */
  @Experimental
  fun <R> transforming(transformation: Function<in Result, out Collection<R>>): Query<R> {
    return Queries.getInstance().transforming(this, transformation)
  }

  /**
   * @param mapper pure function
   */
  @Experimental
  fun <R> mapping(mapper: Function<in Result, out R>): Query<R> {
    return transforming { value: Result ->
      listOf(mapper.apply(value))
    }
  }

  /**
   * @param predicate pure function
   */
  @Experimental
  fun filtering(predicate: Predicate<in Result>): Query<Result> {
    return transforming { value ->
      if (predicate.test(value)) {
        listOf(value)
      }
      else {
        emptyList()
      }
    }
  }

  /**
   * @param mapper pure function
   */
  @Experimental
  fun <R> flatMapping(mapper: Function<in Result, out Query<out R>>): Query<R> {
    return Queries.getInstance().flatMapping(this, mapper)
  }

  /**
   * @return an equivalent query whose [forEach] accepts thread-safe consumers, so it may call the consumer in parallel.
   */
  @Contract(pure = true)
  fun allowParallelProcessing(): Query<Result> {
    return this
  }

  @Deprecated(
    "Don't use Query as Iterable. Call {@link #findAll} explicitly, but first,\n" +
    "consider whether it's necessary to find all results at once, e.g., use {@link #anyMatch} or {@link #allMatch}",
    ReplaceWith("findAll()"),
  )
  fun asIterable(): Iterable<Result> {
    return findAll()
  }

  /**
   * @see Iterable.iterator
   */
  @ScheduledForRemoval
  @Deprecated(
    "Don't use Query as Iterable, the results are computed eagerly, which defeats the purpose",
    ReplaceWith("asIterable().iterator()"),
  )
  override fun iterator(): Iterator<Result> {
    @Suppress("DEPRECATION")
    return asIterable().iterator()
  }

  /**
   * @see Iterable#forEach
   */
  @ScheduledForRemoval
  @Deprecated(
    "Don't use Query as Iterable, the results are computed eagerly, which defeats the purpose",
    ReplaceWith("asIterable().forEach(action)"),
  )
  override fun forEach(action: Consumer<in Result>) {
    @Suppress("DEPRECATION")
    asIterable().forEach(action)
  }

  /**
   * @see Iterable#spliterator
   * @deprecated
   */
  @ScheduledForRemoval
  @Deprecated(
    "Don't use Query as Iterable, the results are computed eagerly, which defeats the purpose",
    ReplaceWith("asIterable().spliterator()"),
  )
  override fun spliterator(): Spliterator<Result> {
    @Suppress("DEPRECATION")
    return asIterable().spliterator()
  }

  @Experimental
  fun interceptWith(interceptor: QueryExecutionInterceptor): Query<Result> {
    val query = this
    return object : AbstractQuery<@UnsafeVariance Result>() {
      override fun processResults(consumer: Processor<in Result>): Boolean {
        return interceptor.intercept {
          delegateProcessResults(query, consumer)
        }
      }
    }
  }

  @ScheduledForRemoval
  @Experimental
  @Deprecated("use {@link #interceptWith}")
  fun wrap(wrapper: @Suppress("DEPRECATION") QueryWrapper<Result>): Query<Result> {
    val query: Query<Result> = this
    return object : AbstractQuery<Result>() {
      override fun processResults(consumer: Processor<in Result>): Boolean {
        return wrapper.wrapExecution(Processor { c -> delegateProcessResults(query, c) }, consumer)
      }
    }
  }
}
