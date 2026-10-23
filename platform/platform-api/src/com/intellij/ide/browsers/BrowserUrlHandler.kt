// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Handles a URL inside the IDE before [BrowserLauncher] hands it to a browser.
 *
 * A handler takes a URL that the IDE itself serves, such as a `jetbrains://` command of its own plugin.
 * [UrlOpener] does not fit this case: the launcher asks it only for an HTTP URL or for a browser the caller names.
 *
 * The launcher asks the handlers after its own guards: the side-effect check, the trust confirmation of an untrusted
 * project, and the URL validation. A handler gets a URL that the launcher would open.
 */
@ApiStatus.Internal
interface BrowserUrlHandler {
  /** Returns `true` when this handler took [url], so no browser opens it. Return `false` to decline. */
  fun handle(url: String, project: Project?): Boolean

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<BrowserUrlHandler> = ExtensionPointName("com.intellij.browserUrlHandler")
  }
}
