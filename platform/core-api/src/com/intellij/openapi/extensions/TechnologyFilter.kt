// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions

import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus

/**
 * Decides if the extensions of a module apply to a project, by the technologies that the project has enabled.
 *
 * The product that has technologies implements this application service. Without the service, every extension applies to every project.
 * The code that asks for the extensions of a project uses [TechnologyAwareExtensionPointName], not this service.
 */
@ApiStatus.Internal
interface TechnologyFilter {
  /**
   * `false` when every extension applies to [project].
   * The callers then take their unfiltered path, for example the cached extension list of a point.
   */
  fun isRestricted(project: Project): Boolean

  /** Whether the extensions of [module] apply to [project]. [module] is a plugin or a content module. */
  fun isApplicable(module: PluginDescriptor, project: Project): Boolean

  companion object {
    /**
     * Tells that the result of [isApplicable] changed for a project.
     * The listeners run on the thread that publishes the change, which can be a background thread.
     */
    @Topic.AppLevel
    @JvmField
    val TOPIC: Topic<TechnologyFilterListener> = Topic(TechnologyFilterListener::class.java, Topic.BroadcastDirection.NONE, true)

    /** The service of the product, or a filter that applies every extension when the product has no service. */
    @JvmStatic
    fun getInstance(): TechnologyFilter = serviceOrNull<TechnologyFilter>() ?: NoRestriction
  }
}

private object NoRestriction : TechnologyFilter {
  override fun isRestricted(project: Project): Boolean = false

  override fun isApplicable(module: PluginDescriptor, project: Project): Boolean = true
}

/** A listener of [TechnologyFilter.TOPIC]. */
@ApiStatus.Internal
fun interface TechnologyFilterListener {
  /** The extensions that apply to [project] can be different now. */
  fun applicabilityChanged(project: Project)
}
