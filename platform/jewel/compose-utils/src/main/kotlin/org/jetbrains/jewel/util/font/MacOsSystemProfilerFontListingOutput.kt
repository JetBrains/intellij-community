package org.jetbrains.jewel.util.font

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
internal data class MacOsSystemProfilerFontListingOutput(
    @SerialName("SPFontsDataType") val fontData: List<FontData>
) {

    @Serializable
    internal data class FontData(
        @Serializable(with = AppleYesNoBooleanSerializer::class) @SerialName("enabled") val enabled: Boolean,
        @SerialName("_name") val fontFileName: String,
        @SerialName("path") val path: String,
        @SerialName("type") val type: FontType,
        @SerialName("typefaces") val typefaces: List<Typeface>,
        @Serializable(with = AppleYesNoBooleanSerializer::class) @SerialName("valid") val valid: Boolean
    ) {

        @Serializable
        internal data class Typeface(
//            @Serializable(with = AppleYesNoBooleanSerializer::class) @SerialName("copy_protected") val copyProtected: Boolean,
//            @SerialName("copyright") val copyright: String? = null,
//            @SerialName("description") val description: String? = null,
//            @SerialName("designer") val designer: String? = null,
//            @Serializable(with = AppleYesNoBooleanSerializer::class) @SerialName("duplicate") val duplicate: Boolean,
//            @Serializable(with = AppleYesNoBooleanSerializer::class) @SerialName("embeddable") val embeddable: Boolean,
            @Serializable(with = AppleYesNoBooleanSerializer::class) @SerialName("enabled") val enabled: Boolean,
            @SerialName("family") val fontFamilyName: String,
            @SerialName("fullname") val fullName: String,
            @SerialName("_name") val name: String,
//            @Serializable(with = AppleYesNoBooleanSerializer::class) @SerialName("outline") val outline: Boolean,
            @SerialName("style") val style: String,
//            @SerialName("trademark") val trademark: String? = null,
//            @SerialName("unique") val unique: String,
            @Serializable(with = AppleYesNoBooleanSerializer::class) @SerialName("valid") val valid: Boolean,
//            @SerialName("vendor") val vendor: String? = null,
//            @SerialName("version") val version: String? = null
        )
    }

    @Serializable
    enum class FontType {

        @SerialName("postscript") POSTSCRIPT,
        @SerialName("truetype") TRUETYPE,
        @SerialName("opentype") OPENTYPE,
        @SerialName("bitmap") BITMAP
    }

    object AppleYesNoBooleanSerializer : KSerializer<Boolean> {

        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("AppleYesNoBoolean", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): Boolean = decoder.decodeString().lowercase() == "yes"

        override fun serialize(encoder: Encoder, value: Boolean) {
            encoder.encodeString(if (value) "yes" else "no")
        }
    }
}
