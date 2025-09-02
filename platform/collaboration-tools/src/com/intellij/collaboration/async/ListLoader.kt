// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.async

import com.intellij.collaboration.async.ListLoader.State
import com.intellij.collaboration.async.PaginatedPotentiallyInfiniteListLoader.PageInfo
import com.intellij.collaboration.util.ResultUtil.runCatchingUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.ApiStatus

/**
 * Represents some list of data we need to fetch and maintain to show in UI.
 *
 * 1. 'reload' to completely clear the list and restart loading.
 * 2. 'loadMore', let's call it 'horizontal loading': it's when the list might be
 *     endless and more entries are requested.
 * 3. 'refresh', a soft 'reload', call it 'vertical loading': it's when the list might
 *     have changes in general, items should be re-fetched most-visible first.
 * 4. 'update' when a client-side update should be applied on the client immediately, but refresh
 *     might later override it.
 */
@ApiStatus.Internal
interface ListLoader<V> {
  data class State<V>(
    val list: List<V>? = null,
    val error: Throwable? = null,
  )

  /**
   * The state of the data collected and maintained by this holder.
   * If an error is present, it's always the latest unhandled exception.
   * If an exception occurs, the list may be updated or it may be left the
   * same as before. This is at the discretion of the implementing class.
   *
   * Updated and managed dynamically by the updating methods defined here.
   */
  val stateFlow: StateFlow<State<V>>

  val isBusyFlow: StateFlow<Boolean>
}

/**
 * The state of the data collected and maintained by this holder
 * represented as a [Result]: [Result.failure] if there is some error
 * present in the current state, [Result.success] otherwise.
 */
@get:ApiStatus.Internal
@OptIn(ExperimentalCoroutinesApi::class)
val <V> ListLoader<V>.resultOrErrorFlow: Flow<Result<List<V>>>
  get() = stateFlow
    .mapLatest { if (it.list == null) null else it.error?.let { Result.failure(it) } ?: Result.success(it.list) }
    .filterNotNull()

@ApiStatus.Internal
sealed interface Change<V>
@ApiStatus.Internal
data class AddedFirst<V>(val value: V) : Change<V>
@ApiStatus.Internal
data class AddedLast<V>(val value: V) : Change<V>
@ApiStatus.Internal
data class AddedAllLast<V>(val values: List<V>) : Change<V>
@ApiStatus.Internal
open class Deleted<V>(val isDeleted: (V) -> Boolean) : Change<V>
@ApiStatus.Internal
class AllDeleted<V> : Deleted<V>({ true })
@ApiStatus.Internal
data class Updated<V>(val updater: (V) -> V) : Change<V>

@ApiStatus.Internal
abstract class MutableListLoader<V> : ListLoader<V> {
  /**
   * Mutable version of the state flow. To be directly modified only by calls to [update].
   */
  protected val mutableStateFlow: MutableStateFlow<State<V>> = MutableStateFlow(State())

  override val stateFlow: StateFlow<State<V>> = mutableStateFlow.asStateFlow()

  /**
   * Manually updates the list and cancels all running refreshes
   * so that changes to the list are not immediately overwritten
   * by scheduled refreshes.
   *
   * This function should only be used when a server-side change
   * of the list is expected by user action. We can change the
   * list locally immediately, but the next refresh will overwrite
   * the local change.
   *
   * Alternatively, when [refresh] is left unimplemented, this
   * function can be used as the main source of truth when updates
   * aren't polled, but are pushed to the IDE.
   *
   * @param change An object that represents a change to be applied.
   */
  fun update(change: Change<V>) {
    mutableStateFlow.update { current ->
      State(
        when (change) {
          is AddedFirst ->
            listOf(change.value) + (current.list ?: listOf())
          is AddedLast ->
            (current.list ?: listOf()) + listOf(change.value)
          is AddedAllLast ->
            (current.list ?: listOf()) + change.values
          is Deleted ->
            current.list?.filterNot { change.isDeleted(it) }
          is Updated ->
            current.list?.map(change.updater)
        }, current.error)
    }
  }
}

@ApiStatus.Internal
interface ReloadableListLoader {
  /**
   * Empty any existing list, cancel any existing refresh requests.
   * Stop tracking all server-side data. Basically, reset this list.
   *
   * Then, load the first 'page' of data. After that, one can optionally
   * call [PotentiallyInfiniteListLoader.loadAll] or [PotentiallyInfiniteListLoader.loadMore]
   * to pre-fetch other data that may be displayed next.
   */
  suspend fun reload()

  /**
   * Poll for changes of the loaded data and fetch added data at
   * the 'end' of the list.
   *
   * The 'end' of the list in this sentence is the part of the list
   * that has the latest added values.
   *
   * This is the main function to implement for polling for changes.
   */
  suspend fun refresh()
}

@ApiStatus.Internal
interface PotentiallyInfiniteListLoader {
  /**
   * Loads a new 'page' worth of items and adds them to the list.
   * After this, refresh will keep track of the items added from
   * this 'page'.
   */
  suspend fun loadMore()

