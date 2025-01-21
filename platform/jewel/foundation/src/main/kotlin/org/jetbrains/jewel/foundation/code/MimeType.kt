package org.jetbrains.jewel.foundation.code

import org.jetbrains.jewel.foundation.code.MimeType.Known.AGSL
import org.jetbrains.jewel.foundation.code.MimeType.Known.DART
import org.jetbrains.jewel.foundation.code.MimeType.Known.JSON
import org.jetbrains.jewel.foundation.code.MimeType.Known.KOTLIN
import org.jetbrains.jewel.foundation.code.MimeType.Known.PYTHON
import org.jetbrains.jewel.foundation.code.MimeType.Known.RUST
import org.jetbrains.jewel.foundation.code.MimeType.Known.TYPESCRIPT
import org.jetbrains.jewel.foundation.code.MimeType.Known.YAML

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
@JvmInline
public value class MimeType(private val mimeType: String) {
    public fun displayName(): String =
        when (normalizeString()) {
            Known.KOTLIN.mimeType -> if (isGradle()) "Gradle DSL" else "Kotlin"
            Known.JAVA.mimeType -> "Java"
            Known.XML.mimeType -> {
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

            Known.JSON.mimeType -> "JSON"
            Known.TEXT.mimeType -> "Text"
            Known.REGEX.mimeType -> "Regular Expression"
            Known.GROOVY.mimeType -> if (isGradle()) "Gradle" else "Groovy"
            Known.TOML.mimeType -> if (isVersionCatalog()) "Version Catalog" else "TOML"
            Known.C.mimeType -> "C"
            Known.CPP.mimeType -> "C++"
            Known.SVG.mimeType -> "SVG"
            Known.AIDL.mimeType -> "AIDL"
            Known.SQL.mimeType -> "SQL"
            Known.PROGUARD.mimeType -> "Shrinker Config"
            Known.PROPERTIES.mimeType -> "Properties"
            Known.PROTO.mimeType -> "Protobuf"
            Known.PYTHON.mimeType -> "Python"
            Known.DART.mimeType -> "Dart"
            Known.RUST.mimeType -> "Rust"
            Known.JAVASCRIPT.mimeType -> "JavaScript"
            Known.AGSL.mimeType -> "Android Graphics Shading Language"
            Known.SHELL.mimeType -> "Shell Script"
            Known.YAML.mimeType -> "YAML"
            Known.GO.mimeType -> "Go"
            else -> mimeType
        }

    private fun normalizeString(): String {
        when (this) {
            // Built-ins are already normalized, don't do string and sorting work
            Known.KOTLIN,
            Known.JAVA,
            Known.TEXT,
            Known.XML,
            Known.PROPERTIES,
            Known.TOML,
            Known.JSON,
            Known.REGEX,
            Known.GROOVY,
            Known.C,
            Known.CPP,
            Known.SVG,
            Known.AIDL,
            Known.PROTO,
            Known.SQL,
            Known.PROGUARD,
            Known.MANIFEST,
            Known.RESOURCE,
            Known.GRADLE,
            Known.GRADLE_KTS,
            Known.VERSION_CATALOG,
            Known.PYTHON,
            Known.DART,
            Known.RUST,
            Known.JAVASCRIPT,
            Known.TYPESCRIPT,
            Known.AGSL,
            Known.SHELL,
            Known.YAML,
            Known.GO,
            Known.UNKNOWN -> return this.mimeType
        }

        val baseEnd = mimeType.indexOf(';')
        val normalizedBase =
            when (val base = if (baseEnd == -1) mimeType else mimeType.substring(0, baseEnd)) {
                "text/x-java-source",
                "application/x-java",
                "text/x-java" -> Known.JAVA.mimeType

                "application/kotlin-source",
                "text/x-kotlin",
                "text/x-kotlin-source" -> KOTLIN.mimeType

                "application/xml" -> Known.XML.mimeType
                "application/json",
                "application/vnd.api+json",
                "application/hal+json",
                "application/ld+json" -> JSON.mimeType

                "image/svg+xml" -> Known.XML.mimeType
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
                "application/x-ecmascript" -> Known.JAVASCRIPT.mimeType

                "application/typescript" + "application/x-typescript" -> TYPESCRIPT.mimeType
                "text/x-rust",
                "application/x-rust" -> RUST.mimeType

                "text/x-sksl" -> AGSL.mimeType
                "application/yaml",
                "text/x-yaml",
                "application/x-yaml" -> YAML.mimeType

                else -> base
            }

        if (baseEnd == -1) {
            return normalizedBase
        }

        val attributes =
            mimeType
                .split(';')
                .asSequence()
                .drop(1)
                .sorted()
                .mapNotNull {
                    val index = it.indexOf('=')
                    if (index != -1) {
                        it.substring(0, index).trim() to it.substring(index + 1).trim()
                    } else {
                        null
                    }
                }
                .filter { isRelevantAttribute(it.first) }
                .map { "${it.first}=${it.second}" }
                .joinToString("; ")

        return if (attributes.isNotBlank()) {
            "$normalizedBase; $attributes"
        } else {
            normalizedBase
        }
    }

    /** Returns whether the given attribute should be included in a normalized string */
    private fun isRelevantAttribute(attribute: String): Boolean =
        when (attribute) {
            ATTR_ROLE,
            ATTR_ROOT_TAG,
            ATTR_FOLDER_TYPE -> true

            else -> false
        }

    /**
     * Returns just the language portion of the mime type.
     *
     * For example, for `text/kotlin; role=gradle` this will return `text/kotlin`. For `text/plain; charset=us-ascii`
     * this returns `text/plain`
     */
    public fun base(): MimeType = MimeType(mimeType.substringBefore(';').trim())

    internal fun getRole(): String? = getAttribute(ATTR_ROLE)

    private fun getFolderType(): String? = getAttribute(ATTR_FOLDER_TYPE)

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

    public object Known {
        // Well known mime types for major languages.

        /**
         * Well known name for Kotlin source snippets. This is the base mime type; consider using [isKotlin] instead to
         * check if a mime type represents Kotlin code such that it also picks up `build.gradle.kts` files (which carry
         * extra attributes in the mime type; see [GRADLE_KTS].)
         */
        public val KOTLIN: MimeType = MimeType("text/kotlin")

        /** Well known name for Java source snippets. */
        public val JAVA: MimeType = MimeType("text/java")

        /** Well known mime type for text files. */
        public val TEXT: MimeType = MimeType("text/plain")

        /**
         * Special marker mimetype for unknown or unspecified mime types. These will generally be treated as [TEXT] for
         * editor purposes. (The standard "unknown" mime type is application/octet-stream (from RFC 2046) but we know
         * this isn't binary data; it's text.)
         *
         * Note that [MimeType] is generally nullable in places where it's optional instead of being set to this value,
         * but this mime type is there for places where we need a specific value to point to.
         */
        public val UNKNOWN: MimeType = MimeType("text/unknown")

        /**
         * Well known name for XML source snippets. This is the base mime type; consider using [isXml] instead to check
         * if a mime type represents any XML such that it also picks up manifest files, resource files etc., which all
         * carry extra attributes in the mime type; see for example [MANIFEST] and [RESOURCE].
         */
        public val XML: MimeType = MimeType("text/xml")
        public val PROPERTIES: MimeType = MimeType("text/properties")
        public val TOML: MimeType = MimeType("text/toml")
        public val JSON: MimeType = MimeType("text/json")
        public val REGEX: MimeType = MimeType("text/x-regex-source")
        public val GROOVY: MimeType = MimeType("text/groovy")
        public val C: MimeType = MimeType("text/c")
        public val CPP: MimeType = MimeType("text/c++")
        public val SVG: MimeType = MimeType("image/svg+xml")
        public val AIDL: MimeType = MimeType("text/x-aidl-source")
        public val PROTO: MimeType = MimeType("text/x-protobuf")
        public val SQL: MimeType = MimeType("text/x-sql")
        public val PROGUARD: MimeType = MimeType("text/x-proguard")
        public val PYTHON: MimeType = MimeType("text/python")
        public val JAVASCRIPT: MimeType = MimeType("text/javascript")
        public val TYPESCRIPT: MimeType = MimeType("text/typescript")
        public val DART: MimeType = MimeType("application/dart")
        public val RUST: MimeType = MimeType("text/rust")
        public val AGSL: MimeType = MimeType("text/x-agsl")
        public val SHELL: MimeType = MimeType("application/x-sh")
        public val YAML: MimeType = MimeType("text/yaml")
        public val GO: MimeType = MimeType("text/go")

        /** Note that most resource files will also have a folder type, so don't use equality on this mime type */
        public val RESOURCE: MimeType = MimeType("$XML; $ATTR_ROLE=resource")
        public val MANIFEST: MimeType = MimeType("$XML;$ATTR_ROLE=manifest $ATTR_ROOT_TAG=manifest")
        public val GRADLE: MimeType = MimeType("$GROOVY; $ATTR_ROLE=gradle")
        public val GRADLE_KTS: MimeType = MimeType("$KOTLIN; $ATTR_ROLE=gradle")
        public val VERSION_CATALOG: MimeType = MimeType("$TOML; $ATTR_ROLE=version-catalog")

        /** Maps from a markdown language [name] back to a mime type. */
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
                "golang" -> YAML

                else -> null
            }
    }
}

