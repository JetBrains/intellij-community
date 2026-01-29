package org.jetbrains.jewel.foundation.code

import org.jetbrains.jewel.foundation.code.MimeType.Known.AGSL
import org.jetbrains.jewel.foundation.code.MimeType.Known.AIDL
import org.jetbrains.jewel.foundation.code.MimeType.Known.C
import org.jetbrains.jewel.foundation.code.MimeType.Known.CPP
import org.jetbrains.jewel.foundation.code.MimeType.Known.DART
import org.jetbrains.jewel.foundation.code.MimeType.Known.DIFF
import org.jetbrains.jewel.foundation.code.MimeType.Known.GO
import org.jetbrains.jewel.foundation.code.MimeType.Known.GRADLE
import org.jetbrains.jewel.foundation.code.MimeType.Known.GRADLE_KTS
import org.jetbrains.jewel.foundation.code.MimeType.Known.GROOVY
import org.jetbrains.jewel.foundation.code.MimeType.Known.JAVA
import org.jetbrains.jewel.foundation.code.MimeType.Known.JAVASCRIPT
import org.jetbrains.jewel.foundation.code.MimeType.Known.JSON
import org.jetbrains.jewel.foundation.code.MimeType.Known.KOTLIN
import org.jetbrains.jewel.foundation.code.MimeType.Known.MANIFEST
import org.jetbrains.jewel.foundation.code.MimeType.Known.PATCH
import org.jetbrains.jewel.foundation.code.MimeType.Known.PROGUARD
import org.jetbrains.jewel.foundation.code.MimeType.Known.PROPERTIES
import org.jetbrains.jewel.foundation.code.MimeType.Known.PROTO
import org.jetbrains.jewel.foundation.code.MimeType.Known.PYTHON
import org.jetbrains.jewel.foundation.code.MimeType.Known.REGEX
import org.jetbrains.jewel.foundation.code.MimeType.Known.RESOURCE
import org.jetbrains.jewel.foundation.code.MimeType.Known.RUST
import org.jetbrains.jewel.foundation.code.MimeType.Known.SHELL
import org.jetbrains.jewel.foundation.code.MimeType.Known.SQL
import org.jetbrains.jewel.foundation.code.MimeType.Known.SVG
import org.jetbrains.jewel.foundation.code.MimeType.Known.TEXT
import org.jetbrains.jewel.foundation.code.MimeType.Known.TOML
import org.jetbrains.jewel.foundation.code.MimeType.Known.TYPESCRIPT
import org.jetbrains.jewel.foundation.code.MimeType.Known.UNKNOWN
import org.jetbrains.jewel.foundation.code.MimeType.Known.VERSION_CATALOG
import org.jetbrains.jewel.foundation.code.MimeType.Known.XML
import org.jetbrains.jewel.foundation.code.MimeType.Known.YAML

private val ALREADY_NORMALIZED_BUILTIN_TYPES =
    setOf(
        AGSL,
        AIDL,
        C,
        CPP,
        DART,
        DIFF,
        GO,
        GRADLE,
        GRADLE_KTS,
        GROOVY,
        JAVA,
        JAVASCRIPT,
        JSON,
        KOTLIN,
        MANIFEST,
        PATCH,
        PROGUARD,
        PROPERTIES,
        PROTO,
        PYTHON,
        REGEX,
        RESOURCE,
        RUST,
        SHELL,
        SQL,
        SVG,
        TEXT,
        TOML,
        TYPESCRIPT,
        UNKNOWN,
        VERSION_CATALOG,
        XML,
        YAML,
    )

