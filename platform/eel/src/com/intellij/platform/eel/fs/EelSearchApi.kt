// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.fs

import com.intellij.platform.eel.path.EelPath
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

/**
 * Server-side search: a single request walks the directory trees and matches file names and/or contents
 * on the remote side, streaming results back. The number of round trips does not depend on the tree size
 * or on the number of matches, which makes this the preferred way to implement goto-file-like and
 * find-in-files-like features over high-latency connections.
 *
 * Name matching ([EelSearchOptions.nameFilter]) is deliberately a superset pre-filter: it mirrors the
 * shared fast-reject of the IDE-side name matchers, and exact matching and ranking stay on the client.
 * Content is matched against the file's raw bytes (BOM-detected UTF-16 is transcoded first; UTF-32
 * content deterministically surfaces as [EelSearchEvent.Skipped.Reason.BINARY] skips), so files in encodings that are not
 * byte-compatible with the query's UTF-8 form may fail to match without being reported as skipped;
 * callers that need encoding-exact semantics must restrict queries accordingly (e.g. to ASCII)
 * or treat content search as a candidate generator. Files that were selected by the walk but not
 * searched are reported as [EelSearchEvent.Skipped] and are NOT covered by the search.
 *
 * Implemented by IJent filesystem APIs; non-IJent filesystem APIs do not implement this interface.
 * Consumer checks: `if (eelApi.fs is EelSearchApi) { ... }`.
 */
@ApiStatus.Internal
interface EelSearchApi {
  /**
   * Starts the search remotely and returns its results. Cancelling the collection cancels the remote search.
   * A request the server cannot execute (a malformed glob, a content pattern that could only match
   * across line boundaries) fails the flow before any event is emitted.
   */
  suspend fun search(options: EelSearchOptions): Flow<EelSearchEvent>
}

@ApiStatus.Internal
data class EelSearchOptions(
  /**
   * Directories to search under; overlapping roots are deduplicated by the server (an ancestor wins).
   * The roots themselves are never reported as hits; a hit is attributed to its root
   * through [EelSearchEvent.Hit.pathFromRoot].
   */
  val roots: List<EelPath>,
  /**
   * Case-insensitive subsequence pre-filter applied to `<root file name>/<path relative to root>`
   * with `/` separators on all platforms: a file passes when all filter characters occur in that string
   * in the given order. An empty string accepts every file.
   *
   * The root file name is the last path component of the root; it is empty when the root is a
   * filesystem root, and the haystack then starts with `/`. Characters are equated when either
   * their Unicode simple lowercase or simple uppercase mappings agree.
   */
  val nameFilter: String = "",
  /** When present, only regular files whose content matches are reported, with the match count filled in. */
  val content: ContentQuery? = null,
  /**
   * Globs in Rust `globset` syntax with `/` separators (`*` and `?` do not cross `/`, only `**` does),
   * matched case-sensitively against the whole path relative to the root, anchored: `.git` matches only
   * a top-level entry; prefix a glob with `**` and a separator to match at any depth. A matching path
   * is skipped entirely, including the whole subtree for a directory; exclusions produce no skip events.
   */
  val excludeGlobs: List<String> = emptyList(),
  /**
   * Globs in Rust `globset` syntax over the bare file name, case-insensitive; when non-empty,
   * only matching files are content-searched. Ignored in name mode.
   */
  val includeNameGlobs: List<String> = emptyList(),
  /** In content mode, files larger than this are reported as skipped. 0 means no limit. */
  val maxFileSize: Long = 0,
  /**
   * Stop the whole search after this many hits. When the budget cuts the search short, the stream ends
   * with [EelSearchEvent.Truncated]; a search that finds exactly this many hits and nothing more
   * afterwards completes without the marker. 0 means no limit.
   */
  val maxHits: Long = 0,
  /** In name mode, also report matching directories. */
  val yieldDirectories: Boolean = false,
  /**
   * When true, directory symlinks are walked into, and a symlink is reported with its target's type;
   * only regular targets are content-searched. Symlink loops and dangling symlinks surface as
   * file-level [EelSearchEvent.Skipped.Reason.IO_ERROR] skips (a loop's target is an ancestor already
   * being walked). When false, symlinks are reported as such in name mode and are never followed
   * or content-searched.
   */
  val followSymlinks: Boolean = false,
) {
  @ApiStatus.Internal
  data class ContentQuery(
    /**
     * Literal text, or a regex in Rust `regex` crate syntax when [regex] is true.
     * Matching is line-oriented: a pattern containing a literal line break (in any alternative)
     * is rejected as an invalid request.
     */
    val query: String,
    val regex: Boolean = false,
    val caseSensitive: Boolean = false,
    val wholeWords: Boolean = false,
  )
}

@ApiStatus.Internal
sealed interface EelSearchEvent {
  @ApiStatus.Internal
  data class Hit(
    val path: EelPath,
    /**
     * The exact haystack [EelSearchOptions.nameFilter] was applied to:
     * `<root file name>/<relative path>` with `/` separators. Run exact matchers on this string
     * instead of reconstructing it - it is byte-identical to what the server filtered by construction.
     */
    val pathFromRoot: String,
    /** The number of matching lines in content mode; 0 in name mode. */
    val matchCount: Long,
    val isDirectory: Boolean,
  ) : EelSearchEvent

  /**
   * A file or directory that was selected by the walk but not searched; it is NOT covered by this search.
   * An [Reason.IO_ERROR] with [isDirectory] means the whole subtree under [path] is not covered
   * (not enumerated, or its results cannot be delivered); any other skip covers only [path] itself.
   */
  @ApiStatus.Internal
  data class Skipped(
    val path: EelPath,
    val reason: Reason,
    val isDirectory: Boolean = false,
  ) : EelSearchEvent {
    @ApiStatus.Internal
    enum class Reason {
      TOO_LARGE,
      BINARY,

      /**
       * The file's encoding cannot be searched reliably. Reserved; not currently produced:
       * BOM-detected UTF-16 is transcoded and searched, UTF-32 is reported as [BINARY].
       */
      ENCODING,
      IO_ERROR,
    }
  }

  /**
   * The [EelSearchOptions.maxHits] budget was exhausted: unvisited files are not reported,
   * and coverage inference from skips is invalid for this search.
   */
  @ApiStatus.Internal
  object Truncated : EelSearchEvent
}
