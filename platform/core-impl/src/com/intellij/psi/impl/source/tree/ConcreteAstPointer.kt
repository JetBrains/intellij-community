// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree

import com.intellij.psi.impl.source.tree.mvcc.VersionedPsiReference
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Contract
import java.util.function.Supplier

/**
 * A reference to [FileElement]. Can be either versioned or non-versioned (with the semantics of versioned PSI),
 * depending on the properties of [com.intellij.psi.FileViewProvider] for which this pointer was created.
 *
 * While mutation operations are copy-on-write, the produced elements can retain memory about past values of [FileElement] in order to support versioning.
 *
 * In the majority of cases, the underlying references are either weak or soft, so the dereferencing function is not pure.
 */
@ApiStatus.Internal
sealed interface ConcreteAstPointer {
    fun dereference(): FileElement?

    fun updateWith(supplier: Supplier<out FileElement?>?): ConcreteAstPointer

    companion object {
        @Contract("_, _ -> new")
        @JvmStatic
        fun createPointer(isVersioned: Boolean, initialValue: Supplier<out FileElement?>?): ConcreteAstPointer {
            return if (isVersioned) VersionedAstPointer(initialValue) else NonVersionedAstPointer(initialValue)
        }
    }

  class VersionedAstPointer : ConcreteAstPointer {
      val ref: VersionedPsiReference<Supplier<out FileElement?>>

      constructor(initialValue: Supplier<out FileElement?>?) {
          this.ref = VersionedPsiReference()
          if (initialValue != null) {
              ref.set(initialValue)
          }
      }

      private constructor(ref: VersionedPsiReference<Supplier<out FileElement?>>) {
          this.ref = ref
      }

      override fun dereference(): FileElement? {
          val supplier = ref.get()
          return supplier?.get()
      }

      override fun updateWith(supplier: Supplier<out FileElement?>?): ConcreteAstPointer {
          val forkedRef = ref.fork()
          forkedRef.set(supplier)
          return VersionedAstPointer(forkedRef)
      }

      override fun toString(): String {
          return ref.toString()
      }
  }

  class NonVersionedAstPointer(val ref: Supplier<out FileElement?>?) : ConcreteAstPointer {
      override fun dereference(): FileElement? {
          return ref?.get()
      }

      override fun updateWith(supplier: Supplier<out FileElement?>?): ConcreteAstPointer {
          return NonVersionedAstPointer(supplier)
      }

      override fun toString(): String {
          return if (ref == null) "no ref" else "reference installed: " + ref.get()
      }
  }
}