// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.documentation

import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import java.util.function.Supplier

sealed interface LinkResolveResult {

  companion object {

    /**
     * @return a result, which makes the browser load the documentation for the [target]
     */
    @RequiresReadLock(generateAssertion = false)
    @RequiresBackgroundThread
    @JvmStatic
    fun resolvedTarget(target: DocumentationTarget): LinkResolveResult {
      ApplicationManager.getApplication().assertReadAccessAllowed()
      return ResolvedTarget(target)
    }

    /**
     * The [supplier]:
     * - will be invoked in the background;
     * - is free to run a read action itself if needed;
     * - must create its result in a read action.
     */
    @RequiresReadLock(generateAssertion = false)
    @RequiresBackgroundThread
    fun asyncResult(supplier: suspend () -> Async?): LinkResolveResult {
      ApplicationManager.getApplication().assertReadAccessAllowed()
      return AsyncLinkResolveResult(supplier)
    }

    /**
     * Same as another overload, but suitable for using from Java.
     * The [supplier] will be invoked under progress indicator.
     */
    @JvmStatic
    fun asyncResult(supplier: Supplier<Async?>): LinkResolveResult {
      return asyncResult(supplier.asAsyncSupplier())
    }
  }

  sealed interface Async {

    companion object {

      /**
       * @return a result, which makes the browser load the documentation for the [target]
       */
      @RequiresReadLock(generateAssertion = false)
      @RequiresBackgroundThread
      @JvmStatic
      fun resolvedTarget(target: DocumentationTarget): Async {
        // async resolve result supplier is invoked outside a read action,
        // so the result should only be created inside a read action
        ApplicationManager.getApplication().assertReadAccessAllowed()
        return AsyncResolvedTarget(target.createPointer())
      }
    }
  }
}
