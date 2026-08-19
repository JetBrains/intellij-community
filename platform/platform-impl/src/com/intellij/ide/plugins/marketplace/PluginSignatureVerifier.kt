// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.registry.RegistryManager
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * Checks that a plugin archive is signed by a trusted certificate.
 *
 * Implemented by `intellij.platform.ide.pluginSignatureVerifier`, which is the only module using
 * the `marketplace-zip-signer` library. Call [verifyIfRequired] instead of using the service directly.
 */
@ApiStatus.Internal
interface PluginSignatureVerifier {
  fun verify(descriptor: IdeaPluginDescriptor, pluginFile: Path, showAcceptDialog: Boolean): Boolean

  companion object {
    private val LOG = logger<PluginSignatureVerifier>()

    @JvmStatic
    fun verifyIfRequired(descriptor: IdeaPluginDescriptor, pluginFile: Path, isMarketplace: Boolean, showAcceptDialog: Boolean): Boolean {
      val key = if (isMarketplace) "marketplace.certificate.signature.check" else "custom-repository.certificate.signature.check"
      if (!RegistryManager.getInstance().`is`(key)) {
        return true
      }

      val verifier = serviceOrNull<PluginSignatureVerifier>()
      if (verifier == null) {
        LOG.error("Signature of ${pluginFile.fileName} is not verified: intellij.platform.ide.pluginSignatureVerifier is not loaded")
        return true
      }
      return verifier.verify(descriptor, pluginFile, showAcceptDialog)
    }
  }
}
