// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.impl

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import com.intellij.diagnostic.LoadingState
import com.intellij.openapi.application.impl.PlatformCoroutineSupport
import com.intellij.openapi.diagnostic.logger
import com.intellij.serviceContainer.NonInjectable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.TimeUnit

@VisibleForTesting
internal open class AssertingPlatformCoroutineSupport @NonInjectable protected constructor(
  private val warnAboutUsingLegacyEdt: Boolean,
) : PlatformCoroutineSupport() {
  @Suppress("unused")
  constructor() : this(warnAboutUsingLegacyEdt = System.getProperty("idea.warn.legacy.edt", "false").toBoolean())

  // early initialization is ok - we use caffeine for the icon loader
  private val uniqueLogWarn: LoadingCache<String, Boolean> by lazy {
    Caffeine.newBuilder()
      .maximumSize(1_000)
      .executor(Dispatchers.Default.asExecutor())
      .expireAfterAccess(8.toLong(), TimeUnit.HOURS)
      .build {
        logger<PlatformCoroutineSupport>().warn("Using of legacy EDT dispatcher before project opening ($it)")
        true
      }
  }

  override fun warnAboutUsingLegacyEdt() {
    if (isLegacyEdtWarningEnabled()) {
      checkUsingLegacyEdt()?.let { logWarning(it) }
    }
  }

  @VisibleForTesting
  protected open fun isLegacyEdtWarningEnabled(): Boolean = warnAboutUsingLegacyEdt && !LoadingState.PROJECT_OPENED.isOccurred

  protected open fun logWarning(className: String) {
    uniqueLogWarn.get(className)
  }
}

private fun checkUsingLegacyEdt(): String? {
  val className = StackWalker.getInstance().walk { stream ->
    stream
      .skip(3)
      .dropWhile {
        it.className == "com.intellij.openapi.application.CoroutinesKt" ||
        it.className == "com.intellij.openapi.application.impl.PlatformCoroutineSupport"
      }
      .findFirst()
      .orElse(null)
      ?.className
  } ?: return null

  val outerClassName = getOuterClassName(className)
  if (!knownOffenders.contains(outerClassName)) {
    return className
  }
  return null
}

private fun getOuterClassName(className: String): String {
  val dollarIndex = className.indexOf('$')
  return if (dollarIndex == -1) className else className.take(dollarIndex)
}

private val knownOffenders = setOf(
  "com.intellij.configurationStore.SaveAndSyncHandlerImpl",
  "com.intellij.util.SingleEdtTaskScheduler",
  "com.intellij.idea.IdeStarter",
  "com.intellij.ui.components.MacScrollBarUI",
  "com.intellij.ide.IdleTracker",
  "com.intellij.openapi.actionSystem.impl.ActionToolbarImpl",
  "com.intellij.util.ui.ShowingScopeKt",
  "com.intellij.openapi.wm.impl.headertoolbar.MainToolbar",
  "com.intellij.ui.components.MacScrollBarAnimationBehavior",
  "com.intellij.ui.components.TwoWayAnimator",
)