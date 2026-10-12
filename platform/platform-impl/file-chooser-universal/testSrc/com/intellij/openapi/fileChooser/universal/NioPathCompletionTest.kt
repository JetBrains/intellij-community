// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.universal

import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileChooser.universal.NioPathCompletion.Edit
import com.intellij.openapi.fileChooser.universal.NioPathCompletion.State
import com.intellij.openapi.fileChooser.universal.NioPathCompletion.Trigger
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.intellij.platform.util.progress.reportRawProgress
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.time.Duration.Companion.seconds

@TestApplication
class NioPathCompletionTest {
  @TempDir
  lateinit var root: Path

  private lateinit var rootText: String

  @BeforeEach
  fun createChildren() {
    root.resolve("alpha").createDirectories().resolve("inner").createDirectories()
    root.resolve("beta").createDirectories()
    root.resolve("bravo.txt").createFile()
    rootText = root.toString() + root.fileSystem.separator
  }

  @Test
  fun `typing filters the listing and the shortcut lists the directory again`() = runCompletionTest {
    completion.update(rootText, Trigger.SHORTCUT)
    assertThat(suggestionNames(awaitSuggestions())).containsExactly("alpha", "beta", "bravo.txt")
    root.resolve("bob").createFile()

    completion.update(rootText + "B", Trigger.TYPING)
    awaitIdle()
    assertThat(suggestionNames(state)).describedAs("the listing before the new file").containsExactly("beta", "bravo.txt")

    completion.update(rootText + "B", Trigger.SHORTCUT)
    awaitIdle()
    assertThat(suggestionNames(state)).containsExactly("beta", "bob", "bravo.txt")
  }

  @Test
  fun `relative text has no suggestions`() = runCompletionTest {
    completion.update("", Trigger.SHORTCUT)
    assertThat(state).isEqualTo(State.Hidden)
    completion.update("alpha", Trigger.SHORTCUT)
    awaitIdle()
    assertThat(state).isEqualTo(State.Hidden)
  }

  @Test
  fun `typed separator lists the typed directory`() = runCompletionTest {
    val separator = root.fileSystem.separator
    completion.update(rootText + "alpha", Trigger.TYPING)
    assertThat(suggestionNames(awaitSuggestions())).describedAs("the full name").containsExactly("alpha")

    completion.update(rootText + "alpha" + separator, Trigger.TYPING)
    assertThat(suggestionNames(awaitSuggestions())).containsExactly("inner")
  }

  @Test
  fun `missing directory shows the no-results text`() = runCompletionTest {
    completion.update(rootText + "missing" + root.fileSystem.separator, Trigger.TYPING)
    assertThat(awaitState { it !is State.Loading }).isEqualTo(State.NoResults)
  }

  @Test
  fun `no match shows the no-results text`() = runCompletionTest {
    completion.update(rootText, Trigger.SHORTCUT)
    awaitSuggestions()

    completion.update(rootText + "zzz", Trigger.TYPING)
    assertThat(state).isEqualTo(State.NoResults)

    completion.update(rootText + "zzz", Trigger.SHORTCUT)
    assertThat(state).isEqualTo(State.NoResults)
  }

  @Test
  fun `chosen directory descends and chosen file ends the completion`() = runCompletionTest {
    completion.update(rootText, Trigger.SHORTCUT)
    awaitSuggestions()

    val separator = rootText.last()
    assertThat(completion.complete(rootText + "al", alphaEntry))
      .isEqualTo(Edit(rootText.length, "alpha$separator", descends = true))
    assertThat(completion.complete(rootText + "br", fileEntry("bravo.txt")))
      .isEqualTo(Edit(rootText.length, "bravo.txt", descends = false))
    assertThat(state).isEqualTo(State.Hidden)
  }

  @Test
  fun `cancel drops the running listing`() {
    val listingStarted = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    val prepare: suspend () -> Unit = {
      listingStarted.complete(Unit)
      release.await()
    }
    runCompletionTest(prepare = prepare) {
      completion.update(rootText, Trigger.SHORTCUT)
      listingStarted.await()

      completion.cancel()
      release.complete(Unit)
      awaitIdle()
      assertThat(state).isEqualTo(State.Hidden)
    }
  }

