// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ide

import com.intellij.ide.IdeBundle
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.JBProtocolCommand
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.BaseProjectDirectories.Companion.getBaseDirectories
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.NlsContexts.DialogMessage
import com.intellij.platform.eel.fs.EelFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.VisibleForTesting
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.isRegularFile

private const val COMMAND: String = "project"
private const val GIT_URL_PARAM: String = "git_url"
/**
 * Handles `jetbrains://open/project?git_url=<url>` URLs.
 *
 * Looks for a known (open or recent) project whose git origin URL matches [GIT_URL_PARAM] and brings it forward,
 * opening it from disk if the matching project is in the recent list but not currently open.
 * If no match is found, delegates to the existing `checkout` command (`JBProtocolCheckoutCommand`) to clone.
 */
@Internal
class OpenProjectByGitUrlJbProtocolCommand : JBProtocolCommand(COMMAND) {
  override suspend fun execute(target: String?, parameters: Map<String, String>, fragment: String?): @DialogMessage String? {
    val gitUrl = parameters[GIT_URL_PARAM]?.takeIf { it.isNotBlank() }
                 ?: return IdeBundle.message("jb.protocol.parameter.missing", GIT_URL_PARAM)

    if (openKnownProjectByGitUrl(gitUrl) != null) {
      return null
    }

    val encoded = URLEncoder.encode(gitUrl, StandardCharsets.UTF_8)
    return execute("open/checkout/git?checkout.repo=$encoded").message
  }
}

/**
 * Returns a [Project] whose git remote URL (any remote — `origin`, `upstream`, fork mirrors, etc.) matches
 * [gitUrl] after normalization, or `null` if no known (open or recent) project matches.
 *
 * If the matching project is already open, its window is focused. If it is in the recent-projects list but
 * not open, it is opened (which also focuses its window).
 *
 * For open projects every base directory reported by [getBaseDirectories] is checked, so layouts with more
 * than one top-level root (Gradle composite builds with `includeBuild`, multi-root `.ipr` workspaces, etc.)
 * are matched against the URL — not only the directory that happens to be [Project.getBasePath]. Recent
 * projects only carry a path, so they fall back to that path plus the existing ancestor walk-up; sibling
 * roots of a composite build are not visible until the project is opened.
 *
 * Git remotes are read directly from `.git/config` to avoid running a `git config` subprocess —
 * that path requires either a [Project] context or that the working directory be in the trusted-paths set,
 * neither of which holds when scanning recent projects (see `GitHandler.start()`).
 *
 * URL normalization strips scheme, userinfo, port, trailing `.git`, trailing slash, and lowercases the host —
 * so `https://github.com/Foo/Bar.git`, `git@github.com:Foo/Bar.git`, and `ssh://git@github.com/Foo/Bar` all match.
 */
