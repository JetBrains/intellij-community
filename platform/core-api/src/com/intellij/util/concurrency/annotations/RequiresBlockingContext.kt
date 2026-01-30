// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency.annotations

import org.jetbrains.annotations.ApiStatus

/**
 * Functions annotated with `RequiresBlockingContext` are not designed to be called in suspend context
 * (where [kotlinx.coroutines.currentCoroutineContext] is available).
 *
 * A function should be annotated if there exists an analog of that function which is tailored for the suspending world.
 * For example:
 * ```
 * @RequiresBlockingContext
 * fun writeActionBlocking(action: () -> Unit) {
 *   ...
 * }
 *
 * suspend fun writeAction(action: () -> Unit) {
 *   ...
 * }
 *
 * suspend fun usage() {
 *   writeActionBlocking { // highlighted because the function is annotated
 *     ...
 *   }
 *   writeAction { // a proper function to call in suspend context
 *     ...
 *   }
 * }
 * ```
 *
 * The annotation shall not be propagated to outer functions by default.
 * If there is no analog of the function for the suspending world,
 * then don't annotate it.
 * For example:
 * ```
 * fun addSdk(sdk: Sdk) {
 *   writeActionBlocking { // fine because the context is not suspending
 *     ...
 *   }
 * }
 * ```
 * If `addSdk` is also annotated with `@RequiresBlockingContext`,
 * then a warning would be produced when this function is called from a suspending context,
 * despite there is nothing else to call:
 * ```
 * suspend fun usage() {
 *   addSdk(sdk) // a warning here would be misleading
 * }
 * ```
 *
 * ## Specifying a replacement
 *
 * Use the [replaceWith] parameter to specify the suspend function that should be used instead:
 * ```
 * @RequiresBlockingContext(ReplaceWith("awaitFileOpenedByLspServer(project, file)",
 *                                       "com.intellij.platform.lsp.testFramework.awaitFileOpenedByLspServer"))
 * fun waitUntilFileOpenedByLspServer(project: Project, file: VirtualFile) {
 *   ...
 * }
 * ```
 *
 * This enables IDE inspections to provide quick-fixes that replace the blocking call
 * with the suspend alternative, and generates documentation hints via `@see`.
 *
 * Note: the inspection is currently disabled due to generating too many false positives.
 *
 * @param replaceWith specifies the code fragment that should be used as a replacement
 *                    for the blocking call, along with any necessary imports.
 *                    See [ReplaceWith] for the format.
 * @see ReplaceWith
 */
@MustBeDocumented
@Retention(AnnotationRetention.SOURCE)
@Target(
  AnnotationTarget.FUNCTION,
  AnnotationTarget.PROPERTY_GETTER,
  AnnotationTarget.PROPERTY_SETTER,
)
@ApiStatus.Experimental
annotation class RequiresBlockingContext(
  val replaceWith: ReplaceWith = ReplaceWith(""),
)

/**
 * Specifies a code fragment that can be used to replace a blocking function call
 * with its suspend equivalent.
 *
 * This is modeled after [kotlin.ReplaceWith] and follows the same semantics.
 *
 * @property expression the replacement expression. Must be a valid Kotlin expression.
 *                      The replacement expression is interpreted in the context of the symbol being used
 *                      and can reference members of the enclosing classes, etc.
 *                      An empty string means no replacement is specified.
 * @property imports the fully qualified names that should be imported to make the replacement
 *                   expression resolve correctly. These are not inserted automatically and are
 *                   used by inspection tooling to determine what imports may be needed.
 * @see kotlin.ReplaceWith
 */
@MustBeDocumented
@Retention(AnnotationRetention.SOURCE)
@Target()
annotation class ReplaceWith(
  val expression: String,
  vararg val imports: String,
)
