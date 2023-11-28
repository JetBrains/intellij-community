// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.ide.actionMacro

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.ide.ui.customization.CustomActionsSchema.Companion.setCustomizationSchemaForCurrentProjects
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionRuntimeRegistrar
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.actionSystem.impl.ActionConfigurationCustomizer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder.Companion.yesNo
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.PlaybackRunner
import com.intellij.openapi.ui.playback.PlaybackRunner.StatusCallback
import com.intellij.openapi.ui.playback.PlaybackRunner.StatusCallback.Edt
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.Consumer
import com.intellij.util.ui.BaseButtonBehavior
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.PositionTracker
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import org.jdom.Element
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.awt.AWTEvent
import java.awt.BorderLayout
import java.awt.Point
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.BiConsumer
import javax.swing.*

@Suppress("SpellCheckingInspection")
private const val TYPING_SAMPLE = "WWWWWWWWWWWWWWWWWWWW"
private const val ELEMENT_MACRO: @NonNls String = "macro"

@State(name = "ActionMacroManager", storages = [Storage("macros.xml")], category = SettingsCategory.UI)
class ActionMacroManager internal constructor(private val coroutineScope: CoroutineScope) : PersistentStateComponent<Element?> {
  var isRecording: Boolean = false
    private set
  private var lastMacro: ActionMacro? = null
  private var recordingMacro: ActionMacro? = null
  private var macros = ArrayList<ActionMacro>()
  private var lastMacroName: String? = null
  var isPlaying: Boolean = false
    private set
  private val lastActionInputEvent = HashSet<InputEvent>()
  private var widget: Widget? = null
  private var lastTyping = ""

  companion object {
    const val NO_NAME_NAME: String = "<noname>"

    @JvmStatic
    fun getInstance(): ActionMacroManager = service<ActionMacroManager>()
  }

  private val isKeyProcessorAdded = AtomicBoolean()

  init {
    ApplicationManager.getApplication().getMessageBus().connect(coroutineScope)
      .subscribe<AnActionListener>(AnActionListener.TOPIC, object : AnActionListener {
        override fun beforeActionPerformed(action: AnAction, event: AnActionEvent) {
          val id = ActionManager.getInstance().getId(action) ?: return
          if ("StartStopMacroRecording" == id) {
            event.inputEvent?.let { lastActionInputEvent.add(it) }
          }
          else if (isRecording) {
            recordingMacro!!.appendAction(id)
            var shortcut: String? = null
            if (event.inputEvent is KeyEvent) {
              shortcut = KeymapUtil.getKeystrokeText(KeyStroke.getKeyStrokeForEvent(event.inputEvent as KeyEvent?))
            }
            notifyUser(text = "$id${if (shortcut == null) "" else " ($shortcut)"}", typing = false)
            event.inputEvent?.let { lastActionInputEvent.add(it) }
          }
        }
      })
  }

  internal class MyActionTuner : ActionConfigurationCustomizer, ActionConfigurationCustomizer.AsyncLightCustomizeStrategy {
    override suspend fun customize(actionRegistrar: ActionRuntimeRegistrar) {
      // load state will call ActionManager, but ActionManager is not yet ready, so, postpone
      serviceAsync<ActionMacroManager>()
    }
  }

  override fun loadState(state: Element) {
    macros = ArrayList()
    for (macroElement in state.getChildren(ELEMENT_MACRO)) {
      val macro = ActionMacro()
      macro.readExternal(macroElement)
      macros.add(macro)
    }
    registerActions(ActionManager.getInstance())
  }

  override fun getState(): Element {
    val element = Element("state")
    for (macro in macros) {
      val macroElement = Element(ELEMENT_MACRO)
      macro.writeExternal(macroElement)
      element.addContent(macroElement)
    }
    return element
  }

  fun startRecording(project: Project?, macroName: String?) {
    thisLogger().assertTrue(!isRecording)

    if (isKeyProcessorAdded.compareAndSet(false, true)) {
      IdeEventQueue.getInstance().addPostprocessor(KeyPostProcessor(), coroutineScope)
    }

    isRecording = true
    recordingMacro = ActionMacro(macroName)
    val frame = WindowManager.getInstance().getIdeFrame(project)
    if (frame == null) {
      thisLogger().warn("Cannot start macro recording: ide frame not found")
      return
    }

    val statusBar = frame.getStatusBar()
    if (statusBar == null) {
      thisLogger().warn("Cannot start macro recording: status bar not found")
      return
    }

    widget = Widget(statusBar)
    statusBar.addWidget(widget!!)
  }

