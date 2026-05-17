// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ide

import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class OpenProjectByGitUrlJbProtocolCommandTest {

  @JvmField
  @Rule
  val temp: TemporaryFolder = TemporaryFolder()

  // --- parseRemoteUrlsFromConfig ---

  @Test
  fun `parser - empty file returns no remotes`() {
    val config = writeConfig("")
    assertThat(parseRemoteUrlsFromConfig(config)).isEmpty()
  }

  @Test
  fun `parser - config without remote sections returns no remotes`() {
    val config = writeConfig("""
      [core]
        repositoryformatversion = 0
      [branch "main"]
        remote = origin
    """.trimIndent())
    assertThat(parseRemoteUrlsFromConfig(config)).isEmpty()
  }

  @Test
  fun `parser - single origin remote is returned`() {
    val config = writeConfig("""
      [remote "origin"]
        url = https://github.com/Foo/Bar.git
        fetch = +refs/heads/*:refs/remotes/origin/*
    """.trimIndent())
    assertThat(parseRemoteUrlsFromConfig(config))
      .containsExactly(GitRemote("origin", "https://github.com/Foo/Bar.git"))
  }

  @Test
  fun `parser - multiple remotes are returned in declared order`() {
    val config = writeConfig("""
      [remote "origin"]
        url = git@github.com:user/fork.git
      [remote "upstream"]
        url = https://github.com/Foo/Bar.git
    """.trimIndent())
    assertThat(parseRemoteUrlsFromConfig(config)).containsExactly(
      GitRemote("origin", "git@github.com:user/fork.git"),
      GitRemote("upstream", "https://github.com/Foo/Bar.git"),
    )
  }

  @Test
  fun `parser - quoted url value is unquoted`() {
    val config = writeConfig("""
      [remote "origin"]
        url = "https://github.com/Foo/Bar.git"
    """.trimIndent())
    assertThat(parseRemoteUrlsFromConfig(config))
      .containsExactly(GitRemote("origin", "https://github.com/Foo/Bar.git"))
  }

  @Test
  fun `parser - hash and semicolon comments are stripped`() {
    val config = writeConfig("""
      # full-line hash comment
      ; full-line semicolon comment
      [remote "origin"]   # inline header comment
        url = https://github.com/Foo/Bar.git  ; inline value comment
    """.trimIndent())
    assertThat(parseRemoteUrlsFromConfig(config))
      .containsExactly(GitRemote("origin", "https://github.com/Foo/Bar.git"))
  }

  @Test
  fun `parser - section header subsection name is case-sensitive but header keyword is not`() {
    val config = writeConfig("""
      [Remote "Origin"]
        url = https://github.com/Foo/Bar.git
    """.trimIndent())
    assertThat(parseRemoteUrlsFromConfig(config))
      .containsExactly(GitRemote("Origin", "https://github.com/Foo/Bar.git"))
  }

  @Test
  fun `parser - non-url keys in remote section are ignored`() {
    val config = writeConfig("""
      [remote "origin"]
        fetch = +refs/heads/*:refs/remotes/origin/*
        pushurl = https://other.example.com/Foo/Bar.git
        url = https://github.com/Foo/Bar.git
    """.trimIndent())
    assertThat(parseRemoteUrlsFromConfig(config))
      .containsExactly(GitRemote("origin", "https://github.com/Foo/Bar.git"))
  }

  @Test
  fun `parser - lines outside any section are ignored`() {
    val config = writeConfig("""
      url = https://stray.example.com/repo.git
      [remote "origin"]
        url = https://github.com/Foo/Bar.git
    """.trimIndent())
    assertThat(parseRemoteUrlsFromConfig(config))
      .containsExactly(GitRemote("origin", "https://github.com/Foo/Bar.git"))
  }

  @Test
  fun `parser - malformed lines without equals are skipped`() {
    val config = writeConfig("""
      [remote "origin"]
        this line has no equals
        url = https://github.com/Foo/Bar.git
        also no equals here
    """.trimIndent())
    assertThat(parseRemoteUrlsFromConfig(config))
      .containsExactly(GitRemote("origin", "https://github.com/Foo/Bar.git"))
  }

  // --- parseHostAndPath ---

  @Test
  fun `parseHostAndPath - https url`() {
    assertThat(parseHostAndPath("https://github.com/Foo/Bar.git"))
      .isEqualTo("github.com" to "Foo/Bar.git")
  }

  @Test
  fun `parseHostAndPath - https url with userinfo and port`() {
    assertThat(parseHostAndPath("https://alice@github.com:443/Foo/Bar.git"))
      .isEqualTo("github.com" to "Foo/Bar.git")
  }

  @Test
  fun `parseHostAndPath - ssh url`() {
    assertThat(parseHostAndPath("ssh://git@github.com/Foo/Bar.git"))
      .isEqualTo("github.com" to "Foo/Bar.git")
  }

  @Test
  fun `parseHostAndPath - mixed-case scheme is accepted`() {
    assertThat(parseHostAndPath("HTTPS://github.com/Foo/Bar.git"))
      .isEqualTo("github.com" to "Foo/Bar.git")
  }

  @Test
  fun `parseHostAndPath - scheme with plus and dot characters`() {
    assertThat(parseHostAndPath("git+ssh://git@example.com/Foo/Bar"))
      .isEqualTo("example.com" to "Foo/Bar")
  }

  @Test
  fun `parseHostAndPath - scp-like form`() {
    assertThat(parseHostAndPath("git@github.com:Foo/Bar.git"))
      .isEqualTo("github.com" to "Foo/Bar.git")
  }

  @Test
  fun `parseHostAndPath - scp-like without user`() {
    assertThat(parseHostAndPath("github.com:Foo/Bar.git"))
      .isEqualTo("github.com" to "Foo/Bar.git")
  }

  @Test
  fun `parseHostAndPath - scheme without slash after host returns null`() {
    // Used to allocate the entire post-scheme remainder via `groupValues[2]` before discovering there
    // was no slash; with the indexOf-based walk no large substring is allocated.
    assertThat(parseHostAndPath("https://github.com-no-slash-here")).isNull()
  }

  @Test
  fun `parseHostAndPath - pathologically long input without scheme is rejected without allocation`() {
    val payload = "a".repeat(100_000)
    assertThat(parseHostAndPath(payload)).isNull()
  }

  @Test
  fun `parseHostAndPath - pathologically long input after scheme without slash returns null`() {
    val payload = "https://" + "a".repeat(100_000)
    assertThat(parseHostAndPath(payload)).isNull()
  }

  @Test
  fun `parseHostAndPath - unrecognized input returns null`() {
    assertThat(parseHostAndPath("just-some-text")).isNull()
    assertThat(parseHostAndPath("")).isNull()
  }

  // --- readGitRemoteUrls (.git layout resolution) ---

  @Test
  fun `layout - dot-git directory - config is read directly`() {
    val project = temp.newFolder("repo").toPath()
    val gitDir = (project / ".git").also { it.createDirectories() }
    (gitDir / "config").writeText("""
      [remote "origin"]
        url = https://github.com/Foo/Bar.git
    """.trimIndent())

    assertThat(readGitRemoteUrls(project))
      .containsExactly(GitRemote("origin", "https://github.com/Foo/Bar.git"))
  }

  @Test
  fun `layout - submodule - dot-git is a file pointing at a sibling gitdir`() {
    val parent = temp.newFolder("parent").toPath()
    val submoduleDir = parent / "submodule"
    submoduleDir.createDirectories()
    val gitDir = (parent / ".git" / "modules" / "submodule").also { it.createDirectories() }
    (gitDir / "config").writeText("""
      [remote "origin"]
        url = https://github.com/Foo/Sub.git
    """.trimIndent())
    (submoduleDir / ".git").writeText("gitdir: ../.git/modules/submodule\n")

    assertThat(readGitRemoteUrls(submoduleDir))
      .containsExactly(GitRemote("origin", "https://github.com/Foo/Sub.git"))
  }

  @Test
  fun `layout - submodule with absolute gitdir pointer`() {
    val project = temp.newFolder("repo").toPath()
    val gitDir = (temp.newFolder("separateGitDir").toPath()).also { it.createDirectories() }
    (gitDir / "config").writeText("""
      [remote "origin"]
        url = https://github.com/Foo/Bar.git
    """.trimIndent())
    (project / ".git").writeText("gitdir: ${gitDir.toAbsolutePath()}\n")

    assertThat(readGitRemoteUrls(project))
      .containsExactly(GitRemote("origin", "https://github.com/Foo/Bar.git"))
  }

  @Test
  fun `layout - worktree - commondir redirects config lookup to the main repository`() {
    val main = temp.newFolder("main").toPath()
    val mainGitDir = (main / ".git").also { it.createDirectories() }
    (mainGitDir / "config").writeText("""
      [remote "origin"]
        url = https://github.com/Foo/Bar.git
      [remote "upstream"]
        url = https://github.com/Foo/UpstreamBar.git
    """.trimIndent())

    val worktreeGitDir = (mainGitDir / "worktrees" / "wt1").also { it.createDirectories() }
    // commondir points back to the main .git directory; its own config has no remotes.
    (worktreeGitDir / "commondir").writeText("../..\n")
    (worktreeGitDir / "config").writeText("[core]\n  bare = false\n")

    val worktree = temp.newFolder("wt1").toPath()
    (worktree / ".git").writeText("gitdir: ${worktreeGitDir.toAbsolutePath()}\n")

    assertThat(readGitRemoteUrls(worktree)).containsExactly(
      GitRemote("origin", "https://github.com/Foo/Bar.git"),
      GitRemote("upstream", "https://github.com/Foo/UpstreamBar.git"),
    )
  }

  @Test
  fun `layout - dot-git missing entirely returns empty list`() {
    val project = temp.newFolder("notARepo").toPath()
    assertThat(readGitRemoteUrls(project)).isEmpty()
  }

  @Test
  fun `layout - walks up from subdirectory to find dot-git in ancestor`() {
    val project = temp.newFolder("repo").toPath()
    val gitDir = (project / ".git").also { it.createDirectories() }
    (gitDir / "config").writeText("""
      [remote "origin"]
        url = https://github.com/Foo/Bar.git
    """.trimIndent())
    val nested = (project / "src" / "main" / "kotlin").also { it.createDirectories() }

    assertThat(readGitRemoteUrls(nested))
      .containsExactly(GitRemote("origin", "https://github.com/Foo/Bar.git"))
  }

  @Test
  fun `layout - dot-git file with broken gitdir pointer returns empty list`() {
    val project = temp.newFolder("repo").toPath()
    (project / ".git").writeText("gitdir: ./does/not/exist\n")

    assertThat(readGitRemoteUrls(project)).isEmpty()
  }

  private fun writeConfig(content: String): Path {
    val file = temp.newFile("config").toPath()
    Files.writeString(file, content)
    return file
  }

  private operator fun Path.div(name: String): Path = resolve(name)
}
