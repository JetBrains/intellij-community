@file:Suppress("DEPRECATION")
package com.intellij.platform.lsp.impl

import com.intellij.platform.lsp.api.LspServer

@Deprecated("Use LspClientImpl", ReplaceWith("LspClientImpl"))
@Suppress("unused")
typealias LspServerImpl = LspClientImpl

/**
 * Needed to remove [LspServer] imports (and noisy deprecation warnings) from LspClient code
 */
internal typealias LspClientRenameCompat = LspServer