  /**
   * Loads all 'pages' of items and adds them to the list.
   *
   * Implementation may be optimized by requesting pages in parallel.
   */
  suspend fun loadAll()
}

@ApiStatus.Internal
interface ReloadablePotentiallyInfiniteListLoader<V>
  : ListLoader<V>, ReloadableListLoader, PotentiallyInfiniteListLoader

@ApiStatus.Internal
abstract class PaginatedPotentiallyInfiniteListLoader<PI : PageInfo<PI>, K, V>(
  private val initialPageInfo: PI,

  private val extractKey: (V) -> K,
  private var shouldTryToLoadAll: Boolean = false,
) : MutableListLoader<V>(), ReloadablePotentiallyInfiniteListLoader<V> {
  interface PageInfo<PI : PageInfo<PI>> {
    fun createNextPageInfo(): PI?
  }

  /**
   * Represents a single 'page' of data. In UI this 'page' does not necessarily need to
   * be displayed as a page. Instead, it can be a part of a potentially very long list.
   * For instance, the list of merge requests, or the list of commits, notes and discussions
   * in a big MR.
   */
  protected data class Page<PageInfo, V>(
    val info: PageInfo?,
    val list: List<V>
  )

  /**
   * The pages that are currently tracked.
   */
  private var pages: State<Page<PI, V>> = State()

  /**
   * Lock for operating on the list of pages.
   */
  private val operationLock = Mutex()

  private val _isBusyFlow: MutableStateFlow<Boolean> = MutableStateFlow(false)
  override val isBusyFlow: StateFlow<Boolean> = _isBusyFlow.asStateFlow()

  override suspend fun reload() {
    withLock {
      // Reset any errors that were present
      doEmitPages(State())

      // Perform a load by default to start seeing data.
      val loadedMoreData = loadMoreImpl()
      if (loadedMoreData && shouldTryToLoadAll) {
        loadAllImpl()
      }
    }
  }

  private suspend fun doRefresh() {
    coroutineScope {
      val currentPages = pages.list ?: listOf()
      doEmitPages(
        runCatchingUser {
          State(currentPages.mapNotNull { page ->
            if (page.info == null) return@mapNotNull null

            performRequestAndProcess(page.info) { pageInfo, results ->
              page.copy(info = pageInfo, list = results ?: page.list)
            }
          }.toList(), null)
        }.getOrElse {
          State(currentPages, it)
        })
    }

    if (shouldTryToLoadAll) {
      loadAllImpl()
    }
  }

  override suspend fun refresh() {
    withLock {
      doRefresh()
    }
  }

  override suspend fun loadMore() {
    withLock {
      loadMoreImpl()
    }
  }

  override suspend fun loadAll() {
    withLock {
      loadAllImpl()
    }
  }

  /**
   * Loads the next page of data.
   */
  private suspend fun loadMoreImpl(): Boolean {
    val latestPages = pages.list ?: listOf()
    val nextPageInfo =
      if (latestPages.isEmpty()) initialPageInfo
      else latestPages.lastOrNull()?.info?.createNextPageInfo()
    if (nextPageInfo == null) return false

    val nextPage = runCatchingUser {
      performRequestAndProcess(nextPageInfo) { pageInfo, results ->
        Page(pageInfo, results ?: return@performRequestAndProcess null)
      } ?: return false
    }.getOrElse {
      doEmitPages(State(latestPages, it))
      return false
    }

    doEmitPages(State(latestPages + listOf(nextPage), pages.error))
    return true
  }

  private suspend fun loadAllImpl() {
    shouldTryToLoadAll = true
    do {
      val loadedMoreData = loadMoreImpl()
    }
    while (loadedMoreData)
  }

  private fun doEmitPages(st: State<Page<PI, V>>) {
    pages = st
    mutableStateFlow.value = State(st.list?.flatMap { it.list }?.distinctBy(extractKey), st.error)
  }

  /**
   * Performs the actual request and processes the response.
   *
   * From the response, the next link, updated etag, and results are pulled.
   * If the response was a 304 Not Modified, the page should not be updated
   * and the previous list of results should be used. In this case, the list
   * of results is null.
   *
   * @return `null` when the page was not loaded.
   */
  protected abstract suspend fun performRequestAndProcess(
    pageInfo: PI,
    f: (pageInfo: PI?, results: List<V>?) -> Page<PI, V>?
  ): Page<PI, V>?

  /**
   * Launch now in this coroutine scope and acquire lock.
   */
  private suspend fun withLock(f: suspend () -> Unit) =
    operationLock.withLock {
      try {
        _isBusyFlow.value = true
        f()
      }
      finally {
        _isBusyFlow.value = false
      }
    }
}

@get:ApiStatus.Internal
val <V : Any> ListLoader<V>.changesFlow: Flow<List<ComputedListChange<V>>>
  get() = stateFlow.mapState { it.list ?: emptyList() }.changesFlow()
