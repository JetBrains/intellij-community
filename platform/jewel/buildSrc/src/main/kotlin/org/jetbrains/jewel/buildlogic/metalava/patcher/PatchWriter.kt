package org.jetbrains.jewel.buildlogic.metalava.patcher

import java.io.File

/**
 * Writes generated patch text to a file.
 *
 * @param patchFile Destination file for the patch output.
 */
internal class PatchWriter(private val patchFile: File) {
    /** Outcome of a patch file write attempt. */
    sealed interface PatchWriteResult {
        /** Write succeeded; [patchFile] is the written file. */
        data class Success(val patchFile: File) : PatchWriteResult

        /** Write failed; [message] describes the error. */
        data class Failure(val message: String) : PatchWriteResult
        
        /** Returns `true` if this result represents a successful write. */
        fun isSuccess() = this is Success
    }

    /**
     * Writes [patchText] to the configured file and returns the outcome.
     *
     * @param patchText Unified diff content to persist.
     */
    fun write(patchText: String): PatchWriteResult {
        patchFile.parentFile.mkdirs()

        return try {
            patchFile.writeText(patchText, Charsets.UTF_8)

            PatchWriteResult.Success(patchFile = patchFile)
        } catch (t: Throwable) {
            PatchWriteResult.Failure(
                "Failed to write patch file '${patchFile.absolutePath}': ${t.message ?: t::class.java.name}"
            )
        }
    }
}
