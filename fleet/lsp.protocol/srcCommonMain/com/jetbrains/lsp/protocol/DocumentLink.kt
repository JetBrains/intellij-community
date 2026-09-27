package com.jetbrains.lsp.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement

@Serializable
data class DocumentLinkParams(
    /**
     * The document to provide document links for.
     */
    val textDocument: TextDocumentIdentifier,

    override val workDoneToken: ProgressToken? = null,
    override val partialResultToken: ProgressToken? = null,
) : WorkDoneProgressParams, PartialResultParams

/**
 * A document link is a range in a text document that links to an internal or
 * external resource, like another text document or a web site.
 */
@Serializable
data class DocumentLink(
    /**
     * The range this link applies to.
     */
    val range: Range,

    /**
     * The uri this link points to. If missing a resolve request is sent later.
     * It is kept as a plain string: servers put web URLs here, which need not pass [URI] validation.
     */
    val target: String? = null,

    /**
     * The tooltip text when you hover over this link.
     *
     * If a tooltip is provided, it will be displayed in a string that includes
     * instructions on how to trigger the link, such as `{0} (ctrl + click)`.
     * The specific instructions vary depending on OS, user settings, and
     * localization.
     *
     * @since 3.15.0
     */
    val tooltip: String? = null,

    /**
     * A data entry field that is preserved on a document link between a
     * DocumentLinkRequest and a DocumentLinkResolveRequest.
     */
    val data: JsonElement? = null,
)

object DocumentLinks {
    val DocumentLinkRequestType: RequestType<DocumentLinkParams, List<DocumentLink>?, Unit> =
        RequestType(
            "textDocument/documentLink",
            DocumentLinkParams.serializer(), ListSerializer(DocumentLink.serializer()).nullable,
            Unit.serializer())

    val ResolveDocumentLink: RequestType<DocumentLink, DocumentLink, Unit> =
        RequestType(
            "documentLink/resolve",
            DocumentLink.serializer(), DocumentLink.serializer(),
            Unit.serializer())
}