/**
 * Represents the language and dialect of a source snippet, as an RFC 2046 mime type.
 *
 * For example, a Kotlin source file may have the mime type `text/kotlin`. However, if it corresponds to a
 * `build.gradle.kts` file, we'll also attach the mime parameter `role=gradle`, resulting in mime type `text/kotlin;
 * role=gradle`.
 *
 * For XML resource files, we'll attach other attributes; for example `role=manifest` for Android manifest files and
 * `role=resource` for XML resource files. For the latter we may also attach for example `folderType=values`, and for
 * XML files in general, the root tag, such as `text/xml; role=resource; folderType=layout; rootTag=LinearLayout`.
 *
 * This class does not implement *all* aspects of the RFC; in particular, we don't treat attributes as case-insensitive,
 * and we only support value tokens, not value strings -- neither of these are needed for our purposes.
 *
 * This is implemented using a value class, such that behind the scenes we're really just passing a String around. This
 * also means we can't initialize related values such as the corresponding Markdown fenced block names, or IntelliJ
 * language id's. Instead, these are looked up via `when`-tables. When adding a new language, update all lookup methods:
 * * [displayName]
 */
@Deprecated(
    message =
        "The MimeType class is deprecated in favor of using the code block info strings (e.g., \"kt\", \"python\"). " +
            "This class creates an unnecessary layer of abstraction and requires manual maintenance " +
            "to support new languages. Use the new `highlight(code, language)` function " +
            "to handle language resolution automatically."
)
@JvmInline
public value class MimeType(private val mimeType: String) {
    @Deprecated(
        message =
            "The MimeType class is deprecated in favor of using the code block info strings (e.g., \"kt\", " +
                "\"python\"). This class creates an unnecessary layer of abstraction and requires manual maintenance " +
                "to support new languages. Use the new `highlight(code, language)` function " +
                "to handle language resolution automatically."
    )
    public fun displayName(): String =
        when (getBaseMimeType()) {
            KOTLIN.mimeType -> if (isGradle()) "Gradle DSL" else "Kotlin"
            JAVA.mimeType -> "Java"
            XML.mimeType -> {
                when (getRole()) {
                    null -> "XML"
                    VALUE_MANIFEST -> "Manifest"
                    VALUE_RESOURCE -> {
                        val folderType = getAttribute(ATTR_FOLDER_TYPE)
                        folderType?.capitalizeAsciiOnly() ?: "XML"
                    }

                    else -> "XML"
                }
            }

            JSON.mimeType -> "JSON"
            TEXT.mimeType -> "Text"
            REGEX.mimeType -> "Regular Expression"
            GROOVY.mimeType -> if (isGradle()) "Gradle" else "Groovy"
            TOML.mimeType -> if (isVersionCatalog()) "Version Catalog" else "TOML"
            C.mimeType -> "C"
            CPP.mimeType -> "C++"
            SVG.mimeType -> "SVG"
            AIDL.mimeType -> "AIDL"
            SQL.mimeType -> "SQL"
            PROGUARD.mimeType -> "Shrinker Config"
            PROPERTIES.mimeType -> "Properties"
            PROTO.mimeType -> "Protobuf"
            PYTHON.mimeType -> "Python"
            DART.mimeType -> "Dart"
            RUST.mimeType -> "Rust"
            JAVASCRIPT.mimeType -> "JavaScript"
            AGSL.mimeType -> "Android Graphics Shading Language"
            SHELL.mimeType -> "Shell Script"
            YAML.mimeType -> "YAML"
            GO.mimeType -> "Go"
            else -> mimeType
        }

    private fun getBaseMimeType(): String {
        val baseMimeType = base().toString()
        // Built-ins are already normalized, don't do string and sorting work
        if (this in ALREADY_NORMALIZED_BUILTIN_TYPES) return baseMimeType

        return when (baseMimeType) {
            "text/x-java-source",
            "application/x-java",
            "text/x-java" -> JAVA.mimeType

            "application/kotlin-source",
            "text/x-kotlin",
            "text/x-kotlin-source" -> KOTLIN.mimeType

            "application/xml" -> XML.mimeType
            "application/json",
            "application/vnd.api+json",
            "application/hal+json",
            "application/ld+json" -> JSON.mimeType

            "image/svg+xml" -> XML.mimeType
            "text/x-python",
            "application/x-python-script" -> PYTHON.mimeType

            "text/dart",
            "text/x-dart",
            "application/dart",
            "application/x-dart" -> DART.mimeType

            "application/javascript",
            "application/x-javascript",
            "text/ecmascript",
            "application/ecmascript",
            "application/x-ecmascript" -> JAVASCRIPT.mimeType

            "application/typescript",
            "application/x-typescript" -> TYPESCRIPT.mimeType
            "text/x-rust",
            "application/x-rust" -> RUST.mimeType

            "text/x-sksl" -> AGSL.mimeType
            "application/yaml",
            "text/x-yaml",
            "application/x-yaml" -> YAML.mimeType
            "application/x-patch" -> PATCH.mimeType

            else -> baseMimeType
        }
    }

    /**
     * Returns just the language portion of the mime type.
     *
     * For example, for `text/kotlin; role=gradle` this will return `text/kotlin`. For `text/plain; charset=us-ascii`
     * this returns `text/plain`
     */
    @Deprecated(
        message =
            "The MimeType class is deprecated in favor of using the code block info strings (e.g., \"kt\", " +
                "\"python\"). This class creates an unnecessary layer of abstraction and requires manual maintenance " +
                "to support new languages. Use the new `highlight(code, language)` function " +
                "to handle language resolution automatically."
    )
    public fun base(): MimeType = MimeType(mimeType.substringBefore(';').trim())

    internal fun getRole(): String? = getAttribute(ATTR_ROLE)

    private fun getAttribute(name: String): String? {
        val marker = "$name="
        var start = mimeType.indexOf(marker)
        if (start == -1) {
            return null
        }
        start += marker.length
        var end = start
        while (end < mimeType.length && !mimeType[end].isWhitespace()) {
            end++
        }
        return mimeType.substring(start, end).removeSurrounding("\"")
    }

    override fun toString(): String = mimeType

    private companion object {
        /**
         * Attribute used to indicate the role this source file plays; for example, an XML file may be a "manifest" or a
         * "resource".
         */
        const val ATTR_ROLE: String = "role"

        /** For XML resource files, the folder type if any (such as "values" or "layout") */
        const val ATTR_FOLDER_TYPE: String = "folderType"

        /** For XML files, the root tag in the content */
        const val ATTR_ROOT_TAG: String = "rootTag"

        const val VALUE_RESOURCE = "resource"
        const val VALUE_MANIFEST = "manifest"
    }

    @Deprecated(
        message =
            "The MimeType class is deprecated in favor of using the code block info strings (e.g., \"kt\", " +
                "\"python\"). This class creates an unnecessary layer of abstraction and requires manual maintenance " +
                "to support new languages. Use the new `highlight(code, language)` function " +
                "to handle language resolution automatically."
    )
    public object Known {
        // Well known mime types for major languages.

        /**
         * Well known name for Kotlin source snippets. This is the base mime type; consider using [isKotlin] instead to
         * check if a mime type represents Kotlin code such that it also picks up `build.gradle.kts` files (which carry
         * extra attributes in the mime type; see [GRADLE_KTS].)
         */
        @Deprecated(
            message =
                "The MimeType class is deprecated in favor of using the code block info strings (e.g., \"kt\", " +
                    "\"python\"). This class creates an unnecessary layer of abstraction and requires manual " +
                    "maintenance to support new languages. Use the new `highlight(code, language)` function " +
                    "to handle language resolution automatically."
        )
        public val KOTLIN: MimeType = MimeType("text/kotlin")

        /** Well known name for Java source snippets. */
        @Deprecated(
            message =
                "The MimeType class is deprecated in favor of using the code block info strings (e.g., \"kt\", " +
                    "\"python\"). This class creates an unnecessary layer of abstraction and requires manual " +
                    "maintenance to support new languages. Use the new `highlight(code, language)` function " +
                    "to handle language resolution automatically."
        )
        public val JAVA: MimeType = MimeType("text/java")

        /** Well known mime type for text files. */
        @Deprecated(
            message =
                "The MimeType class is deprecated in favor of using the code block info strings (e.g., \"kt\", " +
                    "\"python\"). This class creates an unnecessary layer of abstraction and requires manual " +
                    "maintenance to support new languages. Use the new `highlight(code, language)` function " +
                    "to handle language resolution automatically."
        )
        public val TEXT: MimeType = MimeType("text/plain")

        /**
         * Special marker mimetype for unknown or unspecified mime types. These will generally be treated as [TEXT] for
         * editor purposes. (The standard "unknown" mime type is application/octet-stream (from RFC 2046) but we know
         * this isn't binary data; it's text.)
         *
         * Note that [MimeType] is generally nullable in places where it's optional instead of being set to this value,
         * but this mime type is there for places where we need a specific value to point to.
         */
        @Deprecated(
            message =
                "The MimeType class is deprecated in favor of using the code block info strings (e.g., \"kt\", " +
                    "\"python\"). This class creates an unnecessary layer of abstraction and requires manual " +
                    "maintenance to support new languages. Use the new `highlight(code, language)` function " +
                    "to handle language resolution automatically."
        )
        public val UNKNOWN: MimeType = MimeType("text/unknown")

        /**
         * Well known name for XML source snippets. This is the base mime type; consider using [isXml] instead to check
         * if a mime type represents any XML such that it also picks up manifest files, resource files etc., which all
         * carry extra attributes in the mime type; see for example [MANIFEST] and [RESOURCE].
         */
        @Deprecated(
            message =
                "The MimeType class is deprecated in favor of using the code block info strings (e.g., \"kt\", " +
                    "\"python\"). This class creates an unnecessary layer of abstraction and requires manual " +
                    "maintenance to support new languages. Use the new `highlight(code, language)` function " +
                    "to handle language resolution automatically."
        )
        public val XML: MimeType = MimeType("text/xml")

        @Deprecated(
            message =
                "The MimeType class is deprecated in favor of using the code block info strings (e.g., \"kt\", " +
                    "\"python\"). This class creates an unnecessary layer of abstraction and requires manual " +
                    "maintenance to support new languages. Use the new `highlight(code, language)` function " +
                    "to handle language resolution automatically."
        )
        public val PROPERTIES: MimeType = MimeType("text/properties")

        @Deprecated(
            message =
                "The MimeType class is deprecated in favor of using the code block info strings (e.g., \"kt\", " +
                    "\"python\"). This class creates an unnecessary layer of abstraction and requires manual " +
                    "maintenance to support new languages. Use the new `highlight(code, language)` function " +
                    "to handle language resolution automatically."
        )
        public val TOML: MimeType = MimeType("text/toml")

        @Deprecated(
            message =
                "The MimeType class is deprecated in favor of using the code block info strings (e.g., \"kt\", " +
                    "\"python\"). This class creates an unnecessary layer of abstraction and requires manual " +
                    "maintenance to support new languages. Use the new `highlight(code, language)` function " +
                    "to handle language resolution automatically."
        )
        public val JSON: MimeType = MimeType("text/json")

        @Deprecated(
            message =
                "The MimeType class is deprecated in favor of using the code block info strings (e.g., \"kt\", " +
                    "\"python\"). This class creates an unnecessary layer of abstraction and requires manual " +
                    "maintenance to support new languages. Use the new `highlight(code, language)` function " +
                    "to handle language resolution automatically."
        )
        public val REGEX: MimeType = MimeType("text/x-regex-source")

        @Deprecated(
            message =
                "The MimeType class is deprecated in favor of using the code block info strings (e.g., \"kt\", " +
                    "\"python\"). This class creates an unnecessary layer of abstraction and requires manual " +
                    "maintenance to support new languages. Use the new `highlight(code, language)` function " +
                    "to handle language resolution automatically."
        )
        public val GROOVY: MimeType = MimeType("text/groovy")

        @Deprecated(
            message =
                "The MimeType class is deprecated in favor of using the code block info strings (e.g., \"kt\", " +
                    "\"python\"). This class creates an unnecessary layer of abstraction and requires manual " +
                    "maintenance to support new languages. Use the new `highlight(code, language)` function " +
                    "to handle language resolution automatically."
        )
        public val C: MimeType = MimeType("text/c")

        @Deprecated(
            message =
                "The MimeType class is deprecated in favor of using the code block info strings (e.g., \"kt\", " +
                    "\"python\"). This class creates an unnecessary layer of abstraction and requires manual " +
                    "maintenance to support new languages. Use the new `highlight(code, language)` function " +
                    "to handle language resolution automatically."
        )
        public val CPP: MimeType = MimeType("text/c++")

        @Deprecated(
            message =
                "The MimeType class is deprecated in favor of using the code block info strings (e.g., \"kt\", " +
                    "\"python\"). This class creates an unnecessary layer of abstraction and requires manual " +
                    "maintenance to support new languages. Use the new `highlight(code, language)` function " +
                    "to handle language resolution automatically."
        )
        public val SVG: MimeType = MimeType("image/svg+xml")

        @Deprecated(
            message =
                "The MimeType class is deprecated in favor of using the code block info strings (e.g., \"kt\", " +
                    "\"python\"). This class creates an unnecessary layer of abstraction and requires manual " +
                    "maintenance to support new languages. Use the new `highlight(code, language)` function " +
                    "to handle language resolution automatically."
        )
        public val AIDL: MimeType = MimeType("text/x-aidl-source")

        @Deprecated(
            message =
                "The MimeType class is deprecated in favor of using the code block info strings (e.g., \"kt\", " +
                    "\"python\"). This class creates an unnecessary layer of abstraction and requires manual " +
                    "maintenance to support new languages. Use the new `highlight(code, language)` function " +
                    "to handle language resolution automatically."
        )
        public val PROTO: MimeType = MimeType("text/x-protobuf")

        @Deprecated(
            message =
                "The MimeType class is deprecated in favor of using the code block info strings (e.g., \"kt\", " +
                    "\"python\"). This class creates an unnecessary layer of abstraction and requires manual " +
                    "maintenance to support new languages. Use the new `highlight(code, language)` function " +
                    "to handle language resolution automatically."
        )
        public val SQL: MimeType = MimeType("text/x-sql")

        @Deprecated(
            message =
                "The MimeType class is deprecated in favor of using the code block info strings (e.g., \"kt\", " +
                    "\"python\"). This class creates an unnecessary layer of abstraction and requires manual " +
                    "maintenance to support new languages. Use the new `highlight(code, language)` function " +
                    "to handle language resolution automatically."
        )
        public val PROGUARD: MimeType = MimeType("text/x-proguard")

        @Deprecated(
            message =
                "The MimeType class is deprecated in favor of using the code block info strings (e.g., \"kt\", " +
                    "\"python\"). This class creates an unnecessary layer of abstraction and requires manual " +
                    "maintenance to support new languages. Use the new `highlight(code, language)` function " +
                    "to handle language resolution automatically."
        )
        public val PYTHON: MimeType = MimeType("text/python")

        @Deprecated(
            message =
                "The MimeType class is deprecated in favor of using the code block info strings (e.g., \"kt\", " +
                    "\"python\"). This class creates an unnecessary layer of abstraction and requires manual " +
                    "maintenance to support new languages. Use the new `highlight(code, language)` function " +
                    "to handle language resolution automatically."
        )
        public val JAVASCRIPT: MimeType = MimeType("text/javascript")

        @Deprecated(
            message =
                "The MimeType class is deprecated in favor of using the code block info strings (e.g., \"kt\", " +
                    "\"python\"). This class creates an unnecessary layer of abstraction and requires manual " +
                    "maintenance to support new languages. Use the new `highlight(code, language)` function " +
                    "to handle language resolution automatically."
        )
        public val TYPESCRIPT: MimeType = MimeType("text/typescript")

        @Deprecated(
            message =
                "The MimeType class is deprecated in favor of using the code block info strings (e.g., \"kt\", " +
                    "\"python\"). This class creates an unnecessary layer of abstraction and requires manual " +
                    "maintenance to support new languages. Use the new `highlight(code, language)` function " +
                    "to handle language resolution automatically."
        )
        public val DART: MimeType = MimeType("application/dart")

        @Deprecated(
            message =
                "The MimeType class is deprecated in favor of using the code block info strings (e.g., \"kt\", " +
                    "\"python\"). This class creates an unnecessary layer of abstraction and requires manual " +
                    "maintenance to support new languages. Use the new `highlight(code, language)` function " +
                    "to handle language resolution automatically."
        )
        public val RUST: MimeType = MimeType("text/rust")

        @Deprecated(
            message =
                "The MimeType class is deprecated in favor of using the code block info strings (e.g., \"kt\", " +
                    "\"python\"). This class creates an unnecessary layer of abstraction and requires manual " +
                    "maintenance to support new languages. Use the new `highlight(code, language)` function " +
                    "to handle language resolution automatically."
        )
        public val AGSL: MimeType = MimeType("text/x-agsl")

        @Deprecated(
            message =
                "The MimeType class is deprecated in favor of using the code block info strings (e.g., \"kt\", " +
                    "\"python\"). This class creates an unnecessary layer of abstraction and requires manual " +
                    "maintenance to support new languages. Use the new `highlight(code, language)` function " +
                    "to handle language resolution automatically."
        )
        public val SHELL: MimeType = MimeType("application/x-sh")

        @Deprecated(
            message =
                "The MimeType class is deprecated in favor of using the code block info strings (e.g., \"kt\", " +
                    "\"python\"). This class creates an unnecessary layer of abstraction and requires manual " +
                    "maintenance to support new languages. Use the new `highlight(code, language)` function " +
                    "to handle language resolution automatically."
        )
        public val YAML: MimeType = MimeType("text/yaml")

        @Deprecated(
            message =
                "The MimeType class is deprecated in favor of using the code block info strings (e.g., \"kt\", " +
                    "\"python\"). This class creates an unnecessary layer of abstraction and requires manual " +
                    "maintenance to support new languages. Use the new `highlight(code, language)` function " +
                    "to handle language resolution automatically."
        )
        public val GO: MimeType = MimeType("text/go")

        /** Note that most resource files will also have a folder type, so don't use equality on this mime type */
        @Deprecated(
            message =
                "The MimeType class is deprecated in favor of using the code block info strings (e.g., \"kt\", " +
                    "\"python\"). This class creates an unnecessary layer of abstraction and requires manual " +
                    "maintenance to support new languages. Use the new `highlight(code, language)` function " +
                    "to handle language resolution automatically."
        )
        public val RESOURCE: MimeType = MimeType("$XML; $ATTR_ROLE=resource")

        @Deprecated(
            message =
                "The MimeType class is deprecated in favor of using the code block info strings (e.g., \"kt\", " +
                    "\"python\"). This class creates an unnecessary layer of abstraction and requires manual " +
                    "maintenance to support new languages. Use the new `highlight(code, language)` function " +
                    "to handle language resolution automatically."
        )
        public val MANIFEST: MimeType = MimeType("$XML;$ATTR_ROLE=manifest $ATTR_ROOT_TAG=manifest")

        @Deprecated(
            message =
                "The MimeType class is deprecated in favor of using the code block info strings (e.g., \"kt\", " +
                    "\"python\"). This class creates an unnecessary layer of abstraction and requires manual " +
                    "maintenance to support new languages. Use the new `highlight(code, language)` function " +
                    "to handle language resolution automatically."
        )
        public val GRADLE: MimeType = MimeType("$GROOVY; $ATTR_ROLE=gradle")

        @Deprecated(
            message =
                "The MimeType class is deprecated in favor of using the code block info strings (e.g., \"kt\", " +
                    "\"python\"). This class creates an unnecessary layer of abstraction and requires manual " +
                    "maintenance to support new languages. Use the new `highlight(code, language)` function " +
                    "to handle language resolution automatically."
        )
        public val GRADLE_KTS: MimeType = MimeType("$KOTLIN; $ATTR_ROLE=gradle")

        @Deprecated(
            message =
                "The MimeType class is deprecated in favor of using the code block info strings (e.g., \"kt\", " +
                    "\"python\"). This class creates an unnecessary layer of abstraction and requires manual " +
                    "maintenance to support new languages. Use the new `highlight(code, language)` function " +
                    "to handle language resolution automatically."
        )
        public val VERSION_CATALOG: MimeType = MimeType("$TOML; $ATTR_ROLE=version-catalog")

        @Deprecated(
            message =
                "The MimeType class is deprecated in favor of using the code block info strings (e.g., \"kt\", " +
                    "\"python\"). This class creates an unnecessary layer of abstraction and requires manual " +
                    "maintenance to support new languages. Use the new `highlight(code, language)` function " +
                    "to handle language resolution automatically."
        )
        public val DIFF: MimeType = MimeType("text/x-diff")

        @Deprecated(
            message =
                "The MimeType class is deprecated in favor of using the code block info strings (e.g., \"kt\", " +
                    "\"python\"). This class creates an unnecessary layer of abstraction and requires manual " +
                    "maintenance to support new languages. Use the new `highlight(code, language)` function " +
                    "to handle language resolution automatically."
        )
        public val PATCH: MimeType = MimeType("text/x-patch")

        /** Maps from a Markdown language [name] back to a mime type. */
        @Deprecated(
            message =
                "The MimeType class is deprecated in favor of using the code block info strings (e.g., \"kt\", " +
                    "\"python\"). This class creates an unnecessary layer of abstraction and requires manual " +
                    "maintenance to support new languages. Use the new `highlight(code, language)` function " +
                    "to handle language resolution automatically."
        )
        public fun fromMarkdownLanguageName(name: String): MimeType? =
            when (name) {
                "kotlin",
                "kt",
                "kts" -> KOTLIN

                "java" -> JAVA
                "xml" -> XML
                "json",
                "json5" -> JSON

                "regex",
                "regexp" -> REGEX

                "groovy" -> GROOVY
                "toml" -> TOML
                "c" -> C
                "c++" -> CPP
                "svg" -> SVG
                "aidl" -> AIDL
                "sql" -> SQL
                "properties" -> PROPERTIES
                "protobuf" -> PROTO
                "python2",
                "python3",
                "py",
                "python" -> PYTHON

                "dart" -> DART
                "rust" -> RUST
                "js",
                "javascript" -> JAVASCRIPT

                "typescript" -> TYPESCRIPT
                "sksl" -> AGSL
                "sh",
                "bash",
                "zsh",
                "shell" -> SHELL

                "yaml",
                "yml" -> YAML

                "go",
                "golang" -> GO

                "diff" -> DIFF
                "patch" -> PATCH

                else -> null
            }
    }
}