  @Test
  fun `suggestions apply only to a base in the listed directory`() = runCompletionTest {
    val separator = root.fileSystem.separator
    assertThat(completion.hasSuggestionsFor(rootText)).describedAs("no listing").isFalse()

    completion.update(rootText, Trigger.SHORTCUT)
    awaitSuggestions()
    assertThat(completion.hasSuggestionsFor(rootText + "b")).isTrue()
    assertThat(completion.hasSuggestionsFor(rootText + "alpha" + separator)).isFalse()

    completion.cancel()
    assertThat(completion.hasSuggestionsFor(rootText)).describedAs("hidden suggestions").isFalse()
  }

  @Test
  fun `dismiss stops the typing until the shortcut`() = runCompletionTest {
    completion.update(rootText, Trigger.SHORTCUT)
    awaitSuggestions()

    completion.dismiss()
    completion.update(rootText + "a", Trigger.TYPING)
    assertThat(state).isEqualTo(State.Hidden)

    completion.update(rootText + "a", Trigger.SHORTCUT)
    assertThat(suggestionNames(state)).containsExactly("alpha")
  }

  @Test
  fun `directory chooser suggests only the directories`() = runCompletionTest(chooseFiles = false) {
    completion.update(rootText, Trigger.SHORTCUT)
    assertThat(suggestionNames(awaitSuggestions())).containsExactly("alpha", "beta")
  }

  @Test
  fun `shown hidden files list the directory again`() {
    var showHidden = false
    Jimfs.newFileSystem(Configuration.unix()).use { fileSystem ->
      val directory = fileSystem.getPath("/work").createDirectories()
      directory.resolve(".hidden").createFile()
      directory.resolve("shown").createFile()
      runCompletionTest(pathParser = absoluteParser(fileSystem), showHidden = { showHidden }) {
        completion.update("/work/", Trigger.SHORTCUT)
        assertThat(suggestionNames(awaitSuggestions())).containsExactly("shown")

        showHidden = true
        completion.update("/work/", Trigger.TYPING)
        assertThat(suggestionNames(awaitSuggestions())).containsExactly(".hidden", "shown")
      }
    }
  }

  @Test
  fun `listing of a left directory does not change the suggestions`() {
    val firstStarted = CompletableDeferred<Unit>()
    val releaseFirst = CompletableDeferred<Unit>()
    val preparations = AtomicInteger()
    val prepare: suspend () -> Unit = {
      if (preparations.getAndIncrement() == 0) {
        firstStarted.complete(Unit)
        withContext(NonCancellable) { releaseFirst.await() }
      }
    }
    runCompletionTest(prepare = prepare) {
      val separator = root.fileSystem.separator
      completion.update(rootText, Trigger.SHORTCUT)
      firstStarted.await()

      completion.update(rootText + "alpha" + separator, Trigger.TYPING)
      assertThat(suggestionNames(awaitSuggestions())).containsExactly("inner")

      releaseFirst.complete(Unit)
      awaitIdle()
      assertThat(suggestionNames(state)).containsExactly("inner")
    }
  }

  @Test
  fun `home directory expands the tilde`() = runCompletionTest(home = { root.toString() }) {
    completion.update("~/", Trigger.SHORTCUT)
    assertThat(suggestionNames(awaitSuggestions())).containsExactly("alpha", "beta", "bravo.txt")
    assertThat(completion.complete("~/al", alphaEntry))
      .isEqualTo(Edit("~/".length, "alpha/", descends = true))
  }

  @Test
  fun `home directory is resolved once`() {
    val homeCalls = AtomicInteger()
    runCompletionTest(home = { homeCalls.incrementAndGet(); root.toString() }) {
      val separator = root.fileSystem.separator
      completion.update("~$separator", Trigger.SHORTCUT)
      awaitSuggestions()
      completion.update("~${separator}alpha$separator", Trigger.TYPING)
      assertThat(suggestionNames(awaitSuggestions())).containsExactly("inner")

      completion.update("~${separator}alpha$separator", Trigger.SHORTCUT)
      awaitSuggestions()
      assertThat(homeCalls.get()).isEqualTo(1)
    }
  }

