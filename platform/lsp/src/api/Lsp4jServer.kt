// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api

/**
 * An alias for the [org.eclipse.lsp4j.services.LanguageServer] interface from the `lsp4j` library.
 * It helps to distinguish between `lsp4j` library-specific classes and IntelliJ LSP API classes.
 *
 * For example, the [LspServer] class represents an IntelliJ's model of a started LSP server,
 * whereas [Lsp4jServer] is a lower-level library-specific interface.
 *
 * Plugins need to override [Lsp4jServer] only if they need to send custom undocumented notifications or requests to the LSP server.
 *
 * @see LspServerDescriptor.lsp4jServerClass
 */
typealias Lsp4jServer = org.eclipse.lsp4j.services.LanguageServer
