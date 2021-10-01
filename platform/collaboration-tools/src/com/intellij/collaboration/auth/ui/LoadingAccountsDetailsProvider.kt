// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.auth.ui

import com.intellij.collaboration.async.CompletableFutureUtil
import com.intellij.collaboration.auth.Account
import com.intellij.collaboration.auth.AccountDetails
import com.intellij.collaboration.ui.SingleValueModel
import com.intellij.collaboration.util.ProgressIndicatorsProvider
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.IconUtil
import com.intellij.util.ui.EmptyIcon
import org.jetbrains.annotations.Nls
import java.awt.Image
import java.util.concurrent.CompletableFuture
import java.util.function.BiConsumer
import javax.swing.Icon

abstract class LoadingAccountsDetailsProvider<in A : Account, D : AccountDetails>(
  private val progressIndicatorsProvider: ProgressIndicatorsProvider
) : AccountsDetailsProvider<A, D> {

  open val defaultIcon: Icon = IconUtil.resizeSquared(EmptyIcon.ICON_16, 40)
  private val detailsMap = mutableMapOf<A, CompletableFuture<DetailsLoadingResult<D>>>()
  override val loadingStateModel = SingleValueModel(false)

  private var runningProcesses = 0

  override fun getDetails(account: A): D? = getOrLoad(account).getNow(null)?.details

  private fun getOrLoad(account: A): CompletableFuture<DetailsLoadingResult<D>> {
    return detailsMap.getOrPut(account) {
      val indicator = progressIndicatorsProvider.acquireIndicator()
      runningProcesses++
      loadingStateModel.value = true
      scheduleLoad(account, indicator).whenComplete(BiConsumer { _, _ ->
        invokeAndWaitIfNeeded(ModalityState.any()) {
          progressIndicatorsProvider.releaseIndicator(indicator)
          runningProcesses--
          if (runningProcesses == 0) loadingStateModel.value = false
        }
      }).exceptionally {
        val error = CompletableFutureUtil.extractError(it)
        DetailsLoadingResult(null, null, error.localizedMessage, false)
      }
    }
  }

  abstract fun scheduleLoad(account: A, indicator: ProgressIndicator): CompletableFuture<DetailsLoadingResult<D>>

  override fun getAvatarImage(account: A): Image? = getOrLoad(account).getNow(null)?.avatarImage

  override fun getErrorText(account: A): String? = getOrLoad(account).getNow(null)?.error

  override fun checkErrorRequiresReLogin(account: A) = getOrLoad(account).getNow(null)?.needReLogin ?: false

  override fun reset(account: A) {
    detailsMap.remove(account)
  }

  override fun resetAll() = detailsMap.clear()

  data class DetailsLoadingResult<D : AccountDetails>(val details: D?,
                                                      val avatarImage: Image?,
                                                      @Nls val error: String?,
                                                      val needReLogin: Boolean)
}