/** Is the base language for this mime type Kotlin? */
@Deprecated(
    message =
        "The MimeType class is deprecated in favor of using the code block info strings (e.g., \"kt\", \"python\"). " +
            "This class creates an unnecessary layer of abstraction and requires manual maintenance " +
            "to support new languages. Use the new `highlight(code, language)` function " +
            "to handle language resolution automatically."
)
public fun MimeType?.isKotlin(): Boolean = this?.base() == KOTLIN

/** Is the base language for this mime type Java? */
@Deprecated(
    message =
        "The MimeType class is deprecated in favor of using the code block info strings (e.g., \"kt\", \"python\"). " +
            "This class creates an unnecessary layer of abstraction and requires manual maintenance " +
            "to support new languages. Use the new `highlight(code, language)` function " +
            "to handle language resolution automatically."
)
public fun MimeType?.isJava(): Boolean = this?.base() == JAVA

/** Is the base language for this mime type XML? */
@Deprecated(
    message =
        "The MimeType class is deprecated in favor of using the code block info strings (e.g., \"kt\", \"python\"). " +
            "This class creates an unnecessary layer of abstraction and requires manual maintenance " +
            "to support new languages. Use the new `highlight(code, language)` function " +
            "to handle language resolution automatically."
)
public fun MimeType?.isXml(): Boolean = this?.base() == XML

