package com.jetbrains.lsp.protocol

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.listSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class SignatureHelpOptions(
  /**
   * The characters that trigger signature help
   * automatically.
   */
  val triggerCharacters: List<String>,
  /**
   * List of characters that re-trigger signature help.
   *
   * These trigger characters are only active when signature help is already
   * showing. All trigger characters are also counted as re-trigger
   * characters.
   *
   * @since 3.15.0
   */
  val retriggerCharacters: List<String>,
  override val workDoneProgress: Boolean?,
) : WorkDoneProgressOptions

@Serializable
data class SignatureHelpParams(
  /**
   * The signature help context. This is only available if the client
   * specifies to send this using the client capability
   * `textDocument.signatureHelp.contextSupport === true`
   *
   * @since 3.15.0
   */
  val context: SignatureHelpContext?,
  override val textDocument: TextDocumentIdentifier,
  override val position: Position,
  override val workDoneToken: ProgressToken?,
) : TextDocumentPositionParams, WorkDoneProgressParams

/**
 * How a signature help was triggered.
 *
 * @since 3.15.0
 */
@Serializable(with = SignatureHelpTriggerKind.Serializer::class)
enum class SignatureHelpTriggerKind(val value: Int) {
  /**
   * Signature help was invoked manually by the user or by a command.
   */
  Invoked(1),

  /**
   * Signature help was triggered by a trigger character.
   */
  TriggerCharacter(2),

  /**
   * Signature help was triggered by the cursor moving or by the document
   * content changing.
   */
  ContentChange(3),

  ;

  class Serializer : EnumAsIntSerializer<SignatureHelpTriggerKind>(
    serialName = SignatureHelpTriggerKind::class.simpleName!!,
    serialize = SignatureHelpTriggerKind::value,
    deserialize = { entries[it - 1] },
  )
}

/**
 * Additional information about the context in which a signature help request
 * was triggered.
 *
 * @since 3.15.0
 */
@Serializable
data class SignatureHelpContext(
  /**
   * Action that caused signature help to be triggered.
   */
  val triggerKind: SignatureHelpTriggerKind,

  /**
   * Character that caused signature help to be triggered.
   *
   * This is undefined when triggerKind !==
   * SignatureHelpTriggerKind.TriggerCharacter
   */
  val triggerCharacter: String?,

  /**
   * `true` if signature help was already showing when it was triggered.
   *
   * Retriggers occur when the signature help is already active and can be
   * caused by actions such as typing a trigger character, a cursor move, or
   * document content changes.
   */
  val isRetrigger: Boolean,

  /**
   * The currently active `SignatureHelp`.
   *
   * The `activeSignatureHelp` has its `SignatureHelp.activeSignature` field
   * updated based on the user navigating through available signatures.
   */
  val activeSignatureHelp: SignatureHelp?,
)

/**
 * Signature help represents the signature of something
 * callable. There can be multiple signature but only one
 * active and only one active parameter.
 */
@Serializable
data class SignatureHelp(
  /**
   * One or more signatures. If no signatures are available the signature help
   * request should return `null`.
   */
  val signatures: List<SignatureInformation>,

  /**
   * The active signature. If omitted or the value lies outside the
   * range of `signatures` the value defaults to zero or is ignore if
   * the `SignatureHelp` as no signatures.
   *
   * Whenever possible implementors should make an active decision about
   * the active signature and shouldn't rely on a default value.
   *
   * In future version of the protocol this property might become
   * mandatory to better express this.
   */
  val activeSignature: Int?,

  /**
   * The active parameter of the active signature. If omitted or the value
   * lies outside the range of `signatures[activeSignature].parameters`
   * defaults to 0 if the active signature has parameters. If
   * the active signature has no parameters it is ignored.
   * In future version of the protocol this property might become
   * mandatory to better express the active parameter if the
   * active signature does have any.
   */
  val activeParameter: Int?,
) {
  init {
    require(activeSignature == null || activeSignature >= 0) {
      "activeSignature must be non-negative but was $activeSignature"
    }
    require(activeParameter == null || activeParameter >= 0) {
      "activeParameter must be non-negative but was $activeParameter"
    }
  }
}