  private inner class Widget(private val statusBar: StatusBar) : CustomStatusBarWidget, Consumer<MouseEvent> {
    private val icon = com.intellij.util.ui.AnimatedIcon("Macro recording",
                                                         AnimatedIcon.Recording.ICONS.toTypedArray(),
                                                         AllIcons.Ide.Macro.Recording_1,
                                                           AnimatedIcon.Recording.DELAY * AnimatedIcon.Recording.ICONS.size)
    private val presentation: StatusBarWidget.WidgetPresentation
    private val balloonComponent: JPanel
    private var balloon: Balloon? = null
    private val myText: JLabel

    init {
      icon.setBorder(JBUI.CurrentTheme.StatusBar.Widget.iconBorder())
      presentation = object : StatusBarWidget.WidgetPresentation {
        override fun getTooltipText(): String = IdeBundle.message("tooltip.macro.is.being.recorded.now")

        override fun getClickConsumer() = this@Widget
      }

      val behavior = object : BaseButtonBehavior(icon, null as Void?) {
        override fun execute(e: MouseEvent) {
          showBalloon()
        }
      }
      behavior.setupListeners()
      balloonComponent = NonOpaquePanel(BorderLayout())
      val actionManager = ActionManager.getInstance()
      val stopAction = actionManager.getAction("StartStopMacroRecording")
      val group = DefaultActionGroup()
      group.add(stopAction)
      val tb = actionManager.createActionToolbar(ActionPlaces.STATUS_BAR_PLACE, group, true)
      tb.setMiniMode(true)
      val top = NonOpaquePanel(BorderLayout())
      top.add(tb.getComponent(), BorderLayout.WEST)
      myText = JLabel(IdeBundle.message("status.bar.text.macro.recorded", "..." + TYPING_SAMPLE), SwingConstants.LEFT)
      val preferredSize = myText.getPreferredSize()
      myText.preferredSize = preferredSize
      myText.setText(IdeBundle.message("label.macro.recording.started"))
      lastTyping = ""
      top.add(myText, BorderLayout.CENTER)
      balloonComponent.add(top, BorderLayout.CENTER)
    }

    private fun showBalloon() {
      balloon?.let {
        Disposer.dispose(it)
        return
      }

      balloon = JBPopupFactory.getInstance().createBalloonBuilder(balloonComponent)
        .setAnimationCycle(200)
        .setCloseButtonEnabled(true)
        .setHideOnAction(false)
        .setHideOnClickOutside(false)
        .setHideOnFrameResize(false)
        .setHideOnKeyOutside(false)
        .setSmallVariant(true)
        .setShadow(true)
        .createBalloon()
      Disposer.register(balloon!!) { balloon = null }
      balloon!!.addListener(object : JBPopupListener {
        override fun onClosed(event: LightweightWindowEvent) {
          if (balloon != null) {
            Disposer.dispose(balloon!!)
          }
        }
      })
      balloon!!.show(object : PositionTracker<Balloon?>(icon) {
        override fun recalculateLocation(`object`: Balloon): RelativePoint {
          return RelativePoint(icon, Point(icon.size.width / 2, 4))
        }
      }, Balloon.Position.above)
    }

    override fun getComponent(): JComponent = icon

    override fun ID(): String = "MacroRecording"

    override fun consume(mouseEvent: MouseEvent) {}

    override fun getPresentation() = presentation

    override fun install(statusBar: StatusBar) {
      showBalloon()
    }

    override fun dispose() {
      @Suppress("SSBasedInspection")
      icon.dispose()
      balloon?.let {
        Disposer.dispose(it)
      }
    }

    fun delete() {
      balloon?.let {
        Disposer.dispose(it)
      }
      statusBar.removeWidget(ID())
    }

    fun notifyUser(text: @Nls String?) {
      myText.setText(text)
      myText.revalidate()
      myText.repaint()
    }
  }

