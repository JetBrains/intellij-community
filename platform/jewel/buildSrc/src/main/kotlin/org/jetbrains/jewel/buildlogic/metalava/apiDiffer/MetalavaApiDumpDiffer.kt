package org.jetbrains.jewel.buildlogic.metalava.apiDiffer

import java.io.ByteArrayOutputStream
import java.io.File
import org.jetbrains.jewel.buildlogic.metalava.guessIdeaProjectDir

/**
 * Generates a Git-style unified patch by comparing a checked-in (reference) Metalava API dump
 * against a freshly generated dump derived from the current source code API surface.
 *
 * @param refMetalavaApiDump Checked-in API dump file.
 * @param currentMetalavaApiDump Freshly generated API dump file.
 * @param diffCommandExec Runs the `diff` command described by a [DiffCommandContext].
 */
internal class MetalavaApiDumpDiffer(
    private val refMetalavaApiDump: File,
    private val currentMetalavaApiDump: File,
    private val diffCommandExec: (DiffCommandContext) -> DiffExecResult,
) {
    /**
     * Defines the parameters for a `diff` command execution. Instances are created via [newContext].
     *
     * @property arguments Full command line including executable and flags.
     * @property ignoreExitValue Whether non-zero exit values are treated as non-fatal.
     * @property stdOutput Captures the process standard output (unified diff text).
     * @property errorOutput Captures the process standard error.
     */
    class DiffCommandContext private constructor(
        val arguments: List<String>,
        val ignoreExitValue: Boolean,
        val stdOutput: ByteArrayOutputStream,
        val errorOutput: ByteArrayOutputStream,
    ) {
        companion object {
            /**
             * Creates a unified-diff command context for Metalava API dump patches.
             *
             * @param refLabel Patch label (relative path) for the reference file.
             * @param refFile Reference API dump file.
             * @param curFile Current API dump file.
             * @param stdOutput Stream for capturing stdout; defaults to a new buffer.
             * @param errorOutput Stream for capturing stderr; defaults to a new buffer.
             */
            fun newContext(
                refLabel: String,
                refFile: File,
                curFile: File,
                stdOutput: ByteArrayOutputStream = ByteArrayOutputStream(),
                errorOutput: ByteArrayOutputStream = ByteArrayOutputStream(),
            ): DiffCommandContext =
                DiffCommandContext(
                    arguments =
                        listOf(
                            "diff",
                            "-u",
                            "--label",
                            "a/$refLabel",
                            "--label",
                            "b/$refLabel",
                            refFile.absolutePath,
                            curFile.absolutePath,
                        ),
                    ignoreExitValue = true,
                    stdOutput = stdOutput,
                    errorOutput = errorOutput,
                )
        }
    }


    /** Exit code of a `diff` command execution, decoupled from the Gradle API. */
    data class DiffExecResult(val exitValue: Int)

    /** Outcome of attempting to generate a patch from two API dump files. */
    sealed interface PatchGenerationResult {
        /** Patch was generated; [diffText] contains the unified diff. */
        data class Generated(val diffText: String) : PatchGenerationResult

        /** Files are identical - no patch needed. */
        data object NoChanges : PatchGenerationResult

        /** The `diff` executable is not available on this system. */
        data object DiffUnavailable : PatchGenerationResult

        /** The `diff` command failed; [message] describes the error. */
        data class DiffFailed(val message: String) : PatchGenerationResult
    }

    /**
     * Runs the `diff` command and returns a [PatchGenerationResult]. Patch labels use
     * `a/`/`b/` prefixes relative to the project root, making the output directly
     * applicable via `git apply`.
     */
    fun generateDiffPatch(): PatchGenerationResult {
        val refLabel = calculatePatchRefLabel()
                ?: return PatchGenerationResult.DiffFailed("Could not find IDEA project directory for" +
                    " $refMetalavaApiDump or $currentMetalavaApiDump")
        val diffCommandContext = DiffCommandContext.newContext(refLabel, refMetalavaApiDump, currentMetalavaApiDump)
        val result = try {
            diffCommandExec(diffCommandContext)
        } catch (_: Exception) {
            return PatchGenerationResult.DiffUnavailable
        }

        val diffText = diffCommandContext.stdOutput.toString(Charsets.UTF_8).ensureTrailingLineSeparator()
        val errorText = diffCommandContext.errorOutput.toString(Charsets.UTF_8).trim()

        return when (result.exitValue) {
            0 -> PatchGenerationResult.NoChanges
            1 ->
                when {
                    diffText.isBlank() ->
                        PatchGenerationResult.DiffFailed(
                            "The 'diff' command reported file differences but did not produce a patch."
                        )
                    else -> PatchGenerationResult.Generated(diffText)
                }
            else ->
                PatchGenerationResult.DiffFailed(
                    buildString {
                        append("The 'diff' command exited with code ${result.exitValue}.")
                        if (errorText.isNotEmpty()) {
                            appendLine()
                            append(errorText)
                        }
                    }
                )
        }
    }

    private fun String.ensureTrailingLineSeparator(): String =
        if (endsWith('\n')) this else this + "\n"

    /**
     * Returns the reference dump's path relative to the project root, or `null` if the
     * project root cannot be determined.
     *
     * Examples:
     * - For Ultimate checkout patch ref label is: `community/platform/jewel/ui/metalava/ui-api-stable-0.35.0.txt`
     * - For Community checkout patch ref label is: `platform/jewel/ui/metalava/ui-api-stable-0.35.0.txt`
     */
    private fun calculatePatchRefLabel(): String? {
        val ideaProjectDir = guessIdeaProjectDir(refMetalavaApiDump.parentFile)
            ?: guessIdeaProjectDir(currentMetalavaApiDump.parentFile)
            ?: return null

        // Use a/b prefixes so the patch is directly applicable with `git apply` (default -p1).
        // Both labels point to the reference file so git applies the diff in-place to the stable dump.
        return refMetalavaApiDump.relativeToOrSelf(ideaProjectDir).invariantSeparatorsPath
    }
}

/** Returns the diff text if the result is [Generated][MetalavaApiDumpDiffer.PatchGenerationResult.Generated], or `null` otherwise. */
internal fun MetalavaApiDumpDiffer.PatchGenerationResult.getDiffTextOrNull(): String? =
    when (this) {
        is MetalavaApiDumpDiffer.PatchGenerationResult.Generated -> diffText
        MetalavaApiDumpDiffer.PatchGenerationResult.NoChanges -> null
        MetalavaApiDumpDiffer.PatchGenerationResult.DiffUnavailable -> null
        is MetalavaApiDumpDiffer.PatchGenerationResult.DiffFailed -> null
    }
