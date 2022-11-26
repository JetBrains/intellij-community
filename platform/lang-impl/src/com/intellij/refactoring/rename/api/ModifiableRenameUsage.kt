// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename.api

import com.intellij.model.Pointer
import com.intellij.openapi.progress.ProgressManager

/**
 * Implement this interface to consider the usage modifiable.
 * The [RenameUsage]s which don't implement this interface are highlighted as read-only in the Usage View.
 */
interface ModifiableRenameUsage : RenameUsage {

  override fun createPointer(): Pointer<out ModifiableRenameUsage>

  val fileUpdater: FileUpdater?
    get() = null

  val modelUpdater: ModelUpdater?
    get() = null

  /**
   * Stateless piece of implementation, which generates a file patch from usages.
   * Usages are grouped by [fileUpdater], so it is expected to be a singleton object.
   */
  interface FileUpdater {

    /**
     * Prepares update of several [usages] which specify this updater.
     *
     * Implement this method if some usages might affect applying neighbor usages.
     * Example: given `a + b + c` the `plus` method is renamed to `add`.
     * Applying the first patch will yield `a.add(b) + c`;
     * applying the second patch will yield `(a + b).add(c)`;
     * applying both in a sequence will yield `(a.add(b)).add(c)`.
     * Implementing this method allows to return `a.add(b).add(c)`.
     *
     * @param ctx context to check for cancellation
     * @param newName new name of the [RenameTarget] for which the [usages] were found
     * @return operations which are needed to be applied to update the [usages]
     */
    fun prepareFileUpdateBatch(
      usages: Collection<ModifiableRenameUsage>,
      newName: String
    ): Collection<FileOperation> {
      return usages.flatMap { usage ->
        ProgressManager.checkCanceled()
        prepareFileUpdate(usage, newName)
      }
    }

    /**
     * Prepares update of a single [usage].
     *
     * @param newName new name of the [RenameTarget] for which the [usage] was found
     * @return operations which are needed to be applied to update the [usage]
     */
    fun prepareFileUpdate(usage: ModifiableRenameUsage, newName: String): Collection<FileOperation> = emptyList()
  }

  /**
   * Stateless piece of implementation, which prepares updates of usages for which it's doesn't make sense to generate a patch.
   * Usages are grouped by [modelUpdater], so it is expected to be a singleton object.
   */
  interface ModelUpdater {

    fun prepareModelUpdateBatch(usages: Collection<ModifiableRenameUsage>): Collection<ModelUpdate> {
      return usages.mapNotNull { usage ->
        ProgressManager.checkCanceled()
        prepareModelUpdate(usage)
      }
    }

    fun prepareModelUpdate(usage: ModifiableRenameUsage): ModelUpdate? = null
  }

  interface ModelUpdate {

    /**
     * Performs the modification of the model in the write action, e.g.:
     * - updating the FQNs in the run configurations;
     * - changing the package name;
     * - setting the user-defined member declaration name in the tool window.
     *
     * @param newName new name of the [RenameTarget] for which the usages were found,
     * or the old name of the [RenameTarget] when undoing the modification
     */
    fun updateModel(newName: String)
  }
}
