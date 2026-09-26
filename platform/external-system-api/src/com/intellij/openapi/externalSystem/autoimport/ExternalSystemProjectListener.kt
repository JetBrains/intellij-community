// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autoimport

import org.jetbrains.annotations.ApiStatus.NonExtendable
import java.util.EventListener

/**
 * Build tool listener for providing build tool lifecycle for auto-sync support.
 * Needed to highlight bounds of project refresh on the side of an external system.
 */
@NonExtendable
interface ExternalSystemProjectListener: EventListener {

  fun onProjectReloadStart() {}

  fun onProjectReloadFinish(status: ExternalSystemRefreshStatus) {}

  fun onSettingsFilesListChange() {}
}