// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl.scopes

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.codeInsight.multiverse.ModuleContext
import com.intellij.codeInsight.multiverse.ProjectModelContextBridge
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkContext
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl
import com.intellij.openapi.roots.JdkOrderEntry
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleOrderEntry
import com.intellij.openapi.roots.ModuleSourceOrderEntry
import com.intellij.openapi.roots.OrderEntry
import com.intellij.openapi.roots.impl.DummyRootDescriptor
import com.intellij.openapi.roots.impl.LibraryRootDescriptor
import com.intellij.openapi.roots.impl.ModuleRootDescriptor
import com.intellij.openapi.roots.impl.RootDescriptor
import com.intellij.openapi.roots.impl.SdkRootDescriptor
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryContext
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryBridge
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import com.intellij.workspaceModel.ide.legacyBridge.findLibraryBridge

/**
 * A scope entity is what a scope reaches a root *through*: the [Module], [Library] or [Sdk] that owns it, rather
 * than the [OrderEntry] instance that brought it in.
 *
 * A module or a library bridge is stored as its id ([ModuleId], [LibraryId]), not as the bridge object. A caller
 * can keep a scope across a root change, and the change can dispose a bridge and create a new one with the same
 * id. The id still names the new bridge. The bridge object is disposed, and it is not the owner that the file
 * index reports. A module or a library that is not a bridge, and every [Sdk], is stored as the object itself.
 *
 * It is the key of the per-scope map in [EntrySetRootContainer], and deliberately not an [OrderEntry]: an order
 * entry belongs to one module's root model, so keying by it would both pin root models and give two modules
 * depending on the same library two distinct keys. The owner is also exactly what the file index already reports
 * per root ([RootDescriptor]), which is what lets a scope answer `contains` without storing roots at all.
 *
 * There is no wrapper type around the three: the map stores the owner object itself. A wrapper would be an object
 * header and a reference per entry of every cached scope - more than the slot the entry-set container saves on a
 * project model with few roots per entity - and it would have to be allocated on every `contains` call just to
 * probe the map. The cost of that choice is this file: [scopeEntityOf], [findContextOf] and [entityHasTestRoots]
 * dispatch where a sealed hierarchy would have had a virtual call.
 */
internal typealias ScopeEntity = Any

/**
 * The owner behind an order entry, or `null` when there is nothing to own the roots - an unresolved library, a
 * missing SDK or a dependency on a module that is gone. Such an entry contributes no roots either, so dropping it
 * loses nothing.
 */
internal fun scopeEntityOf(orderEntry: OrderEntry): ScopeEntity? = when (orderEntry) {
  is LibraryOrderEntry -> orderEntry.library?.let(::keyOf)
  is ModuleOrderEntry -> orderEntry.module?.let(::keyOf)
  is ModuleSourceOrderEntry -> keyOf(orderEntry.rootModel.module)
  is JdkOrderEntry -> orderEntry.jdk
  else -> {
    log.error("Unexpected order entry: $orderEntry")
    null
  }
}

/**
 * The owner the file index reports for a root, or `null` for a [DummyRootDescriptor] - a file set registered
 * without the data that says which module, library or SDK it belongs to. Those are resolved the slow way, see
 * `EntrySetRootContainer.findUnattributedRoot`.
 */
internal fun scopeEntityOf(rootDescriptor: RootDescriptor): ScopeEntity? = when (rootDescriptor) {
  is ModuleRootDescriptor -> keyOf(rootDescriptor.module)
  is LibraryRootDescriptor -> keyOf(rootDescriptor.library)
  is SdkRootDescriptor -> rootDescriptor.sdk
  is DummyRootDescriptor -> null
}

/**
 * The owner a code insight context names, or `null` for a context that is not a project model one.
 *
 * Note that this is the *claim* a caller makes, not what the file index reports: a scope has to check the claimed
 * owner against the real one rather than trust it, or `contains(file, context)` would accept any context whose
 * entity the scope happens to reach.
 */