  @Test
  fun `base out of the home cancels the home lookup`() {
    val lookupStarted = CompletableDeferred<Unit>()
    val lookupCancelled = CompletableDeferred<Unit>()
    val home: suspend () -> String? = {
      lookupStarted.complete(Unit)
      try {
        awaitCancellation()
      }
      finally {
        lookupCancelled.complete(Unit)
      }
    }
    runCompletionTest(home = home) {
      completion.update("~/", Trigger.SHORTCUT)
      withTimeout(10.seconds) { lookupStarted.await() }
      completion.update(rootText, Trigger.SHORTCUT)
      assertThat(suggestionNames(awaitSuggestions())).containsExactly("alpha", "beta", "bravo.txt")
      withTimeout(10.seconds) { lookupCancelled.await() }
    }
  }

  @Test
  fun `unknown home directory shows the no-results text`() = runCompletionTest(home = { null }) {
    completion.update("~/", Trigger.TYPING)
    assertThat(awaitState { it !is State.Loading }).isEqualTo(State.NoResults)
  }

  @Test
  fun `expanded text has the home directory in place of the tilde`() = runCompletionTest(home = { root.toString() }) {
    val separator = root.fileSystem.separator
    assertThat(completion.expandHome("~")).isEqualTo(root.toString())
    assertThat(completion.expandHome("~${separator}alpha")).isEqualTo(root.resolve("alpha").toString())
    assertThat(completion.expandHome(rootText)).isEqualTo(rootText)
    assertThat(completion.expandHome("~bob")).describedAs("the home of another user").isEqualTo("~bob")
  }

  @Test
  fun `posix backslash after the tilde does not expand`() {
    Jimfs.newFileSystem(Configuration.unix()).use { fileSystem ->
      runCompletionTest(pathParser = absoluteParser(fileSystem), home = { "/home/me" }) {
        assertThat(completion.expandHome("~/projects/~/project")).isEqualTo("/home/me/projects/~/project")
        assertThat(completion.expandHome("~\\project")).describedAs("a file name with a backslash").isEqualTo("~\\project")
      }
    }
  }

  @Test
  fun `windows home expands with both separators`() {
    Jimfs.newFileSystem(Configuration.windows()).use { fileSystem ->
      runCompletionTest(pathParser = absoluteParser(fileSystem), home = { "C:\\Users\\me" }) {
        assertThat(completion.expandHome("~\\projects")).isEqualTo("C:\\Users\\me\\projects")
        assertThat(completion.expandHome("~/projects")).isEqualTo("C:\\Users\\me/projects")
        assertThat(completion.expandHome("~me\\projects")).describedAs("the home of another user").isEqualTo("~me\\projects")
      }
    }
  }

  @Test
  fun `expansion keeps the tilde when the home directory is unknown`() = runCompletionTest(home = { null }) {
    assertThat(completion.expandHome("~/alpha")).isEqualTo("~/alpha")
  }

  @Test
  fun `failed home directory lookup reports the error`() = runCompletionTest(home = { throw IllegalStateException("No home") }) {
    completion.update("~/", Trigger.SHORTCUT)
    val failed = awaitState { it !is State.Loading } as State.Failed
    assertThat(failed.error).hasMessage("No home")
  }

  @Test
  fun `slow listing shows the loading state`() {
    val release = CompletableDeferred<Unit>()
    runCompletionTest(prepare = { release.await() }) {
      completion.update(rootText, Trigger.SHORTCUT)
      assertThat(awaitState { it is State.Loading }).isEqualTo(State.Loading(progressText = null))

      release.complete(Unit)
      assertThat(suggestionNames(awaitSuggestions())).containsExactly("alpha", "beta", "bravo.txt")
    }
  }