  fun stopRecording(project: Project?) {
    thisLogger().assertTrue(isRecording)
    if (widget != null) {
      widget!!.delete()
      widget = null
    }

    isRecording = false
    lastActionInputEvent.clear()
    var macroName: String? = ""
    do {
      macroName = Messages.showInputDialog(project,
                                           IdeBundle.message("prompt.enter.macro.name"),
                                           IdeBundle.message("title.enter.macro.name"),
                                           Messages.getQuestionIcon(), macroName, null)
      if (macroName == null) {
        recordingMacro = null
        return
      }
      if (macroName.isEmpty()) {
        macroName = null
      }
    }
    while (macroName != null && !checkCanCreateMacro(project, macroName))
    lastMacro = recordingMacro
    addRecordedMacroWithName(macroName)
    registerActions(ActionManager.getInstance())
  }

  private fun addRecordedMacroWithName(macroName: String?) {
    if (macroName != null) {
      recordingMacro!!.name = macroName
      macros.add(recordingMacro!!)
      recordingMacro = null
    }
    else {
      for (i in macros.indices) {
        val macro = macros[i]
        if (NO_NAME_NAME == macro.name) {
          macros.set(i, recordingMacro!!)
          recordingMacro = null
          break
        }
      }
      if (recordingMacro != null) {
        macros.add(recordingMacro!!)
        recordingMacro = null
      }
    }
  }

  fun playbackLastMacro() {
    if (lastMacro != null) {
      playbackMacro(lastMacro)
    }
  }

  private fun playbackMacro(macro: ActionMacro?) {
    val frame = WindowManager.getInstance().getIdeFrame(null)!!
    val script = StringBuffer()
    val actions = macro!!.actions
    for (each in actions) {
      each.generateTo(script)
    }
    val runner = PlaybackRunner(script.toString(), object : Edt() {
      override fun messageEdt(context: PlaybackContext?, text: @NlsContexts.StatusBarText String?, type: StatusCallback.Type) {
        var text = text
        if (type == StatusCallback.Type.message || type == StatusCallback.Type.error) {
          val statusBar = frame.getStatusBar()
          if (statusBar != null) {
            if (context != null) {
              text = IdeBundle.message("status.bar.message.at.line", context.currentLine, text)
            }
            statusBar.setInfo(text)
          }
        }
      }
    }, Registry.`is`("actionSystem.playback.useDirectActionCall"), true, Registry.`is`("actionSystem.playback.useTypingTargets"))
    isPlaying = true
    runner.run()
      .thenRun(Runnable {
        val statusBar = frame.getStatusBar()!!
        statusBar.setInfo(IdeBundle.message("status.bar.text.script.execution.finished"))
      })
      .whenComplete(BiConsumer { unused: Void?, throwable: Throwable? -> isPlaying = false })
  }

  val allMacros: Array<ActionMacro>
    get() = macros.toTypedArray()

  fun removeAllMacros() {
    if (lastMacro != null) {
      lastMacroName = lastMacro!!.name
      lastMacro = null
    }
    macros = ArrayList()
  }

  fun addMacro(macro: ActionMacro) {
    macros.add(macro)
    if (lastMacroName != null && lastMacroName == macro.name) {
      lastMacro = macro
      lastMacroName = null
    }
  }

  fun playMacro(macro: ActionMacro?) {
    playbackMacro(macro)
    lastMacro = macro
  }

  fun hasRecentMacro(): Boolean {
    return lastMacro != null
  }

  @JvmOverloads
  fun registerActions(actionManager: ActionManager, renamingMap: Map<String, String> = emptyMap()) {
    // unregister Tool actions
    val icons = HashMap<String, Icon>()
    for (oldId in actionManager.getActionIdList(ActionMacro.MACRO_ACTION_PREFIX)) {
      val action = actionManager.getAction(oldId)
      if (action != null) {
        val icon = action.getTemplatePresentation().icon
        if (icon != null) {
          val newId = renamingMap.get(oldId)
          icons.put(newId ?: oldId, icon)
        }
      }
      actionManager.unregisterAction(oldId)
    }

    // to prevent exception if 2 or more targets have the same name
    val registeredIds = HashSet<String>()
    for (macro in allMacros) {
      val actionId = macro.actionId
      if (!registeredIds.contains(actionId)) {
        registeredIds.add(actionId)
        val action = InvokeMacroAction(macro)
        val icon = icons.get(actionId)
        if (icon != null) {
          action.getTemplatePresentation().setIcon(icon)
        }
        actionManager.registerAction(actionId, action)
      }
    }

    // fix references to and icons of renamed macros in the custom actions schema
    val customActionsSchema = CustomActionsSchema.getInstance()
    for (actionUrl in customActionsSchema.getActions()) {
      val newId = renamingMap.get(actionUrl.component)
      if (newId != null) {
        actionUrl.component = newId
      }
    }
    for ((oldId, newId) in renamingMap) {
      val path = customActionsSchema.getIconPath(oldId)
      if (!path.isEmpty()) {
        customActionsSchema.removeIconCustomization(oldId)
        customActionsSchema.addIconCustomization(newId, path)
      }
    }
    if (!renamingMap.isEmpty()) {
      setCustomizationSchemaForCurrentProjects()
    }
  }

