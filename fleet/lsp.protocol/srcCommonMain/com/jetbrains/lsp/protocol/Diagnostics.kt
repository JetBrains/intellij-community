@file:Suppress("PublicApiImplicitType")

package com.jetbrains.lsp.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement

/**
 * Represents a diagnostic, such as a compiler error or warning. Diagnostic objects are only valid in the scope of a resource.
 */
@Serializable
data class Diagnostic(
    /**
     * The range at which the message applies.
     */
    val range: Range,

    /**
     * The diagnostic's severity. To avoid interpretation mismatches when a
     * server is used with different clients it is highly recommended that
     * servers always provide a severity value. If omitted, it’s recommended
     * for the client to interpret it as an Error severity.
     */
    val severity: DiagnosticSeverity? = null,

    /**
     * The diagnostic's code, which might appear in the user interface.
     */
    val code: StringOrInt? = null, // integer or string equivalent

    /**
     * An optional property to describe the error code.
     *
     * @since 3.16.0
     */
    val codeDescription: CodeDescription? = null,

    /**
     * A human-readable string describing the source of this
     * diagnostic, e.g. 'typescript' or 'super lint'.
     */
    val source: String? = null,

    /**
     * The diagnostic's message.
     */
    val message: String,

    /**
     * Additional metadata about the diagnostic.
     *
     * @since 3.15.0
     */
    val tags: List<DiagnosticTag>? = null,

    /**
     * An array of related diagnostic information, e.g. when symbol-names within
     * a scope collide all definitions can be marked via this property.
     */
    val relatedInformation: List<DiagnosticRelatedInformation>? = null,

    /**
     * A data entry field that is preserved between a
     * `textDocument/publishDiagnostics` notification and
     * `textDocument/codeAction` request.
     *
     * @since 3.16.0
     */
    val data: JsonElement? = null,
)

class DiagnosticSeveritySerializer : EnumAsIntSerializer<DiagnosticSeverity>(
    serialName = "DiagnosticSeverity",
    serialize = DiagnosticSeverity::value,
    deserialize = { DiagnosticSeverity.entries[it - 1] },
)

@Serializable(DiagnosticSeveritySerializer::class)
enum class DiagnosticSeverity(val value: Int) {
    Error(1),
    Warning(2),
    Information(3),
    Hint(4)
}

class DiagnosticTagSerializer : EnumAsIntSerializer<DiagnosticTag>(
    serialName = "DiagnosticTag",
    serialize = DiagnosticTag::value,
    deserialize = { DiagnosticTag.entries[it - 1] },
)

/**
 * The diagnostic tags.
 *
 * @since 3.15.0
 */
@Serializable(DiagnosticTagSerializer::class)
enum class DiagnosticTag(val value: Int) {
    /**
     * Unused or unnecessary code.
     *
     * Clients are allowed to render diagnostics with this tag faded out
     * instead of having an error squiggle.
     */
    Unnecessary(1),

    /**
     * Deprecated or obsolete code.
     *
     * Clients are allowed to rendered diagnostics with this tag strike through.
     */
    Deprecated(2)
}

/**
 * Represents a related message and source code location for a diagnostic.
 * This should be used to point to code locations that cause or are related to
 * a diagnostics, e.g when duplicating a symbol in a scope.
 */
@Serializable
data class DiagnosticRelatedInformation(
    /**
     * The location of this related diagnostic information.
     */
    val location: Location,

    /**
     * The message of this related diagnostic information.
     */
    val message: String,
)

/**
 * Structure to capture a description for an error code.
 *
 * @since 3.16.0
 */
@Serializable
data class CodeDescription(
    /**
     * An URI to open with more information about the diagnostic error.
     */
    val href: URI,
)

@Serializable
data class PublishDiagnosticsClientCapabilities(
    /**
     * Whether the client accepts diagnostics with related information.
     */
    val relatedInformation: Boolean? = null,

    /**
     * Client supports the tag property to provide meta data about a diagnostic.
     * Clients supporting tags have to handle unknown tags gracefully.
     *
     * @since 3.15.0
     */
    val tagSupport: TagSupport? = null,

    /**
     * Whether the client interprets the version property of the
     * `textDocument/publishDiagnostics` notification's parameter.
     *
     * @since 3.15.0
     */
    val versionSupport: Boolean? = null,

    /**
     * Client supports a codeDescription property.
     *
     * @since 3.16.0
     */
    val codeDescriptionSupport: Boolean? = null,

    /**
     * Whether code actions support the `data` property which is
     * preserved between a `textDocument/publishDiagnostics` and
     * `textDocument/codeAction` request.
     *
     * @since 3.16.0
     */
    val dataSupport: Boolean? = null,
) {
    @Serializable
    data class TagSupport(
        /**
         * The tags supported by the client.
         */
        val valueSet: List<DiagnosticTag>,
    )
}