  @Test
  fun `reported progress shows in the loading state`() {
    val progressShown = CompletableDeferred<Unit>()
    val prepare: suspend () -> Unit = {
      reportRawProgress { reporter ->
        reporter.text("Installing")
        progressShown.await()
      }
    }
    runCompletionTest(prepare = prepare) {
      completion.update(rootText, Trigger.SHORTCUT)
      awaitState { it == State.Loading("Installing") }
      progressShown.complete(Unit)
      assertThat(suggestionNames(awaitSuggestions())).containsExactly("alpha", "beta", "bravo.txt")
    }
  }

  @Test
  fun `failed preparation reports the error and waits for the shortcut`() {
    val prepareCalls = AtomicInteger()
    val prepare: suspend () -> Unit = {
      if (prepareCalls.incrementAndGet() == 1) throw IllegalStateException("No connection")
    }
    runCompletionTest(prepare = prepare) {
      completion.update(rootText, Trigger.SHORTCUT)
      val failed = awaitState { it !is State.Loading } as State.Failed
      assertThat(failed.error).hasMessage("No connection")

      completion.update(rootText + "a", Trigger.TYPING)
      assertThat(state).describedAs("an edit hides the failure").isEqualTo(State.Hidden)
      assertThat(prepareCalls.get()).isEqualTo(1)

      completion.update(rootText, Trigger.SHORTCUT)
      assertThat(suggestionNames(awaitSuggestions())).containsExactly("alpha", "beta", "bravo.txt")
    }
  }

  @Test
  fun `posix file name can contain a backslash`() {
    Jimfs.newFileSystem(Configuration.unix()).use { fileSystem ->
      val directory = fileSystem.getPath("/work").createDirectories()
      directory.resolve("a\\b.txt").createFile()
      runCompletionTest(pathParser = absoluteParser(fileSystem)) {
        completion.update("/work/a\\", Trigger.SHORTCUT)
        assertThat(suggestionNames(awaitSuggestions())).containsExactly("a\\b.txt")
        assertThat(completion.complete("/work/a\\", fileEntry("a\\b.txt")))
          .isEqualTo(Edit("/work/".length, "a\\b.txt", descends = false))
      }
    }
  }

  @Test
  fun `windows directory gets the typed separator`() {
    Jimfs.newFileSystem(Configuration.windows()).use { fileSystem ->
      fileSystem.getPath("C:\\work\\alpha").createDirectories()
      runCompletionTest(pathParser = absoluteParser(fileSystem)) {
        completion.update("C:/work/", Trigger.SHORTCUT)
        assertThat(suggestionNames(awaitSuggestions())).containsExactly("alpha")
        assertThat(completion.complete("C:/work/al", alphaEntry))
          .isEqualTo(Edit("C:/work/".length, "alpha/", descends = true))

        completion.update("C:\\work\\", Trigger.SHORTCUT)
        awaitSuggestions()
        assertThat(completion.complete("C:\\work\\al", alphaEntry))
          .isEqualTo(Edit("C:\\work\\".length, "alpha\\", descends = true))
      }
    }
  }

  @Test
  fun `parser decides the directory text`() = runCompletionTest {
    val separator = root.fileSystem.separator
    assertThat(completion.directoryTextOf(rootText + "al")).isEqualTo(rootText)
    assertThat(completion.directoryTextOf(rootText)).isEqualTo(rootText)
    assertThat(completion.directoryTextOf("${rootText}alpha${separator}in")).isEqualTo("${rootText}alpha$separator")
    assertThat(completion.directoryTextOf("alpha")).isNull()
    assertThat(completion.directoryTextOf("alpha${separator}in")).describedAs("a relative path").isNull()
  }

  @Test
  fun `posix parser keeps a backslash in the name`() {
    Jimfs.newFileSystem(Configuration.unix()).use { fileSystem ->
      runCompletionTest(pathParser = absoluteParser(fileSystem)) {
        assertThat(completion.directoryTextOf("/tmp/a\\b")).isEqualTo("/tmp/")
      }
    }
  }

