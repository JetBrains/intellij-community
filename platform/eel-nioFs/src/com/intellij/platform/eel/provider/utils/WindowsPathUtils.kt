// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider.utils

import com.intellij.platform.eel.path.EelPath
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.pathString

@ApiStatus.Internal
object WindowsPathUtils {
  const val DRIVE_PATH_PREPEND: String = "@"
  val WINDOWS_DRIVE_PREFIX_REGEX: Regex = Regex("^\\w:")

  /**
   * Returns synthetic per-drive roots `A..Z` under [mountRoot] for use in
   * `MultiRoutingFileSystemBackend.getCustomRoots`. VFS root lookup does strict equality between
   * `Path.of(p).getRoot().toString()` and `getRootDirectories()`; for Windows targets `getRoot()`
   * is per-drive (`/<mount>/@/C`), so customRoots must list each drive.
   *
   * [mountRoot] is normalized internally to forward slashes with no trailing `/` (matching the
   * `@MultiRoutingFileSystemPath` convention enforced by `MultiRoutingFileSystem.sanitizeRoot`),
   * so it is safe to pass a raw `Path.toString()` result from any host OS.
   *
   * If the normalized mount already ends with `/@`, drive letters are appended directly; otherwise
   * the `/@` drive-encoding zone is inserted to match composition in [resolveEelPathOntoRoot].
   *
   * No I/O. Non-existent drives are filtered out lazily by VFS (`findRoot` returns null).
   */
  fun expandPerDriveRoots(mountRoot: String): List<String> {
    val root = mountRoot.replace('\\', '/').trimEnd('/')
    val driveZone = if (root.endsWith("/$DRIVE_PATH_PREPEND")) "" else "/$DRIVE_PATH_PREPEND"
    return ('A'..'Z').map { "$root$driveZone/$it" }
  }

  /**
   * Parses the UNC `<server>/<share>` prefix of a Windows path under [mountRoot] (composed by
   * [resolveEelPathOntoRoot]) and returns the path that VFS will see as `Path.of(p).getRoot()`.
   * Returns `null` if [path] is not under [mountRoot], or its first segment under the mount looks
   * like a drive letter, or no `<share>` segment is present.
   *
   * UNC composition does not insert the `/@` zone, so the resulting root is `<mountRoot>/<server>/<share>`.
   * Use in `MultiRoutingFileSystemBackend.compute` to lazily collect seen UNC roots for `getCustomRoots`.
   *
   * [mountRoot] is normalized internally to forward slashes with no trailing `/` (matching
   * `MultiRoutingFileSystem.sanitizeRoot`); [path] is assumed already sanitized (it is the
   * `sanitizedPath` passed to `compute`).
   *
   * Examples (mount with `/@` drive-encoding zone vs. without):
   * ```
   * extractUncRoot("/$tcp.ij/abc/@", "/$tcp.ij/abc/@/server/share/dir") == "/$tcp.ij/abc/@/server/share"
   * extractUncRoot("/$tcp.ij/abc",   "/$tcp.ij/abc/server/share/dir")   == "/$tcp.ij/abc/server/share"
   * extractUncRoot("/$tcp.ij/abc/@", "/$tcp.ij/abc/@/C/Users")          == null  // drive letter, not UNC
   * extractUncRoot("/$tcp.ij/abc",   "/$tcp.ij/xyz/server/share")       == null  // outside mount
   * extractUncRoot("/$tcp.ij/abc/@", "/$tcp.ij/abc/@/server")           == null  // missing share segment
   * ```
   */
  fun extractUncRoot(mountRoot: String, path: String): String? {
    val root = mountRoot.replace('\\', '/').trimEnd('/')
    if (path != root && !path.startsWith("$root/")) return null
    var rest = path.removePrefix(root).removePrefix("/")
    if (!root.endsWith("/$DRIVE_PATH_PREPEND")) rest = rest.removePrefix("$DRIVE_PATH_PREPEND/")
    // Drive letters are single-char (`C`, `D`, ...); UNC server names are >= 2 chars.
    val server = rest.substringBefore('/', "").takeIf { it.length > 1 } ?: return null
    val afterServer = rest.removePrefix(server).removePrefix("/")
    val share = afterServer.substringBefore('/', afterServer).takeIf { it.isNotEmpty() } ?: return null
    return "$root/$server/$share"
  }

  /**
   * Converts Windows [eelPath] into nio path under virtual mount [root].
   *
   * If [root] is posix path `/$some.ij/mount/`, then the result will be`:
   * `C:` -> `/$some.ij/mount/@/C`
   * `C:\` -> `/$some.ij/mount/@/C/`
   * `C:\Users` -> `/$some.ij/mount/@/C/Users`
   * `\\server.share\dir\` -> `/$some.ij/mount/server.share/dir/`
   *
   * If [root] is Windows path `\\some.ij\mount\`, then the result will be:
   * `C:` -> `\\some.ij\mount\@\C`
   * `C:\` -> `\\some.ij\mount\@\C\`
   * `C:\Users` -> `\\some.ij\mount\@\C\Users`
   * `\\server.share\dir\` -> `\\some.ij\mount\server.share\dir\`
   */
  fun resolveEelPathOntoRoot(root: Path, eelPath: EelPath): Path {
    val rootParts = if (WINDOWS_DRIVE_PREFIX_REGEX.containsMatchIn(eelPath.root.toString())) {
      listOf(DRIVE_PATH_PREPEND, eelPath.root.toString().take(1))
    }
    else if (eelPath.root.toString().startsWith("\\\\")) {
      eelPath.root.toString().removePrefix("\\\\").removeSuffix("\\").split("\\", limit = 2)
    } else {
      error("Unsupported root: ${eelPath.root}")
    }
    return (rootParts + eelPath.parts).fold(root, Path::resolve)
  }

  /**
   * Performs backward conversion. Accepts and returns system independent slashes.
   *
   * `@/C/Users` -> `C:/Users`
   * `server.share/dir/` -> `//server.share/dir/`
   *
   */
  fun rootRelativeToEelPath(relativePath: String): String {
    return if (relativePath.startsWith("@/")) {
      "${relativePath[2]}:${relativePath.drop(3)}"
    }
    else {
      "//$relativePath"
    }
  }

  /**
   * Performs backward conversion. Returns a pair of root and remaining path, both ready for constructing EelPath.
   *
   * `@/C/Users` -> `C:`, `Users`
   * `server.share/dir/tmp` -> `\\server.share\dir`, `tmp`
   *
   */
  fun rootRelativeToEelPath(relativePath: Path): Pair<String, Path> {
    val rest = when (relativePath.nameCount) {
      in 0..1 -> error("windows relative path should contain at least : $relativePath")
      2 -> Path("")
      else -> relativePath.subpath(2, relativePath.nameCount)
    }
    val root = if (relativePath.startsWith(Path("@"))) {
      "${relativePath.elementAt(1).pathString}:"
    }
    else {
      "\\\\${relativePath.elementAt(0).pathString}\\${relativePath.elementAt(1).pathString}"

    }
    return root to rest
  }

}
