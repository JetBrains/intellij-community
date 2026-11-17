// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.productMigration

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.icons.AllIcons
import com.intellij.ide.ui.laf.setEarlyNewUiDarkLaF
import com.intellij.idea.ApplicationStartArguments
import com.intellij.idea.WellKnownCommand
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.updateSettings.impl.MigrationKillSwitch
import com.intellij.ui.AppUIUtil
import com.intellij.ui.ColorUtil
import com.intellij.ui.ComponentUtil
import com.intellij.ui.JBColor
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.io.URLUtil
import com.intellij.util.io.awaitExit
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Frame
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.KeyEvent
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.KeyStroke

private val LOG = logger<MigrationRedirect>()

private const val OPEN_TIMEOUT_SECONDS = 30L
private const val CONTENT_WIDTH = 420
private const val CONTENT_INSET = 24
private const val LOGO_SIZE = 32
// the size step of `JBFont.h3`
private const val TITLE_SIZE_INCREASE = 3f
private const val WAIT_OPTION = "--wait"
private val GUI_COMMANDS = setOf("diff", "merge")

/**
 * What the migration does with a redirected start. A non-null [content] shows the redirect window.
 * [launcher] is the macOS launcher of the Unified IDE for a start that [waits].
 */
@ApiStatus.Internal
class MigrationRedirect(val state: MigrationState, val waits: Boolean, val content: MigrationRedirectContent?, val launcher: Path?)

/** What the user selects in the redirect window. */
@ApiStatus.Internal
enum class MigrationRedirectChoice {
  /** The primary button. The window gives it also when it cannot show, because the migration is done. */
  LAUNCH,

  /** Esc or the close button. This IDE exits, and the window shows again at the next start. */
  EXIT,
}

/** The kind of a redirected start. [waits] is `true` when the caller of this IDE waits for the result, so the start shows no window. */
@ApiStatus.Internal
enum class MigrationRedirectStart(val waits: Boolean) {
  /** A start with no argument, or with options, files, projects or a URL, without the `--wait` option. */
  OPEN(waits = false),

  /** A start with the `--wait` option, for example from `git` with this IDE as its editor. */
  OPEN_AND_WAIT(waits = true),

  /** The `diff` or the `merge` command, for example from `git difftool` or `git mergetool`. */
  GUI_COMMAND(waits = true),
}

/**
 * Returns the kind of the start with [args], or `null` when the migration does not redirect it.
 * A known command other than `diff` and `merge` stays in this IDE, because scripts and CI call it.
 * The first argument of a redirected start is an option, a URL, or an existing file or directory in [currentDir].
 * Any other first argument can be the command of a plugin, so this IDE runs it.
 */
@ApiStatus.Internal
fun migrationRedirectStart(args: List<String>, currentDir: Path = Path.of("").toAbsolutePath()): MigrationRedirectStart? {
  val positional = ApplicationStartArguments.stripKnownArguments(migrationRedirectArguments(args, currentDir))
  val first = positional.firstOrNull() ?: return MigrationRedirectStart.OPEN
  if (WellKnownCommand.getCommandFor(positional) != null) {
    return if (first in GUI_COMMANDS) MigrationRedirectStart.GUI_COMMAND else null
  }
  if (first.startsWith("-") || first.contains(URLUtil.SCHEME_SEPARATOR) || existingPath(first, currentDir) != null) {
    return if (WAIT_OPTION in positional) MigrationRedirectStart.OPEN_AND_WAIT else MigrationRedirectStart.OPEN
  }
  LOG.info("the first argument is not a file, the usual start continues: ${first}")
  return null
}

/**
 * The arguments of this start for the Unified IDE.
 * The `-Dkey=value` arguments are meant for this IDE, as in `Main.preprocessArgs`, so they go away.
 * An existing relative path becomes absolute in [currentDir], because the Unified IDE has its own working directory.
 * The first argument of a known command stays.
 */
@ApiStatus.Internal
fun migrationRedirectArguments(args: List<String>, currentDir: Path = Path.of("").toAbsolutePath()): List<String> {
  val arguments = args.filterNot { it.startsWith("-D") && it.contains('=') }
  val positional = ApplicationStartArguments.stripKnownArguments(arguments)
  val commandIndex = if (WellKnownCommand.getCommandFor(positional) != null) arguments.indexOf(positional.first()) else -1
  return arguments.mapIndexed { index, argument ->
    if (index == commandIndex || argument.startsWith("-") || argument.contains(URLUtil.SCHEME_SEPARATOR)) argument
    else existingPath(argument, currentDir)?.toString() ?: argument
  }
}

/** Returns the absolute path of [argument] in [currentDir] when the file exists, else `null`. */
private fun existingPath(argument: String, currentDir: Path): Path? {
  val path = try {
    currentDir.resolve(argument).normalize()
  }
  catch (_: InvalidPathException) {
    return null
  }
  return path.takeIf { Files.exists(it) }
}