@Internal
suspend fun openKnownProjectByGitUrl(gitUrl: String): Project? {
  val target = normalizeGitUrl(gitUrl)
  LOG.info("openKnownProjectByGitUrl: input='$gitUrl' normalized='$target'")
  if (target == null) {
    LOG.info("openKnownProjectByGitUrl: input did not normalize, giving up")
    return null
  }

  val openProjects = ProjectManager.getInstance().openProjects
  LOG.info("openKnownProjectByGitUrl: scanning ${openProjects.size} open project(s)")
  for (project in openProjects) {
    if (project.isDisposed) {
      continue
    }
    val baseDirs = project.getBaseDirectories()
      .mapNotNull { it.fileSystem.getNioPath(it) }
      .ifEmpty { listOfNotNull(project.basePath?.let(::toPath)) }
    if (baseDirs.isEmpty()) {
      LOG.info("  open project '${project.name}': no base directories (basePath='${project.basePath}')")
      continue
    }
    val match = baseDirs.firstNotNullOfOrNull { dir ->
      readGitRemoteUrls(dir).firstOrNull { normalizeGitUrl(it.url) == target }
    }
    if (match != null) {
      LOG.info("  -> match on open project '${project.name}' via remote '${match.name}' (${match.url}), focusing")
      withContext(Dispatchers.EDT) {
        ProjectUtil.focusProjectWindow(project, true)
      }
      return project
    }
    LOG.info("  open project '${project.name}' baseDirs=${baseDirs.size}, no match")
  }

  val recents = RecentProjectsManagerBase.getInstanceEx()
  val recentPaths = recents.getRecentPaths()
  LOG.info("openKnownProjectByGitUrl: scanning ${recentPaths.size} recent path(s)")
  for (pathStr in recentPaths) {
    val path = toPath(pathStr)
    if (path == null) {
      LOG.info("  recent path '$pathStr' is unparseable")
      continue
    }
    val remotes = readGitRemoteUrls(path)
    val match = remotes.firstOrNull { normalizeGitUrl(it.url) == target }
    if (match != null) {
      LOG.info("  -> match on recent '$path' via remote '${match.name}' (${match.url}), opening")
      return recents.openProject(path, OpenProjectTask())
    }
    LOG.info("  recent path='$path' remotes=${remotes.size}, no match")
  }

  LOG.info("openKnownProjectByGitUrl: no match for '$target'")
  return null
}

@Internal
data class GitRemote(val name: String, val url: String)

/**
 * Walks up from [projectDir] to find the git directory and returns every `[remote "*"] url = …` from
 * its `config`, in the order they appear. Returns an empty list if no git directory is found.
 *
 * Handles three layouts:
 * - regular checkout: `.git` is a directory; config is `<.git>/config`.
 * - submodule: `.git` is a file `gitdir: <gitdir>`; config is `<gitdir>/config`.
 * - worktree: `.git` is a file `gitdir: <gitdir>` where `<gitdir>/commondir` points at the main
 *   repository's `.git`; remotes live in the main repository's config, not the worktree's.
 *
 * Reads the file directly as INI text — does not invoke `git`, so it works for paths whose project is not
 * currently open or trusted (unlike [com.intellij.ide.impl.getProjectOriginUrl]).
 */
@Internal
fun readGitRemoteUrls(projectDir: Path): List<GitRemote> {
  var dir: Path? = projectDir
  while (dir != null) {
    val gitDir = resolveGitDir(dir.resolve(".git"))
    if (gitDir != null) {
      val configDir = readCommonDir(gitDir) ?: gitDir
      val config = configDir.resolve("config")
      return if (config.isRegularFile()) parseRemoteUrlsFromConfig(config) else emptyList()
    }
    dir = dir.parent
  }
  return emptyList()
}

private const val GIT_DIR_PREFIX = "gitdir:"

/**
 * Resolves a `.git` entry to the actual git directory: returns [dotGit] itself when it's a directory,
 * or follows the `gitdir: <path>` pointer when it's a regular file (submodule/worktree layout).
 * Relative pointer paths are resolved against [dotGit]'s parent.
 */
@Internal
@VisibleForTesting
fun resolveGitDir(dotGit: Path): Path? {
  val attrs = try { Files.readAttributes(dotGit, BasicFileAttributes::class.java) } catch (_: Exception) { return null }
  if (attrs.isDirectory) return dotGit
  if (!attrs.isRegularFile) return null

  val content = try { EelFiles.readString(dotGit, Charsets.UTF_8).trim() } catch (_: Exception) { return null }
  if (!content.startsWith(GIT_DIR_PREFIX)) return null
  val target = content.substring(GIT_DIR_PREFIX.length).trim().takeIf { it.isNotEmpty() } ?: return null

  val pointer = try { Paths.get(target) } catch (_: Exception) { return null }
  val resolved = if (pointer.isAbsolute) pointer else (dotGit.parent ?: return null).resolve(pointer)
  return resolved.normalize()
}