/** Is this a Gradle file (which could be in Groovy, *or*, Kotlin) */
@Deprecated(
    message =
        "The MimeType class is deprecated in favor of using the code block info strings (e.g., \"kt\", \"python\"). " +
            "This class creates an unnecessary layer of abstraction and requires manual maintenance " +
            "to support new languages. Use the new `highlight(code, language)` function " +
            "to handle language resolution automatically."
)
public fun MimeType?.isGradle(): Boolean = this?.getRole() == "gradle"

/** Is this a version catalog file (which could be in TOML, or in Groovy) */
@Deprecated(
    message =
        "The MimeType class is deprecated in favor of using the code block info strings (e.g., \"kt\", \"python\"). " +
            "This class creates an unnecessary layer of abstraction and requires manual maintenance " +
            "to support new languages. Use the new `highlight(code, language)` function " +
            "to handle language resolution automatically."
)
public fun MimeType?.isVersionCatalog(): Boolean = this?.getRole() == "version-catalog"

/** Is this an Android manifest file? */
@Deprecated(
    message =
        "The MimeType class is deprecated in favor of using the code block info strings (e.g., \"kt\", \"python\"). " +
            "This class creates an unnecessary layer of abstraction and requires manual maintenance " +
            "to support new languages. Use the new `highlight(code, language)` function " +
            "to handle language resolution automatically."
)
public fun MimeType?.isManifest(): Boolean = this?.getRole() == "manifest"