/**
 * Returns what the migration does with a start of the kind [start], or `null` for the usual start.
 * Only a valid state with the `moved` status redirects. A `null` [start] reads no file.
 * Only an [MigrationRedirectStart.OPEN] start reads the content copy, and a missing or invalid copy shows no window.
 * A failure is logged and gives `null`, so that the usual start continues.
 * When [isMigrationDisabled] is true, the result is `null`, and no file is read, checked or deleted.
 */
@ApiStatus.Internal
fun loadMigrationRedirect(
  start: MigrationRedirectStart?,
  stateFile: Path = migrationStateFile(),
  isMigrationDisabled: Boolean = MigrationKillSwitch.isActive(),
): MigrationRedirect? {
  if (start == null || isMigrationDisabled) {
    return null
  }
  try {
    val unchecked = readUncheckedMigrationState(stateFile) ?: return null
    if (unchecked.status != MigrationStatus.MOVED) {
      return null
    }
    val info = readInstalledProductInfo(unchecked.app)
    val state = validateMigrationState(unchecked, stateFile, info) ?: return null
    if (start.waits) {
      LOG.info("a start that waits for the result, ${state.app} starts with no window")
      return MigrationRedirect(state, waits = true, content = null, launcher = info?.macLauncher)
    }
    val content = readMigrationRedirectContent(migrationRedirectContentFile(stateFile))
    if (content == null) {
      LOG.info("no valid content copy, ${state.app} starts with no window")
    }
    return MigrationRedirect(state, waits = false, content, launcher = null)
  }
  catch (e: Throwable) {
    rethrowControlFlowException(e)
    LOG.warn("the migration redirect check failed, the usual start continues", e)
    return null
  }
}

/**
 * The command that starts the Unified IDE with the `redirect` reason and the arguments of this start.
 * Without [launcher], it starts a new instance with `open`. With [launcher], it runs the launcher of the Unified IDE.
 */
@ApiStatus.Internal
fun migrationRedirectCommand(app: Path, sourceProduct: String, arguments: List<String>, launcher: Path? = null): List<String> {
  val launchArguments = migrationLaunchArguments(MigrationLaunchReason.REDIRECT, sourceProduct, arguments)
  return if (launcher == null) macOpenCommand(app, launchArguments) else listOf(launcher.toString()) + launchArguments
}

/**
 * Starts the Unified IDE of [redirect] and returns the exit code for this process, or `null` when the start fails.
 * A start that does not wait uses `open` and gives `0`.
 * A start that waits runs the launcher as a child with the standard streams of this process, and gives the exit code of the child.
 * [onStart] runs after the start of the child.
 */
@ApiStatus.Internal
suspend fun launchMigrationRedirect(
  redirect: MigrationRedirect,
  sourceProduct: String,
  arguments: List<String>,
  onStart: suspend () -> Unit = {},
): Int? {
  val app = redirect.state.app
  if (!redirect.waits) {
    val process = startMigrationProcess(migrationRedirectCommand(app, sourceProduct, arguments)) ?: return null
    val isStarted = runInterruptible(Dispatchers.IO) { waitForMigrationOpen(process, OPEN_TIMEOUT_SECONDS) }
    return if (isStarted) 0 else null
  }

  val launcher = redirect.launcher ?: run {
    LOG.warn("no launcher in ${app}, the start that waits cannot be redirected")
    return null
  }
  val child = startMigrationProcess(migrationRedirectCommand(app, sourceProduct, arguments, launcher), inheritIO = true) ?: return null
  onStart()
  val exitCode = child.awaitExit()
  LOG.info("the Unified IDE exited with the code ${exitCode}")
  return exitCode
}

/**
 * The files and the URL that macOS gives to this start as Apple events, for example from Finder or a drop on the Dock icon.
 * [uri] is the last URL, because the IDE opens only one.
 */
@ApiStatus.Internal
class MigrationOpenRequests(val files: List<Path>, val uri: String?) {
  /** The forwarded arguments: the URL first, because the IDE reads a URL only in the first argument, then the absolute file paths. */
  fun toArguments(): List<String> = listOfNotNull(uri) + files.map { it.toAbsolutePath().toString() }
}

/**
 * Shows the redirect window and returns the choice of the user. The application does not exist yet.
 * [setLookAndFeel] sets the look and feel for the window, and its result is closed after the window.
 * A failure gives [MigrationRedirectChoice.LAUNCH], because the migration is done.
 * It also deletes the content copy, so that the next start does not fail in the same way.
 */
