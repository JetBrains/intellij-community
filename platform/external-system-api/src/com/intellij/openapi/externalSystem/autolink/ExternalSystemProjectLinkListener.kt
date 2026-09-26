// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autolink

import org.jetbrains.annotations.ApiStatus

/**
 * Instance of this interface should be notified by external system plugin code when related project is (un)linked
 * @see ExternalSystemUnlinkedProjectAware for details
 */
@ApiStatus.Experimental
interface ExternalSystemProjectLinkListener {

  fun onProjectLinked(externalProjectPath: String) {}

  fun onProjectUnlinked(externalProjectPath: String) {}
}