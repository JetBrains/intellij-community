package com.jetbrains.lsp.implementation

import com.jetbrains.lsp.protocol.ServerCapabilities

data class LspServerCapabilities(
    val lspHandlers: LspHandlers,
    val serverCapabilities: ServerCapabilities,
)

interface LspServerCapabilitiesBuilder {

    fun capability(
        update: ServerCapabilities.() -> ServerCapabilities,
        register: LspHandlersBuilder.() -> Unit,
    )

    /**
     * Registers handlers for methods that are not gated by a capability, such as `initialized` or `$/cancelRequest`.
     */
    fun orphanHandlers(register: LspHandlersBuilder.() -> Unit)
}

fun lspServerCapabilities(
    initial: ServerCapabilities = ServerCapabilities(),
    build: LspServerCapabilitiesBuilder.() -> Unit,
): LspServerCapabilities {
    val builder = LspServerCapabilitiesBuilderImpl(initial)
    builder.build()
    return builder.result()
}

private class LspServerCapabilitiesBuilderImpl(
    private var capabilities: ServerCapabilities,
) : LspServerCapabilitiesBuilder {
    private val registrations = mutableListOf<LspHandlersBuilder.() -> Unit>()
    private val capabilityUpdates = mutableListOf<ServerCapabilities.() -> ServerCapabilities>()

    override fun capability(
        update: ServerCapabilities.() -> ServerCapabilities,
        register: LspHandlersBuilder.() -> Unit,
    ) {
        capabilityUpdates += update
        registrations += register
    }

    override fun orphanHandlers(register: LspHandlersBuilder.() -> Unit) {
        registrations += register
    }

    fun result(): LspServerCapabilities = LspServerCapabilities(
        lspHandlers = lspHandlers {
            for (register in registrations) {
                register()
            }
        },
        serverCapabilities = capabilityUpdates.fold(capabilities) { acc, update -> acc.update() },
    )
}