/**
 * If [gitDir] is a worktree gitdir, reads its `commondir` pointer and returns the resolved path to the
 * main repository's git directory; otherwise `null`. Relative pointer paths are resolved against [gitDir].
 */
@Internal
@VisibleForTesting
fun readCommonDir(gitDir: Path): Path? {
  val commonDirFile = gitDir.resolve("commondir")
  val content = try { EelFiles.readString(commonDirFile, Charsets.UTF_8).trim() } catch (_: Exception) { return null }
  if (content.isEmpty()) return null
  val pointer = try { Paths.get(content) } catch (_: Exception) { return null }
  val resolved = if (pointer.isAbsolute) pointer else gitDir.resolve(pointer)
  return resolved.normalize()
}

private val REMOTE_HEADER = Regex("""^\[remote\s+"([^"]+)"]$""", RegexOption.IGNORE_CASE)
private val SECTION_HEADER = Regex("""^\[.*]$""")

@Internal
@VisibleForTesting
fun parseRemoteUrlsFromConfig(configFile: Path): List<GitRemote> {
  val lines = try {
    Files.readAllLines(configFile, Charsets.UTF_8)
  }
  catch (e: Exception) {
    LOG.info("  failed to read $configFile: ${e.message}")
    return emptyList()
  }
  val remotes = mutableListOf<GitRemote>()
  var currentRemote: String? = null
  for (raw in lines) {
    val line = raw.trim().substringBefore('#').substringBefore(';').trim()
    if (line.isEmpty()) continue
    if (SECTION_HEADER.matches(line)) {
      currentRemote = REMOTE_HEADER.matchEntire(line)?.groupValues?.get(1)
      continue
    }
    val name = currentRemote ?: continue
    val eq = line.indexOf('=')
    if (eq < 0) continue
    val key = line.substring(0, eq).trim()
    val value = line.substring(eq + 1).trim().trim('"')
    if (key.equals("url", ignoreCase = true)) remotes.add(GitRemote(name, value))
  }
  return remotes
}

private val LOG = logger<OpenProjectByGitUrlJbProtocolCommand>()

private fun toPath(path: String): Path? = try { Paths.get(path) } catch (_: Exception) { null }

// Matches only the `<scheme>://` header — deliberately omits a trailing `(.+)$` so the match metadata is
// O(scheme length) regardless of how long the rest of the URL is. The post-header walk uses indexOf on the
// original string so the only substrings allocated are the (short) authority and the (returned) path.
private val SCHEME_HEAD = Regex("""^[a-zA-Z][a-zA-Z0-9+.\-]*://""")
private val SCP_LIKE = Regex("""^(?:[^@/:\s]+@)?([^/:\s]+):(.+)$""")

private fun normalizeGitUrl(url: String): String? {
  val trimmed = url.trim()
  if (trimmed.isEmpty()) return null

  val (host, path) = parseHostAndPath(trimmed) ?: return null
  val cleanPath = path
    .removePrefix("/")
    .replace(Regex("/+"), "/")
    .removeSuffix(".git")
    .removeSuffix("/")
  if (host.isBlank() || cleanPath.isBlank()) return null
  return "${host.lowercase()}/$cleanPath"
}

@Internal
@VisibleForTesting
fun parseHostAndPath(url: String): Pair<String, String>? {
  val schemeMatch = SCHEME_HEAD.find(url)
  if (schemeMatch != null && schemeMatch.range.first == 0) {
    val authStart = schemeMatch.range.last + 1
    val slash = url.indexOf('/', authStart).takeIf { it >= 0 } ?: return null
    val authority = url.substring(authStart, slash).substringAfterLast('@').substringBefore(':')
    val path = url.substring(slash + 1)
    return authority to path
  }
  val scp = SCP_LIKE.matchEntire(url) ?: return null
  return scp.groupValues[1] to scp.groupValues[2]
}