internal fun scopeEntityOf(context: CodeInsightContext): ScopeEntity? = when (context) {
  is ModuleContext -> context.getModule()?.let(::keyOf)
  is LibraryContext -> context.getLibrary()?.let(::keyOf)
  is SdkContext -> context.getSdk()
  else -> null
}

/**
 * Whether two entities denote the same module, library or SDK. Plain equality, except for the SDK instances a unit
 * test recreates behind the scope cache's back.
 *
 * @see isSameSdkInTests
 */
internal fun isSameEntity(entity: ScopeEntity, other: ScopeEntity): Boolean =
  entity == other || entity is Sdk && other is Sdk && isSameSdkInTests(entity, other)

private fun keyOf(module: Module): ScopeEntity = (module as? ModuleBridge)?.moduleEntityId ?: module

private fun keyOf(library: Library): ScopeEntity = (library as? LibraryBridge)?.libraryId ?: library

/**
 * The object behind [entity] for a context lookup. For an id, this is the owner that the file index reports for
 * [rootDescriptor], when there is one: that owner is current, and taking it costs no lookup by id.
 */
internal fun ownerForContext(entity: ScopeEntity, rootDescriptor: RootDescriptor): ScopeEntity {
  if (entity !is ModuleId && entity !is LibraryId) return entity
  return when (rootDescriptor) {
    is ModuleRootDescriptor -> rootDescriptor.module
    is LibraryRootDescriptor -> rootDescriptor.library
    else -> entity
  }
}

/**
 * The [Module], [Library] or [Sdk] that a scope entity names now, or `null` when it is gone: the id of a removed
 * bridge, or a disposed module or library. A gone entity has no roots and no context.
 */
internal fun resolveEntity(entity: ScopeEntity, project: Project): Any? = when (entity) {
  is ModuleId -> ModuleManager.getInstance(project).findModuleByName(entity.name)
  is LibraryId -> entity.findLibraryBridge(WorkspaceModel.getInstance(project).currentSnapshot, project)
  is Module -> entity.takeUnless { it.isDisposed }
  is LibraryEx -> entity.takeUnless { it.isDisposed }
  else -> entity
}

/** The code insight context an entity stands for, or `null` when the project model has no context for it. */
internal fun findContextOf(entity: ScopeEntity, project: Project): CodeInsightContext? {
  val bridge = ProjectModelContextBridge.getInstance(project)
  return when (val resolved = resolveEntity(entity, project)) {
    is Module -> bridge.getContext(resolved)
    is Library -> bridge.getContext(resolved)
    is Sdk -> bridge.getContext(resolved)
    else -> null
  }
}

/**
 * Whether an entity's roots are split into production and test ones. Only a module's are: the `TESTS` option
 * filters roots *within* a module source entry, while a library or an SDK entry is taken whole or not at all.
 */
internal fun entityHasTestRoots(entity: ScopeEntity): Boolean = entity is ModuleId || entity is Module

/**
 * Whether two SDK objects that are not the same instance nevertheless denote the same SDK.
 *
 * Unit tests recreate their mock SDK on every run without firing the events that would invalidate the scope cache,
 * so the instance the file index hands out and the one the order entry resolved can differ. The descriptor-based
 * container worked around that in `correspondTo`; here the workaround is a linear probe over a container's entries,
 * and it stays switched off outside tests so that the hot path never pays for it.
 */
// todo IJPL-339 get rid of this
internal fun isSameSdkInTests(sdk1: Sdk, sdk2: Sdk): Boolean {
  if (!ApplicationManager.getApplication().isUnitTestMode) return false

  return sdk1.homePath == sdk2.homePath &&
         sdk1.name == sdk2.name &&
         //sdk1.versionString == sdk2.versionString && todo IJPL-339 the version can be different
         sdk1.sdkType == sdk2.sdkType &&
         sdk1.unwrap().javaClass == sdk2.unwrap().javaClass
}

private fun Sdk.unwrap(): Sdk = if (this is ProjectJdkImpl) this.delegate else this

private val log = logger<Module>()
