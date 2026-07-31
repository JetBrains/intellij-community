#!/usr/bin/env kotlin
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

// Emits GitHub Actions annotations for detekt findings by reading the checkstyle reports produced by
// detektMain (main.xml) and detektTest (test.xml) under the current directory (the Jewel root). This is
// independent of GitHub code scanning and mirrors annotate-api-dump-changes.main.kts: it prints
// ::error / ::warning / ::notice workflow commands and writes a short GITHUB_STEP_SUMMARY.
//
// detekt writes checkstyle paths relative to the Gradle root (platform/jewel), while GitHub annotations
// need repo-root-relative paths, so we prefix them accordingly. The failing detekt task is the actual gate — these
// annotations are purely for visibility on the PR's "Files changed" tab.

val scanRoot = File(".").canonicalFile
val workspace =
    System.getenv("GITHUB_WORKSPACE")?.let { File(it).canonicalFile } ?: scanRoot.parentFile?.parentFile ?: scanRoot
val pathPrefix = scanRoot.relativeToOrNull(workspace)?.path?.takeIf { it.isNotBlank() }

fun repoRelative(rawPath: String): String {
    val file = File(rawPath)
    return when {
        file.isAbsolute -> file.canonicalFile.relativeToOrNull(workspace)?.path ?: rawPath
        pathPrefix != null -> "$pathPrefix/$rawPath"
        else -> rawPath
    }
}

fun ghLevel(severity: String) =
    when (severity.lowercase()) {
        "error" -> "error"
        "warning" -> "warning"
        else -> "notice"
    }

fun escapeData(text: String) = text.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")

fun escapeProperty(text: String) = escapeData(text).replace(":", "%3A").replace(",", "%2C")

val reports =
    scanRoot
        .walkTopDown()
        .filter { it.isFile && (it.name == "main.xml" || it.name == "test.xml") }
        .filter { it.parentFile?.name == "detekt" && it.parentFile?.parentFile?.name == "reports" }
        .toList()

val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()

var errors = 0
var warnings = 0
var notices = 0

for (report in reports) {
    val document = builder.parse(report)
    val fileNodes = document.getElementsByTagName("file")
    for (i in 0 until fileNodes.length) {
        val fileElement = fileNodes.item(i) as Element
        val path = repoRelative(fileElement.getAttribute("name"))
        val errorNodes = fileElement.getElementsByTagName("error")
        for (j in 0 until errorNodes.length) {
            val error = errorNodes.item(j) as Element
            val level = ghLevel(error.getAttribute("severity"))
            when (level) {
                "error" -> errors++
                "warning" -> warnings++
                else -> notices++
            }
            val line = error.getAttribute("line").toIntOrNull() ?: 1
            val column = error.getAttribute("column").toIntOrNull()
            val rule = error.getAttribute("source").removePrefix("detekt.")
            val message = error.getAttribute("message")
            val columnPart = column?.let { ",col=$it" } ?: ""
            println(
                "::$level file=${escapeProperty(path)},line=$line$columnPart," +
                    "title=${escapeProperty("detekt: $rule")}::${escapeData(message)}"
            )
        }
    }
}

val total = errors + warnings + notices
val summary = buildString {
    appendLine("## Detekt results")
    appendLine()
    if (total == 0) {
        appendLine("✅ No Detekt findings.")
    } else {
        appendLine("❌ $total Detekt finding(s): $errors error, $warnings warning, $notices notice.")
    }
}
System.getenv("GITHUB_STEP_SUMMARY")?.takeIf { it.isNotBlank() }?.let { File(it).appendText(summary) }
println("Emitted annotations for $total Detekt finding(s)")