@Serializable
data class PublishDiagnosticsParams(
    /**
     * The URI for which diagnostic information is reported.
     */
    val uri: DocumentUri,

    /**
     * The version number of the document the diagnostics are published for.
     *
     * Optional, since 3.15.0
     */
    val version: Int? = null,

    /**
     * An array of diagnostic information items.
     */
    val diagnostics: List<Diagnostic>,
)

/**
 * Client capabilities specific to diagnostic pull requests.
 *
 * @since 3.17.0
 */
@Serializable
data class DiagnosticClientCapabilities(
    /**
     * Whether implementation supports dynamic registration. If this is set to
     * `true` the client supports the new
     * `(TextDocumentRegistrationOptions & StaticRegistrationOptions)`
     * return value for the corresponding server capability as well.
     */
    val dynamicRegistration: Boolean? = null,

    /**
     * Whether the client supports related documents for document diagnostic
     * pulls.
     */
    val relatedDocumentSupport: Boolean? = null,
)

@Serializable
data class DiagnosticOptions(
    /**
     * An optional identifier under which the diagnostics are
     * managed by the client.
     */
    val identifier: String?,
    /**
     * Whether the language has inter file dependencies meaning that
     * editing code in one file can result in a different diagnostic
     * set in another file. Inter file dependencies are common for
     * most programming languages and typically uncommon for linters.
     */
    val interFileDependencies: Boolean?,
    /**
     * The server provides support for workspace diagnostics as well.
     */
    val workspaceDiagnostics: Boolean?,
    override val workDoneProgress: Boolean?,
) : WorkDoneProgressOptions

/**
 * Parameters of the document diagnostic request.
 *
 * @since 3.17.0
 */
@Serializable
data class DocumentDiagnosticParams(
    /**
     * The text document.
     */
    val textDocument: TextDocumentIdentifier,

    /**
     * The additional identifier provided during registration.
     */
    val identifier: String? = null,

    /**
     * The result id of a previous response if provided.
     */
    val previousResultId: String? = null,

    override val workDoneToken: ProgressToken?,

    override val partialResultToken: ProgressToken?,

    ) : WorkDoneProgressParams, PartialResultParams

@Serializable
enum class DocumentDiagnosticReportKind {
    @SerialName("full")
    Full,

    @SerialName("unchanged")
    Unchanged
}

@Serializable
data class DocumentDiagnosticReport(
    val kind: DocumentDiagnosticReportKind,
    val resultId: String?,
    val items: List<Diagnostic>?,
    val relatedDocuments: Map<DocumentUri, DocumentDiagnosticReport>?,
) {
    companion object {
        val EMPTY_FULL = DocumentDiagnosticReport(
            DocumentDiagnosticReportKind.Full,
            resultId = null,
            items = emptyList(),
            relatedDocuments = null,
        )
    }
}

@Serializable
data class DiagnosticServerCancellationData(
    val retriggerRequest: Boolean,
)

@Serializable
data class DiagnosticWorkspaceClientCapabilities(
    /**
     * Whether the client implementation supports a refresh request sent from
     * the server to the client.
     *
     * Note that this event is global and will force the client to refresh all
     * pulled diagnostics currently shown. It should be used with absolute care
     * and is useful for situations where a server, for example, detects a project-
     * wide change that requires such a calculation.
     */
    val refreshSupport: Boolean? = null,
)

object Diagnostics {
    /**
     * Diagnostics notifications are sent from the server to the client to signal results of validation runs.
     *
     * Diagnostics are “owned” by the server so it is the server’s responsibility to clear them if necessary. The following rule is used for VS Code servers that generate diagnostics:
     * if a language is single file only (for example HTML) then diagnostics are cleared by the server when the file is closed. Please note that open / close events don’t necessarily reflect what the user sees in the user interface.
     * These events are ownership events. So with the current version of the specification it is possible that problems are not cleared although the file is not visible in the user interface since the client has not closed the file yet.
     * if a language has a project system (for example C#) diagnostics are not cleared when a file closes. When a project is opened all diagnostics for all files are recomputed (or read from a cache).
     * When a file changes it is the server’s responsibility to re-compute diagnostics and push them to the client. If the computed set is empty it has to push the empty array to clear former diagnostics.
     * Newly pushed diagnostics always replace previously pushed diagnostics.
     * There is no merging that happens on the client side.
     */
    val PublishDiagnosticsNotificationType: NotificationType<PublishDiagnosticsParams> =
        NotificationType("textDocument/publishDiagnostics", PublishDiagnosticsParams.serializer())

    val DocumentDiagnosticRequestType: RequestType<DocumentDiagnosticParams, DocumentDiagnosticReport, DiagnosticServerCancellationData?> =
        RequestType(
            "textDocument/diagnostic", DocumentDiagnosticParams.serializer(), DocumentDiagnosticReport.serializer(),
            DiagnosticServerCancellationData.serializer().nullable
        )

    val Refresh: RequestType<Unit, Unit, Unit> =
        RequestType("textDocument/diagnostics/refresh", Unit.serializer(), Unit.serializer(), Unit.serializer())
}