  private fun checkCanCreateMacro(project: Project?, name: String): Boolean {
    val actionManager = ActionManager.getInstance()
    val actionId = ActionMacro.MACRO_ACTION_PREFIX + name
    if (actionManager.getAction(actionId) != null) {
      if (!yesNo(IdeBundle.message("title.macro.name.already.used"), IdeBundle.message("message.macro.exists", name))
          .icon(Messages.getWarningIcon()).ask(project)) {
        return false
      }
      actionManager.unregisterAction(actionId)
      removeMacro(name)
    }
    return true
  }

  private fun removeMacro(name: String) {
    for (i in macros.indices) {
      val macro = macros[i]
      if (name == macro.name) {
        macros.removeAt(i)
        break
      }
    }
  }

  private class InvokeMacroAction(private val macro: ActionMacro)
    : AnAction(IdeBundle.message("action.invoke.macro.text")) {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
      IdeEventQueue.getInstance().doWhenReady(Runnable { getInstance().playMacro(macro) })
    }

    override fun update(e: AnActionEvent) {
      e.presentation.setText(macro.name, false)
      e.presentation.setEnabled(!getInstance().isPlaying)
    }
  }

  private inner class KeyPostProcessor : IdeEventQueue.EventDispatcher {
    override fun dispatch(e: AWTEvent): Boolean {
      if (isRecording && e is KeyEvent) {
        postProcessKeyEvent(e)
      }
      return false
    }

    fun postProcessKeyEvent(e: KeyEvent) {
      if (e.id != KeyEvent.KEY_PRESSED) {
        return
      }

      if (lastActionInputEvent.contains(e)) {
        lastActionInputEvent.remove(e)
        return
      }

      val modifierKeyIsPressed = e.keyCode == KeyEvent.VK_CONTROL ||
                                 e.keyCode == KeyEvent.VK_ALT ||
                                 e.keyCode == KeyEvent.VK_META ||
                                 e.keyCode == KeyEvent.VK_SHIFT
      if (modifierKeyIsPressed) {
        return
      }

      val ready = IdeEventQueue.getInstance().keyEventDispatcher.isReady
      val isChar = UIUtil.isReallyTypedEvent(e)
      val hasActionModifiers = e.isAltDown || e.isControlDown || e.isMetaDown
      val plainType = isChar && !hasActionModifiers
      val isEnter = e.keyCode == KeyEvent.VK_ENTER
      if (plainType && ready && !isEnter) {
        recordingMacro!!.appendKeyPressed(e.keyChar, e.keyCode, e.modifiers)
        notifyUser(Character.valueOf(e.keyChar).toString(), true)
      }
      else if (!plainType && ready || isEnter) {
        val stroke = KeyStroke.getKeyStrokeForEvent(e).toString()
        val pressed = stroke.indexOf("pressed")
        val key = stroke.substring(pressed + "pressed".length)
        val modifiers = stroke.substring(0, pressed)
        val shortcut = (modifiers.replace("ctrl", "control").trim() + " " + key.trim()).trim()
        recordingMacro!!.appendShortcut(shortcut)
        notifyUser(KeymapUtil.getKeystrokeText(KeyStroke.getKeyStrokeForEvent(e)), false)
      }
    }
  }

  private fun notifyUser(text: String, typing: Boolean) {
    var actualText = text
    if (typing) {
      val maxLength = TYPING_SAMPLE.length
      lastTyping += text
      if (lastTyping.length > maxLength) {
        lastTyping = "..." + lastTyping.substring(lastTyping.length - maxLength)
      }
      actualText = lastTyping
    }
    else {
      lastTyping = ""
    }
    widget?.notifyUser(IdeBundle.message("status.bar.text.macro.recorded", actualText))
  }
}
