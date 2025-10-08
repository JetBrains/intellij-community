package com.jetbrains.lsp.protocol

import kotlinx.serialization.Serializable

@Serializable
data class ClientFileOperationsCapabilities(
    /**
     * Whether the client supports dynamic registration for file requests/notifications.
     */
    val dynamicRegistration: Boolean? = null,

    /**
     * The client has support for sending didCreateFiles notifications.
     */
    val didCreate: Boolean? = null,

    /**
     * The client has support for sending willCreateFiles requests.
     */
    val willCreate: Boolean? = null,

    /**
     * The client has support for sending didRenameFiles notifications.
     */
    val didRename: Boolean? = null,

    /**
     * The client has support for sending willRenameFiles requests.
     */
    val willRename: Boolean? = null,

    /**
     * The client has support for sending didDeleteFiles notifications.
     */
    val didDelete: Boolean? = null,

    /**
     * The client has support for sending willDeleteFiles requests.
     */
    val willDelete: Boolean? = null
)