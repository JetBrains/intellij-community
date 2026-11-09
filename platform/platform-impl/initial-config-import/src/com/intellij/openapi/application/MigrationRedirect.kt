// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.icons.AllIcons
import com.intellij.ide.ui.laf.setEarlyNewUiDarkLaF
import com.intellij.idea.ApplicationStartArguments
import com.intellij.idea.WellKnownCommand
import com.intellij.openapi.diagnostic.logger
import com.intellij.ui.AppUIUtil
import com.intellij.ui.ColorUtil
import com.intellij.ui.ComponentUtil
import com.intellij.ui.JBColor
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.io.URLUtil
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

/** The moved state and the content of the redirect window. */
@ApiStatus.Internal
class MigrationRedirect(val state: MigrationState, val content: MigrationRedirectContent)

/** What the user selects in the redirect window. */
@ApiStatus.Internal
enum class MigrationRedirectChoice {
  /** The primary button. The window gives it also when it cannot show, because the migration is done. */
  LAUNCH,

  /** Esc or the close button. This IDE exits, and the window shows again at the next start. */
  EXIT,
}

/**
 * Returns `true` when the migration redirects the start with [args].
 * A known command stays in this IDE, because scripts and CI call it.
 * The first argument of a redirected start is an option, a URL, or an existing file or directory in [currentDir].
 * Any other first argument can be the command of a plugin, so this IDE runs it.
 */
@ApiStatus.Internal
fun isMigrationRedirectStart(args: List<String>, currentDir: Path = Path.of("").toAbsolutePath()): Boolean {
  val positional = ApplicationStartArguments.stripKnownArguments(args)
  val first = positional.firstOrNull() ?: return true
  if (WellKnownCommand.getCommandFor(positional) != null) {
    return false
  }
  if (first.startsWith("-") || first.contains(URLUtil.SCHEME_SEPARATOR) || isExistingPath(first, currentDir)) {
    return true
  }
  LOG.info("the first argument is not a file, the usual start continues: ${first}")
  return false
}

private fun isExistingPath(argument: String, currentDir: Path): Boolean =
  try {
    Files.exists(currentDir.resolve(argument))
  }
  catch (_: InvalidPathException) {
    false
  }

/**
 * Returns the redirect when this start shows the redirect window, or `null`.
 * The window shows only for [isRedirected], a valid state with the `moved` status, and a valid content copy.
 * A failure is logged and gives `null`, so that the usual start continues.
 */
@ApiStatus.Internal
fun loadMigrationRedirect(isRedirected: Boolean, stateFile: Path = migrationStateFile()): MigrationRedirect? {
  if (!isRedirected) {
    return null
  }
  try {
    val unchecked = readUncheckedMigrationState(stateFile) ?: return null
    if (unchecked.status != MigrationStatus.MOVED) {
      return null
    }
    val state = validateMigrationState(unchecked, stateFile) ?: return null
    val content = readMigrationRedirectContent(migrationRedirectContentFile(stateFile)) ?: return null
    return MigrationRedirect(state, content)
  }
  catch (e: Throwable) {
    rethrowControlFlowException(e)
    LOG.warn("the migration redirect check failed, the usual start continues", e)
    return null
  }
}

/** The command that starts the Unified IDE with the `redirect` reason and the arguments of this start. */
@ApiStatus.Internal
fun migrationRedirectCommand(app: Path, sourceProduct: String, arguments: List<String>): List<String> =
  macOpenCommand(app, migrationLaunchArguments(MigrationLaunchReason.REDIRECT, sourceProduct, arguments))

/** Starts the Unified IDE of [redirect] with `open` and returns `true` when the start succeeds. */
@ApiStatus.Internal
suspend fun launchMigrationRedirect(redirect: MigrationRedirect, sourceProduct: String, arguments: List<String>): Boolean {
  val process = startMigrationProcess(migrationRedirectCommand(redirect.state.app, sourceProduct, arguments)) ?: return false
  return runInterruptible(Dispatchers.IO) { waitForMigrationOpen(process, OPEN_TIMEOUT_SECONDS) }
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
