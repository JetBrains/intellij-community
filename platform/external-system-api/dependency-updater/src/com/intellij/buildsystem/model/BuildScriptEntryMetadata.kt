package com.intellij.buildsystem.model

data class BuildScriptEntryMetadata constructor(
    val startLine: Int,
    val startColumn: Int,
    val endLine: Int,
    val endColumn: Int,
    val rawText: String
) {

    private val linesCount: Int = endLine - startLine + 1

    init {
        require(startLine >= 1) { "The startLine value is 1-based, and must be [1, +inf), but was $startLine" }
        require(startColumn >= 1) { "The startColumn value is 1-based, and must be [1, +inf), but was $startColumn" }
        require(endLine >= 1) { "The endLine value is 1-based, and must be [1, +inf), but was $endLine" }
        require(endColumn >= 1) { "The endColumn value is 1-based, and must be [1, +inf), but was $endColumn" }
        require(endLine >= startLine) { "The endLine value must be equal or greater to the startLine value" }

        if (linesCount == 1) {
            require(endColumn >= startColumn) { "The endColumn value must be equal or greater to the startColumn value" }
        }
    }
}
