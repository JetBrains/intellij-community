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