/**
 * Represents the signature of something callable. A signature
 * can have a label, like a function-name, a doc-comment, and
 * a set of parameters.
 */
@Serializable
data class SignatureInformation(
  /**
   * The label of this signature. Will be shown in
   * the UI.
   */
  val label: String,

  /**
   * The human-readable doc-comment of this signature. Will be shown
   * in the UI but can be omitted.
   */
  val documentation: StringOrMarkupContent?,

  /**
   * The parameters of this signature.
   */
  val parameters: List<ParameterInformation>?,

  /**
   * The index of the active parameter.
   *
   * If provided, this is used in place of `SignatureHelp.activeParameter`.
   *
   * @since 3.16.0
   */
  val activeParameter: Int?,
) {
  init {
    require(activeParameter == null || activeParameter >= 0) {
      "activeParameter must be non-negative but was $activeParameter"
    }
  }
}

/**
 * Represents a parameter of a callable-signature. A parameter can
 * have a label and a doc-comment.
 */
@Serializable
data class ParameterInformation(

  /**
   * The label of this parameter information.
   *
   * Either a string or an inclusive start and exclusive end offsets within
   * its containing signature label. (see SignatureInformation.label). The
   * offsets are based on a UTF-16 string representation as `Position` and
   * `Range` does.
   *
   * *Note*: a label of type string should be a substring of its containing
   * signature label. Its intended use case is to highlight the parameter
   * label part in the `SignatureInformation.label`.
   */
  val label: Label,

  /**
   * The human-readable doc-comment of this parameter. Will be shown
   * in the UI but can be omitted.
   */
  val documentation: StringOrMarkupContent?,
) {

  @Serializable(with = Label.LabelSerializer::class)
  sealed interface Label {
    @Serializable
    @JvmInline
    value class StringLabel(val value: String) : Label

    @Serializable(with = RangeLabel.Serializer::class)
    data class RangeLabel(val start: Int, val end: Int) : Label {
      init {
        require(start >= 0) {
          "start must be non-negative but was $start"
        }
        require(end >= 0) {
          "end must be non-negative but was $end"
        }
        require(start <= end) {
          "start must be less than or equal to end but was start=$start, end=$end"
        }
      }

      object Serializer : KSerializer<RangeLabel> {
        private val listSerializer = ListSerializer(Int.serializer())

        override val descriptor: SerialDescriptor = listSerialDescriptor<Int>()

        override fun serialize(encoder: Encoder, value: RangeLabel) {
          encoder.encodeSerializableValue(listSerializer, listOf(value.start, value.end))
        }

        override fun deserialize(decoder: Decoder): RangeLabel {
          val list = decoder.decodeSerializableValue(listSerializer)
          require(list.size == 2) {
            "Expected list of size 2 for RangeLabel, got ${list.size}"
          }
          return RangeLabel(list[0], list[1])
        }
      }
    }

    /**
     * We serialize [RangeLabel] as an array, and thus we cannot use default polymorphic serializer as it writes the `kind` field
     * which is not expected possible for the array case. So, we have a custom serializer.
     */
    object LabelSerializer : JsonContentPolymorphicSerializer<Label>(Label::class) {
      override fun selectDeserializer(element: JsonElement): DeserializationStrategy<Label> {
        return when (element) {
          is JsonPrimitive -> StringLabel.serializer()
          is JsonArray -> RangeLabel.serializer()
          else -> throw SerializationException("Unknown Label variant: $element")
        }
      }
    }
  }
}

val SignatureHelpRequest: RequestType<SignatureHelpParams, SignatureHelp?, Unit> =
  RequestType("textDocument/signatureHelp", SignatureHelpParams.serializer(), SignatureHelp.serializer().nullable, Unit.serializer())
