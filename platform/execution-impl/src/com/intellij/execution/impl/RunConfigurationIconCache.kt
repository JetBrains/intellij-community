// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl

/**
 * An object to control the run configuration icon cache. Normally, the icon of the currently selected run configuration is updated
 * periodically, while all the others may be cached for a long time.
 *
 * Use this interface in those rare cases when you need to control that (e.g. when inactive configurations' validity may depend on some
 * external event).
 *
 * It is not advised to flush the cache often, since it may affect the IDE performance.
 *
 * The main attribute that may require to update the cache is the run configuration validity: it gets cached together with the icon, and it
 * may be required to update it sometimes.
 */
interface RunConfigurationIconCache {

  /**
   * Removes an icon for a particular configuration from the cache. It will be recalculated on next query.
   *
   * @param id a value taken from [com.intellij.execution.RunnerAndConfigurationSettings.getUniqueID].
   */
  fun remove(id: String)

  /**
   * Removes all the icons from the cache.
   *
   * Use only in case of global changes that may affect icons of a large amount of the configurations.
   */
  fun clear()
}
