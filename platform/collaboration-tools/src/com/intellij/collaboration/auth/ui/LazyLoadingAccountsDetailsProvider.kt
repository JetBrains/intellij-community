// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.auth.ui

import com.intellij.collaboration.auth.Account
import com.intellij.collaboration.auth.AccountDetails
import com.intellij.collaboration.auth.AccountManager
import com.intellij.collaboration.ui.icon.AsyncImageIconsProvider
import com.intellij.collaboration.ui.icon.CachingIconsProvider
import com.intellij.collaboration.ui.items
import com.intellij.util.IconUtil
import com.intellij.util.ui.ImageUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.annotations.Nls
import java.awt.Image
import java.util.concurrent.ConcurrentHashMap
import javax.swing.Icon
import javax.swing.ListModel
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener
import kotlin.properties.Delegates.observable

abstract class LazyLoadingAccountsDetailsProvider<A : Account, D : AccountDetails>(
  private val scope: CoroutineScope,
  private val defaultAvatarIcon: Icon
) : LoadingAccountsDetailsProvider<A, D> {

  final override val loadingState = MutableStateFlow(false)
  private var loadingCount by observable(0) { _, _, newValue ->
    loadingState.value = newValue > 0
  }

  final override val loadingCompletionFlow = MutableSharedFlow<A>()

  private val requestsMap = ConcurrentHashMap<A, Deferred<Result<D>>>()
  private val resultsMap = ConcurrentHashMap<A, Result<D>>()

  private val delegateIconProvider = CachingIconsProvider(createIconProvider())

  private fun requestDetails(account: A) = requestsMap.getOrPut(account) {
    scope.async {
      loadingCount++
      try {
        val result = loadDetails(account)
        resultsMap[account] = result
        loadingCompletionFlow.emit(account)
        result
      }
      finally {
        loadingCount--
      }
    }
  }

  fun clearDetails(account: A) {
    requestsMap.remove(account)?.cancel()
    resultsMap.remove(account)
    //TODO: granular invalidation
    delegateIconProvider.invalidateAll()
    loadingCompletionFlow.tryEmit(account)
  }

  fun clearOutdatedDetails(currentList: Set<A>) {
    for (account in requestsMap.keys - currentList) {
      clearDetails(account)
    }
  }

  protected abstract suspend fun loadDetails(account: A): Result<D>

  final override fun getDetails(account: A): D? {
    requestDetails(account)
    return (resultsMap[account] as? Result.Success<D>)?.details
  }

  final override fun getErrorText(account: A): String? {
    requestDetails(account)
    return (resultsMap[account] as? Result.Error<*>)?.error
  }

  final override fun checkErrorRequiresReLogin(account: A): Boolean {
    requestDetails(account)
    return (resultsMap[account] as? Result.Error<*>)?.needReLogin ?: false
  }

  final override fun getIcon(key: A?, iconSize: Int): Icon = delegateIconProvider.getIcon(key, iconSize)

  protected abstract suspend fun loadAvatar(account: A, url: String): Image?

  sealed class Result<out D : AccountDetails> {
    class Success<out D : AccountDetails>(val details: D) : Result<D>()
    class Error<out D : AccountDetails>(val error: @Nls String?, val needReLogin: Boolean) : Result<D>()
  }

  private fun createIconProvider() = AsyncImageIconsProvider(scope, object : AsyncImageIconsProvider.AsyncImageLoader<A> {
    override suspend fun load(key: A): Image? {
      val url = (requestDetails(key).await() as? Result.Success<D>)?.details?.avatarUrl ?: return null
      return loadAvatar(key, url)
    }

    override fun createBaseIcon(key: A?, iconSize: Int): Icon = IconUtil.resizeSquared(defaultAvatarIcon, iconSize)
    override suspend fun postProcess(image: Image): Image = ImageUtil.createCircleImage(ImageUtil.toBufferedImage(image))
  })
}

fun <A : Account> LazyLoadingAccountsDetailsProvider<A, *>.cancelOnRemoval(listModel: ListModel<A>) {
  listModel.addListDataListener(object : ListDataListener {

    override fun contentsChanged(e: ListDataEvent) {
      if (e.index0 < 0 || e.index1 < 0) return

      for (i in e.index0..e.index1) {
        val account = listModel.getElementAt(i)
        clearDetails(account)
      }
    }

    override fun intervalRemoved(e: ListDataEvent) {
      val accounts = listModel.items.toSet()
      clearOutdatedDetails(accounts)
    }

    override fun intervalAdded(e: ListDataEvent) = Unit
  })
}

fun <A : Account> LazyLoadingAccountsDetailsProvider<A, *>.cancelOnRemoval(scope: CoroutineScope, accountManager: AccountManager<A, *>) {
  scope.launch {
    accountManager.accountsState.collectLatest {
      clearOutdatedDetails(it)
      //TODO: subscribe only when requested
      coroutineScope {
        for (account in it) {
          launch {
            accountManager.getCredentialsFlow(account, false).collect {
              clearDetails(account)
            }
          }
        }
      }
    }
  }
}