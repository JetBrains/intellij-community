// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.svg.getSvgDocumentSize
import com.intellij.util.io.PreemptiveSafeFileOutputStream
import org.jetbrains.annotations.ApiStatus
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

private val LOG = logger<MigrationRedirectContent>()

/** The config import skips this file, as [MIGRATION_STATE_FILE_NAME]. */
internal const val MIGRATION_REDIRECT_CONTENT_FILE_NAME: String = "migration-redirect.json"
private const val CONTENT_VERSION = 1

private const val WINDOW_TITLE = "windowTitle"
private const val TITLE = "title"
private const val BODY = "body"
private const val PRIMARY_BUTTON = "primaryButton"
private val TEXT_KEYS = setOf(WINDOW_TITLE, TITLE, BODY, PRIMARY_BUTTON)

private const val SOURCE_LOGO = "sourceLogo"
private const val TARGET_LOGO = "targetLogo"

/**
 * The texts of the redirect window after the migration, in one locale. Each text is plain text, not HTML.
 */
@ApiStatus.Internal
class MigrationRedirectTexts(
  val windowTitle: @NlsSafe String,
  val title: @NlsSafe String,
  val body: @NlsSafe String,
  val primaryButton: @NlsSafe String,
)

/** Everything that the redirect window shows. The window loads nothing from the network. */
@ApiStatus.Internal
class MigrationRedirectContent(
  val texts: MigrationRedirectTexts,
  val sourceLogo: MigrationImageData,
  val targetLogo: MigrationImageData,
)

/**
 * The copy of the content next to the [stateFile]. The install writes it, because the server content is in memory only at that time.
 * The startup reads it before the network and the services exist.
 */
@ApiStatus.Internal
fun migrationRedirectContentFile(stateFile: Path = migrationStateFile()): Path =
  stateFile.resolveSibling(MIGRATION_REDIRECT_CONTENT_FILE_NAME)

/**
 * Writes a temporary file next to [file], then renames it. The images are written as base64, so the bytes do not change.
 * A `null` [content] deletes [file], so that the redirect window does not show old texts.
 */
@ApiStatus.Internal
@Throws(IOException::class)
fun writeMigrationRedirectContent(content: MigrationRedirectContent?, file: Path) {
  if (content == null) {
    Files.deleteIfExists(file)
    return
  }
  val texts = content.texts
  val text = formatMigrationJson {
    writeNumberProperty("version", CONTENT_VERSION)
    writeName("texts")
    writeStartObject()
    writeStringProperty(WINDOW_TITLE, texts.windowTitle)
    writeStringProperty(TITLE, texts.title)
    writeStringProperty(BODY, texts.body)
    writeStringProperty(PRIMARY_BUTTON, texts.primaryButton)
    writeEndObject()
    writeName("images")
    writeStartObject()
    writeImage(SOURCE_LOGO, content.sourceLogo)
    writeImage(TARGET_LOGO, content.targetLogo)
    writeEndObject()
  }
  Files.createDirectories(file.parent)
  PreemptiveSafeFileOutputStream(file).use { it.write(text.toByteArray(Charsets.UTF_8)) }
}

/**
 * Returns the content in [file], or `null` when the file does not exist or is not valid. The log gets the reason.
 * A valid content has the window title, the title, the body and the primary button, not blank.
 * It has the light and the dark SVG of both logos. Unknown fields are ignored.
 */
@ApiStatus.Internal
fun readMigrationRedirectContent(file: Path): MigrationRedirectContent? {
  val text = try {
    Files.readString(file)
  }
  catch (_: NoSuchFileException) {
    LOG.info("no migration redirect content: ${file}")
    return null
  }
  catch (e: IOException) {
    LOG.warn("cannot read ${file}", e)
    return null
  }

  return try {
    parseContent(text)
  }
  catch (e: InvalidMigrationJsonException) {
    LOG.info("the migration redirect content is ignored: ${e.message}")
    null
  }
}

private fun parseContent(text: String): MigrationRedirectContent {
  var version: Int? = null
  val texts = HashMap<String, String?>()
  val images = HashMap<String, MigrationImageData>()
  parseMigrationJson(text) { parser, name ->
    when (name) {
      "version" -> version = parser.intOrNull()
      "texts" -> readMigrationJsonObject(parser) { textParser, key ->
        if (key in TEXT_KEYS) texts[key] = textParser.stringOrNull() else textParser.skipChildren()
      }
      "images" -> readMigrationJsonObject(parser) { imageParser, key ->
        if (key == SOURCE_LOGO || key == TARGET_LOGO) images[key] = readImage(imageParser, key) else imageParser.skipChildren()
      }
      else -> parser.skipChildren()
    }
  }

  if (version != CONTENT_VERSION) invalidMigrationJson("unsupported 'version': ${version}")
  fun text(key: String): String = texts[key]?.takeIf { it.isNotBlank() } ?: invalidMigrationJson("no 'texts.${key}'")
  return MigrationRedirectContent(
    texts = MigrationRedirectTexts(
      windowTitle = text(WINDOW_TITLE),
      title = text(TITLE),
      body = text(BODY),
      primaryButton = text(PRIMARY_BUTTON),
    ),
    sourceLogo = images[SOURCE_LOGO] ?: invalidMigrationJson("no 'images.${SOURCE_LOGO}'"),
    targetLogo = images[TARGET_LOGO] ?: invalidMigrationJson("no 'images.${TARGET_LOGO}'"),
  )
}

private fun readImage(parser: JsonParser, name: String): MigrationImageData {
  var light: ByteArray? = null
  var dark: ByteArray? = null
  readMigrationJsonObject(parser) { variantParser, variant ->
    when (variant) {
      "light" -> light = variantParser.binaryOrNull()
      "dark" -> dark = variantParser.binaryOrNull()
      else -> variantParser.skipChildren()
    }
  }
  return MigrationImageData(
    light = checkedSvg(light ?: invalidMigrationJson("no 'images.${name}.light'"), "${name}.light"),
    dark = checkedSvg(dark ?: invalidMigrationJson("no 'images.${name}.dark'"), "${name}.dark"),
  )
}

private fun checkedSvg(data: ByteArray, name: String): ByteArray {
  try {
    getSvgDocumentSize(data)
  }
  catch (e: Exception) {
    rethrowControlFlowException(e)
    invalidMigrationJson("'images.${name}' is not an SVG image: ${e.message}")
  }
  return data
}

private fun JsonGenerator.writeImage(name: String, image: MigrationImageData) {
  writeName(name)
  writeStartObject()
  writeBinaryProperty("light", image.light)
  writeBinaryProperty("dark", image.dark)
  writeEndObject()
}
