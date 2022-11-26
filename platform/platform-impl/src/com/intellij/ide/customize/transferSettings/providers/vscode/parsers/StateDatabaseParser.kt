// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.providers.vscode.parsers

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeType
import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.ide.customize.transferSettings.models.Settings
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.io.FileUtil
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

private val logger = logger<StateDatabaseParser>()

class StateDatabaseParser(private val settings: Settings) {
  private val recentsKey = "history.recentlyOpenedPathsList"

  lateinit var connection: Connection

  fun process(file: File) {
    try {
      Class.forName("org.sqlite.JDBC")
      connection = DriverManager.getConnection("jdbc:sqlite:" + FileUtil.toSystemIndependentName(file.path))
      parseRecents()
    }
    catch (t: Throwable) {
      logger.warn(t)
    }
  }

  private fun parseRecents() {
    val recentProjectsRaw = getKey(recentsKey) ?: return

    val root = ObjectMapper(JsonFactory().enable(JsonParser.Feature.ALLOW_COMMENTS)).readTree(recentProjectsRaw)
                 as? ObjectNode
               ?: error("Unexpected JSON data; expected: ${JsonNodeType.OBJECT}")

    val paths = (root["entries"] as ArrayNode).mapNotNull { it["folderUri"]?.textValue() }

    paths.forEach { uri ->
      val res = StorageParser.parsePath(uri)
      if (res != null) {
        settings.recentProjects.add(res)
      }
    }
  }

  private fun getKey(key: String): String? {
    val query = "SELECT value FROM ItemTable WHERE key is '$key' LIMIT 1"

    val res = connection.createStatement().executeQuery(query)
    if (!res.next()) {
      return null
    }

    return res.getString("value")
  }
}