@ApiStatus.Internal
@RequiresEdt
fun showMigrationRedirect(
  content: MigrationRedirectContent,
  stateFile: Path = migrationStateFile(),
  setLookAndFeel: () -> AutoCloseable = ::setEarlyNewUiDarkLaF,
  showWindow: (MigrationRedirectContent, sourceLogo: Icon, targetLogo: Icon) -> MigrationRedirectChoice = ::showRedirectWindow,
): MigrationRedirectChoice {
  var lookAndFeel: AutoCloseable? = null
  val choice = try {
    lookAndFeel = setLookAndFeel()
    // the logo variant follows the real background of the look and feel
    val isDark = ColorUtil.isDark(UIUtil.getPanelBackground())
    val logoSize = JBUI.scale(LOGO_SIZE)
    val sourceLogo = migrationSvgIcon(content.sourceLogo, logoSize, logoSize, isDark)
    val targetLogo = migrationSvgIcon(content.targetLogo, logoSize, logoSize, isDark)
    LOG.info("the migration redirect window is shown")
    showWindow(content, sourceLogo, targetLogo)
  }
  catch (e: Throwable) {
    rethrowControlFlowException(e)
    LOG.warn("the migration redirect window cannot show, the content copy is deleted, the Unified IDE starts with no window", e)
    deleteLogged(migrationRedirectContentFile(stateFile))
    return MigrationRedirectChoice.LAUNCH
  }
  finally {
    lookAndFeel?.let(::closeLookAndFeel)
  }
  if (choice == MigrationRedirectChoice.EXIT) {
    LOG.info("the user closes the migration redirect window, this IDE exits")
  }
  return choice
}

private fun closeLookAndFeel(lookAndFeel: AutoCloseable) {
  try {
    lookAndFeel.close()
  }
  catch (e: Throwable) {
    rethrowControlFlowException(e)
    LOG.warn("cannot restore the look and feel after the migration redirect window", e)
  }
}

private fun showRedirectWindow(content: MigrationRedirectContent, sourceLogo: Icon, targetLogo: Icon): MigrationRedirectChoice {
  val window = MigrationRedirectWindow(content, sourceLogo, targetLogo)
  AppUIUtil.updateAppWindowIcon(window)
  window.isVisible = true
  return window.choice
}

/**
 * The redirect window after the migration. The server gives all texts and images. The client defines the actions.
 * It is a plain Swing window, because it shows before the application services exist.
 */
private class MigrationRedirectWindow(
  content: MigrationRedirectContent,
  sourceLogo: Icon,
  targetLogo: Icon,
) : JDialog(null as Frame?, content.texts.windowTitle, ModalityType.TOOLKIT_MODAL) {
  var choice: MigrationRedirectChoice = MigrationRedirectChoice.EXIT
    private set

  init {
    val texts = content.texts
    defaultCloseOperation = DISPOSE_ON_CLOSE

    val textFont = JBFont.label()
    val primary = plainTextButton(texts.primaryButton) { close(MigrationRedirectChoice.LAUNCH) }
    primary.font = textFont

    val bar = JPanel(BorderLayout()).apply {
      border = JBUI.Borders.compound(JBUI.Borders.customLineTop(JBColor.border()), JBUI.Borders.empty(12, 20))
      add(primary, BorderLayout.EAST)
    }

    val width = maxOf(JBUI.scale(CONTENT_WIDTH), bar.preferredSize.width - JBUI.scale(2 * CONTENT_INSET))
    val logos = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0)).apply {
      isOpaque = false
      add(JLabel(sourceLogo))
      add(JLabel(AllIcons.General.ArrowRight))
      add(JLabel(targetLogo))
    }
    val body = plainTextArea(texts.body, textFont, width).apply {
      foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
    }
    val message = JPanel(GridBagLayout()).apply {
      border = JBUI.Borders.empty(CONTENT_INSET, CONTENT_INSET, 20, CONTENT_INSET)
      val gridBag = GridBag().setDefaultAnchor(GridBagConstraints.WEST)
      add(logos, gridBag.nextLine().insetBottom(JBUI.scale(16)))
      add(plainTextArea(texts.title, textFont.biggerOn(TITLE_SIZE_INCREASE).asBold(), width), gridBag.nextLine().insetBottom(JBUI.scale(8)))
      add(body, gridBag.nextLine())
    }

    contentPane = JPanel(BorderLayout()).apply {
      add(message, BorderLayout.CENTER)
      add(bar, BorderLayout.SOUTH)
    }

    rootPane.defaultButton = primary
    rootPane.registerKeyboardAction({ close(MigrationRedirectChoice.EXIT) }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                                    JComponent.WHEN_IN_FOCUSED_WINDOW)
    // the getter creates the context; the protected AWT field of the same name stays null until then
    getAccessibleContext().accessibleDescription = texts.body
    // the native title bar follows the dark look and feel, not the macOS appearance
    ComponentUtil.decorateWindowHeader(rootPane)
    pack()
    setLocationRelativeTo(null)
  }

  private fun close(choice: MigrationRedirectChoice) {
    this.choice = choice
    dispose()
  }
}