/** Is the base language for this mime type Kotlin? */
public fun MimeType?.isKotlin(): Boolean = this?.base() == MimeType.Known.KOTLIN

/** Is the base language for this mime type Java? */
public fun MimeType?.isJava(): Boolean = this?.base() == MimeType.Known.JAVA

/** Is the base language for this mime type XML? */
public fun MimeType?.isXml(): Boolean = this?.base() == MimeType.Known.XML

/** Is this a Gradle file (which could be in Groovy, *or*, Kotlin) */
public fun MimeType?.isGradle(): Boolean = this?.getRole() == "gradle"

/** Is this a version catalog file (which could be in TOML, or in Groovy) */
public fun MimeType?.isVersionCatalog(): Boolean = this?.getRole() == "version-catalog"

/** Is this an Android manifest file? */
public fun MimeType?.isManifest(): Boolean = this?.getRole() == "manifest"

/** Is the base language for this mime type SQL? */
public fun MimeType?.isSql(): Boolean = this?.base() == MimeType.Known.SQL

/** Is the base language for this mime type a regular expression? */
public fun MimeType?.isRegex(): Boolean = this?.base() == MimeType.Known.REGEX

/** Is the base language for this mime type a protobuf? */
public fun MimeType?.isProto(): Boolean = this?.base() == MimeType.Known.PROTO

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
