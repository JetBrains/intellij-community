package org.jetbrains.jewel.scripts.bazel

import com.facebook.ktfmt.format.Formatter
import com.facebook.ktfmt.format.FormattingOptions
import java.io.File
import kotlin.system.exitProcess

// Run it like: bazel run //platform/jewel/build-scripts/bazel:ktfmtCheck or ktfmtFormat
fun main(args: Array<String>) {
    val parameter: String = args.getOrThrow(0)

    val jewelRoot = getJewelRoot()

    val filesWithWrongFormat: MutableList<String> = mutableListOf()

    val formattingOptions =
        FormattingOptions(
            maxWidth = 120,
            blockIndent = 4,
            continuationIndent = 4,
            manageTrailingCommas = true,
            removeUnusedImports = true,
        )

    when (parameter) {
        "format" -> {
            jewelRoot
                ?.walkTopDownValidDirectories()
                ?.filter { it.isKotlinFile() }
                ?.forEach { file ->
                    val formattedFile = Formatter.format(formattingOptions, file.readText())

                    file.writeText(formattedFile)
                }

            println("\n${"SUCCESS:".asSuccess()} Files were formatted.")
        }
        "check" -> {
            jewelRoot
                ?.walkTopDownValidDirectories()
                ?.filter { it.isKotlinFile() }
                ?.forEach { file ->
                    val fileText = file.readText()
                    val formattedFile = Formatter.format(formattingOptions, fileText)

                    if (formattedFile != fileText) {
                        filesWithWrongFormat.add(file.path)
                    }
                }

            if (filesWithWrongFormat.isNotEmpty()) {
                println("\n${red("ERROR: ")} Some files are not properly formatted. Here's the list:")
                filesWithWrongFormat.forEach { filePath -> println("    - $filePath") }
                println(
                    "\nPlease run `bazel run //platform/jewel/build-scripts/bazel:ktfmtFormat` in the terminal or run " +
                        "the `ktfmtFormat` target in platform/jewel/build-scripts/BUILD.bazel file."
                )

                exitProcess(1)
            } else {
                println("\n${"SUCCESS:".asSuccess()} All files are correctly formatted.")
            }
        }
        else -> {
            error("Unknown parameter '$parameter'. Valid values: format, check")
        }
    }
}

private fun File.walkTopDownValidDirectories() =
    this.walkTopDown().onEnter { dir ->
        dir.name != "build" &&
            dir.name != "generated" &&
            dir.name != "buildSrc" // TODO: remove this check once we get rid of Gradle.
    }

private fun File.isKotlinFile() = this.isFile && this.name.substringAfterLast(".") == "kt"
