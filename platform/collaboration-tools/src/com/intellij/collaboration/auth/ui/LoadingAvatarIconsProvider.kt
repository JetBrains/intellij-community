// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.auth.ui

import com.intellij.collaboration.auth.Account
import com.intellij.collaboration.ui.codereview.avatar.CachingCircleImageIconsProvider
import com.intellij.collaboration.ui.codereview.avatar.IconsProvider
import kotlinx.coroutines.CoroutineScope
import java.awt.Image
import javax.swing.Icon

class LoadingAvatarIconsProvider<A : Account>(private val scope: CoroutineScope,
                                                       private val detailsLoader: AccountsDetailsLoader<A, *>,
                                              private val defaultAvatarIcon: Icon,
                                              private val avatarUrlSupplier: (A) -> String?)
  : IconsProvider<A> {

  private val cachingDelegate = object : CachingCircleImageIconsProvider<Pair<A, String>>(scope, defaultAvatarIcon) {
    override suspend fun loadImage(key: Pair<A, String>): Image? = detailsLoader.loadAvatar(key.first, key.second)
  }


  override fun getIcon(key: A?, iconSize: Int): Icon {
    val account = key ?: return cachingDelegate.getIcon(null, iconSize)
    val uri = avatarUrlSupplier(key) ?: return cachingDelegate.getIcon(null, iconSize)
    return cachingDelegate.getIcon(account to uri, iconSize)
  }
}