/** Is the base language for this mime type SQL? */
@Deprecated(
    message =
        "The MimeType class is deprecated in favor of using the code block info strings (e.g., \"kt\", \"python\"). " +
            "This class creates an unnecessary layer of abstraction and requires manual maintenance " +
            "to support new languages. Use the new `highlight(code, language)` function " +
            "to handle language resolution automatically."
)
public fun MimeType?.isSql(): Boolean = this?.base() == SQL

/** Is the base language for this mime type a regular expression? */
@Deprecated(
    message =
        "The MimeType class is deprecated in favor of using the code block info strings (e.g., \"kt\", \"python\"). " +
            "This class creates an unnecessary layer of abstraction and requires manual maintenance " +
            "to support new languages. Use the new `highlight(code, language)` function " +
            "to handle language resolution automatically."
)
public fun MimeType?.isRegex(): Boolean = this?.base() == REGEX

/** Is the base language for this mime type a protobuf? */
@Deprecated(
    message =
        "The MimeType class is deprecated in favor of using the code block info strings (e.g., \"kt\", \"python\"). " +
            "This class creates an unnecessary layer of abstraction and requires manual maintenance " +
            "to support new languages. Use the new `highlight(code, language)` function " +
            "to handle language resolution automatically."
)
public fun MimeType?.isProto(): Boolean = this?.base() == PROTO

private fun String.capitalizeAsciiOnly(): String {
    if (isEmpty()) return this
    val c = this[0]
    return if (c in 'a'..'z') {
        buildString(length) {
            append(c.uppercaseChar())
            append(this@capitalizeAsciiOnly, 1, this@capitalizeAsciiOnly.length)
        }
    } else {
        this
    }
}
