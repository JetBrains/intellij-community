// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation

import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.AsyncSupplier
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus.Experimental
import java.util.function.Supplier

@Experimental
sealed interface LinkResolveResult {

  companion object {

    /**
     * @return a result, which makes the browser load the documentation for the [target]
     */
    @RequiresReadLock(generateAssertion = false)
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
    fun asyncResult(supplier: AsyncSupplier<Async?>): LinkResolveResult {
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
