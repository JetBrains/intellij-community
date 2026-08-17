// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions.impl

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.extensions.ExtensionPoint
import org.jetbrains.annotations.ApiStatus
import kotlin.reflect.KClass

private val logger = Logger.getInstance("#com.intellij.openapi.extensions.impl.ExtensionPointCleanup")

// IDEA-226246 unregisterExtension(Class<*>) doesn't work with inner classes
@ApiStatus.Internal
fun <T : Any> ExtensionPoint<T>.unregisterExtensions(vararg toRemoveClasses: KClass<out T>) {
  unregisterExtensionsByClassName(*toRemoveClasses.map { it.java.name }.toTypedArray())
}

@ApiStatus.Internal
fun <T : Any> ExtensionPoint<T>.unregisterExtensionsByClassName(vararg toRemoveClassNames: String) {
  val toRemoveSet = toRemoveClassNames.toSet()
  val allEps = unregisterExtensionsMatching { className, _ -> className in toRemoveSet }
  val notFound = toRemoveSet - allEps.map { it.className }.toSet()
  notFound.forEach { logger.info("Can't unregister '${this}' extension: $it - Not Found") }
}

@ApiStatus.Internal
fun <T : Any> ExtensionPoint<T>.unregisterExtensionsById(vararg toRemoveIds: String) {
  val toRemoveSet = toRemoveIds.toSet()
  val allEps = unregisterExtensionsMatching { _, adapter -> adapter.orderId in toRemoveSet }
  val notFound = toRemoveSet - allEps.map { it.adapter.orderId }.toSet()
  notFound.forEach { logger.info("Can't unregister '${this}' extension: $it - Not Found") }
}

@ApiStatus.Internal
fun <T : Any> ExtensionPoint<T>.unregisterEverythingExcept(vararg toKeepClasses: KClass<out T>) {
  unregisterEverythingExceptByClassName(*toKeepClasses.map { it.java.name }.toTypedArray())
}

@ApiStatus.Internal
fun <T : Any> ExtensionPoint<T>.unregisterEverythingExceptByClassName(vararg toKeepClassNames: String) {
  val toKeepSet = toKeepClassNames.toSet()
  val allEps = unregisterExtensionsMatching { className, _ -> className !in toKeepSet }
  val notFound = toKeepSet - allEps.map { it.className }.toSet()
  notFound.forEach { logger.info("Can't keep '${this}' extension: $it - Not Found") }
}

@ApiStatus.Internal
fun <T : Any> ExtensionPoint<T>.unregisterEverythingExceptById(vararg toKeepIds: String) {
  val toKeepSet = toKeepIds.toSet()
  val allEps = unregisterExtensionsMatching { _, adapter -> adapter.orderId !in toKeepSet }
  val notFound = toKeepSet - allEps.map { it.adapter.orderId }.toSet()
  notFound.forEach { logger.info("Can't keep '${this}' extension: $it - Not Found") }
}

@ApiStatus.Internal
fun <T : Any> ExtensionPoint<T>.unregisterEverything(): List<ExtensionUnregistrationReport> =
  unregisterExtensionsMatching { _, _ -> true }

@ApiStatus.Internal
fun <T : Any> ExtensionPoint<T>.unregisterExtensionsMatching(
  filter: (className: String, adapter: ExtensionComponentAdapter) -> Boolean,
): List<ExtensionUnregistrationReport> {
  val allEps = mutableListOf<ExtensionUnregistrationReport>()
  unregisterExtensions({ className, adapter ->
                         val wasUnregistered = filter(className, adapter)
                         allEps += ExtensionUnregistrationReport(className, adapter, wasUnregistered)
                         !wasUnregistered
                       }, /*stopAfterFirstMatch =*/ false)

  allEps.forEach { it.log() }
  return allEps
}

@ApiStatus.Internal
data class ExtensionUnregistrationReport(val className: String, val adapter: ExtensionComponentAdapter, val wasUnregistered: Boolean) {
  fun log() {
    if (wasUnregistered) {
      logger.debug { "Unregistered extension ${asString()}" }
    }
    else {
      logger.trace { "Kept extension ${asString()}" }
    }
  }

  private fun asString(): String = "'$className'" + if (adapter.orderId != null) ", id: ${adapter.orderId}" else ""
}