  @Test
  fun `tilde path is a directory path only in the home`() = runCompletionTest(home = { root.toString() }) {
    val separator = root.fileSystem.separator
    completion.update("~$separator", Trigger.SHORTCUT)
    awaitSuggestions()
    assertThat(completion.directoryTextOf("~${separator}alpha${separator}in")).isEqualTo("~${separator}alpha$separator")
    assertThat(completion.directoryTextOf("~$separator")).isEqualTo("~$separator")
    assertThat(completion.directoryTextOf("~")).describedAs("the parent of the home").isNull()
    assertThat(completion.directoryTextOf("~src${separator}a")).describedAs("another user").isNull()
  }

  @Test
  fun `drive letter alone is not a directory text`() {
    Jimfs.newFileSystem(Configuration.windows()).use { fileSystem ->
      runCompletionTest(pathParser = absoluteParser(fileSystem)) {
        assertThat(completion.directoryTextOf("C:")).isNull()
        assertThat(completion.directoryTextOf("C:\\")).isEqualTo("C:\\")
        assertThat(completion.directoryTextOf("C:/Users/a")).isEqualTo("C:/Users/")
      }
    }
  }

  @Test
  fun `unc share is the first directory text`() {
    Jimfs.newFileSystem(Configuration.windows()).use { fileSystem ->
      runCompletionTest(pathParser = absoluteParser(fileSystem)) {
        assertThat(completion.directoryTextOf("\\\\wsl.localhost\\Ubuntu\\ho")).isEqualTo("\\\\wsl.localhost\\Ubuntu\\")
        assertThat(completion.directoryTextOf("\\\\wsl.localhost\\Ubuntu")).describedAs("the share without a separator").isNull()
      }
    }
  }

  private val alphaEntry = NioFileChooserUtil.DirectoryEntry("alpha", isDirectory = true, icon = null)

  private fun fileEntry(name: String) = NioFileChooserUtil.DirectoryEntry(name, isDirectory = false, icon = null)

  private fun parseAbsolute(text: String): Path? = absoluteParser(FileSystems.getDefault())(text)

  private fun absoluteParser(fileSystem: FileSystem): (String) -> Path? =
    { text -> runCatching { fileSystem.getPath(text) }.getOrNull()?.takeIf { it.isAbsolute } }

  private class CompletionFixture(
    private val scope: CoroutineScope,
    pathParser: (String) -> Path?,
    home: suspend () -> String?,
    prepare: suspend () -> Unit,
    showHidden: () -> Boolean,
    chooseFiles: Boolean,
  ) {
    private val states = Channel<State>(Channel.UNLIMITED)

    /** The last state that the completion reported. */
    var state: State = State.Hidden
      private set

    val completion = NioPathCompletion(
      scope = scope,
      chooseFiles = chooseFiles,
      chooseArchives = false,
      showHidden = showHidden,
      pathParser = pathParser,
      prepareCompletion = prepare,
      homeDirectory = home,
      onStateChanged = {
        state = it
        states.trySend(it)
      },
    )

    suspend fun awaitState(predicate: (State) -> Boolean): State =
      withTimeout(10.seconds) {
        var state = states.receive()
        while (!predicate(state)) state = states.receive()
        state
      }

    suspend fun awaitSuggestions(): State = awaitState { it is State.Suggestions }

    /** Waits until the coroutines of the completion end, so no later state can come. */
    suspend fun awaitIdle() {
      val job = scope.coroutineContext.job
      while (true) {
        val running = job.children.toList()
        if (running.isEmpty()) return
        running.joinAll()
      }
    }

    fun suggestionNames(state: State): List<String> =
      (state as State.Suggestions).matching.map { it.name }
  }

  private fun runCompletionTest(
    pathParser: (String) -> Path? = ::parseAbsolute,
    home: suspend () -> String? = { null },
    prepare: suspend () -> Unit = {},
    showHidden: () -> Boolean = { false },
    chooseFiles: Boolean = true,
    test: suspend CompletionFixture.() -> Unit,
  ) = timeoutRunBlocking {
    val job = Job(coroutineContext.job)
    try {
      withContext(Dispatchers.EDT) {
        CompletionFixture(CoroutineScope(Dispatchers.Default + job), pathParser, home, prepare, showHidden, chooseFiles).test()
      }
    }
    finally {
      job.cancel()
    }
  }
}
