// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.observation

import org.jetbrains.annotations.Nls

/**
 * Allows tracking subsystem activities and getting a "dumb mode" with respect to tracked computations.
 *
 * ## Concept
 * This "dumb mode" is obtained by syntactic markup of the code which runs configuration with [trackActivity].
 * Once a block is marked with [trackActivity], the platform starts tracking all asynchronous computations spawned within the block,
 * and considers the completion moment of all these computations as the end of configuration activity.
 * This approach closely follows the structured concurrency paradigm.
 *
 * ## Usage
 * The intended usage is creating a Kotlin `Object` extending this interface,
 * and then using this singleton in [trackActivity].
 *
 * ## Examples
 * ```kotlin
 * object MyActivityKey : ActivityKey {
 *   override val presentableName: String
 *     get() = MyBundle.getMessage("my.awesome.name")
 * }
 *
 * suspend fun configureProjectOnOpen(project: Project) {
 *   project.trackActivity(MyActivityKey) {
 *     doLongAsynchronousConfigurationProcess(project)
 *   }
 * }
 * ```
 *
 * You can also use it in Java. As long as your configuration code uses IntelliJ Platform framework for asynchronous computations,
 * the configuration process will be tracked. This process of tracking here is governed by context propagation.
 * ```java
 * void configureProjectOnOpen(Project project) {
 *   TrackingUtil.trackActivity(project, MyActivityKey.INSTANCE, () -> {
 *     doLongAsynchronousConfigurationProcess(project)
 *   });
 * }
 * ```
 *
 * ## Completeness of coverage
 * For the completeness of coverage, we expect from a subsystem that all the places where the configuration starts (the *roots*)
 * are marked with [trackActivity]. In this regard, it is important to consider the message-passing model of IntelliJ,
 * since message passing induces interruptions in the provided structured concurrency models
 * (both in kotlin coroutines and in the context propagation).
 *
 * The default roots for the configuration process are:
 * - [com.intellij.openapi.startup.ProjectActivity]. This is the code that runs on project open and most likely starts configuration,
 *   so there is a high change that you need to mark it with [trackActivity].
 * - [com.intellij.util.messages.MessageBus]. This is the core of IntelliJ message passing,
 *   and a way of defining a reactive dependency on another subsystem's actions.
 *   The context propagation intentionally does not work across message delivery,
 *   so you should mark the configuration that starts in the listener methods with [trackActivity].
 * - [kotlinx.coroutines.flow.SharedFlow]. This is a way to implement message passing in Kotlin coroutines fashion,
 *   and it naturally escapes the default structured concurrency scope,
 *   so any configuration that starts in the flow should also use [trackActivity].
 */
interface ActivityKey {
  /**
   * The user-visible name of the activity.
   */
  val presentableName: @Nls String
}
