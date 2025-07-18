// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.bootstrap

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.diagnostic.runActivity
import com.intellij.ide.AssertiveRepaintManager
import com.intellij.ide.BootstrapBundle
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.ui.html.createGlobalStyleSheet
import com.intellij.ide.ui.laf.LookAndFeelThemeAdapter
import com.intellij.ide.ui.laf.createBaseLaF
import com.intellij.idea.AppExitCodes
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.application.impl.AWTExceptionHandler
import com.intellij.openapi.application.setUserInteractiveQosForEdt
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.wm.WeakFocusStackManager
import com.intellij.platform.diagnostic.telemetry.impl.span
import com.intellij.ui.AppUIUtil
import com.intellij.ui.IconManager
import com.intellij.ui.icons.CoreIconManager
import com.intellij.ui.isWindowIconAlreadyExternallySet
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.updateAppWindowIcon
import com.intellij.util.ui.RawSwingDispatcher
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.accessibility.ScreenReader
import kotlinx.coroutines.*
import org.jetbrains.annotations.VisibleForTesting
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.dnd.DragSource
import java.lang.invoke.MethodHandles
import javax.swing.JOptionPane
import javax.swing.RepaintManager
import javax.swing.UIManager
import kotlin.system.exitProcess

internal suspend fun initUi(initAwtToolkitJob: Job, isHeadless: Boolean, asyncScope: CoroutineScope) {
  // IdeaLaF uses AllIcons - icon manager must be activated
  if (!isHeadless) {
    span("icon manager activation") {
      IconManager.activate(CoreIconManager())
    }
  }

  initAwtToolkitJob.join()

  val preloadFontJob = if (isHeadless) {
    null
  }
  else {
    asyncScope.launch(CoroutineName("system fonts loading") + Dispatchers.IO) {
      // forces loading of all system fonts; the following statement alone might not do it (see JBR-1825)
      Font("N0nEx1st5ntF0nt", Font.PLAIN, 1).family
      // caches available font family names for the default locale to speed up editor reopening (see `ComplementaryFontsRegistry`)
      GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames
    }
  }

  // SwingDispatcher must be used after Toolkit init
  span("initUi", RawSwingDispatcher) {
    initLafAndScale(isHeadless = isHeadless, preloadFontJob = preloadFontJob)
  }
}

internal suspend fun configureCssUiDefaults() {
  withContext(RawSwingDispatcher) {
    val uiDefaults = span("app-specific laf state initialization") { UIManager.getDefaults() }
    span("html style patching") {
      // create a separate copy for each case
      val globalStyleSheet = createGlobalStyleSheet()
      uiDefaults["javax.swing.JLabel.userStyleSheet"] = globalStyleSheet
      uiDefaults["HTMLEditorKit.jbStyleSheet"] = globalStyleSheet
    }
  }
}

private suspend fun initLafAndScale(isHeadless: Boolean, preloadFontJob: Job?) {
  if (!isHeadless) {
    span("graphics environment checking") {
      if (GraphicsEnvironment.getLocalGraphicsEnvironment().isHeadlessInstance) {
        StartupErrorReporter.showError(BootstrapBundle.message("bootstrap.error.title.start.failed"), BootstrapBundle.message("bootstrap.error.message.no.graphics"))
        exitProcess(AppExitCodes.NO_GRAPHICS)
      }
    }

    // we don't need Idea LaF to show splash, but we do need some base LaF to compute system font data (see below for what)
    if (SystemInfoRt.isLinux) {
      preloadFontJob?.join()
    }
  }

  val baseLaF = span("base LaF creation") { createBaseLaF() }
  span("base LaF initialization") {
    // LaF is useless until initialized (`getDefaults` "should only be invoked ... after `initialize` has been invoked.")
    baseLaF.initialize()
    LookAndFeelThemeAdapter.preInitializedBaseLaf.compareAndSet(null, baseLaF)
  }

  // to compute the system scale factor on non-macOS (JRE HiDPI is not enabled), we need to know system font data,
  // and to compute system font data, we need to know `Label.font` UI default (that's why we compute base LaF first)
  if (!isHeadless && !SystemInfoRt.isMac) {
    JBUIScale.preload {
      runActivity("base LaF defaults getting") { baseLaF.defaults }
    }
  }
}

internal fun scheduleInitAwtToolkit(scope: CoroutineScope, lockSystemDirsJob: Job, busyThread: Thread): Job {
  val task = scope.launch {
    // this should happen before UI initialization - if we're not going to show the UI (in case another IDE instance is already running),
    // we shouldn't initialize AWT toolkit to avoid unnecessary focus stealing and space switching on macOS.
    if (SystemInfoRt.isMac) {
      lockSystemDirsJob.join()
    }

    launch(CoroutineName("initAwtToolkit")) {
      initAwtToolkit(busyThread)
    }
  }
  return task
}

private suspend fun initAwtToolkit(busyThread: Thread) {
  checkHiDPISettings()
  blockATKWrapper()

  @Suppress("SpellCheckingInspection")
  System.setProperty("sun.awt.noerasebackground", "true")
  // mute system Cmd+`/Cmd+Shift+` shortcuts on macOS to avoid a conflict with corresponding platform actions (JBR-specific option)
  if (System.getProperty("apple.awt.captureNextAppWinKey") == null) {
    System.setProperty("apple.awt.captureNextAppWinKey", "true")
  }

  span("awt toolkit creating") {
    Toolkit.getDefaultToolkit()
  }

  span("awt auto shutdown configuring") {
    // Make EDT to always persist while the main thread is alive.
    // Otherwise, it's possible to have EDT being terminated by [AWTAutoShutdown], which will break a `ReadMostlyRWLock` instance.
    // [AWTAutoShutdown.notifyThreadBusy(Thread)] will put the main thread into the thread map,
    // and thus will effectively disable auto shutdown behavior for this application.
    try {
      @Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")
      sun.awt.AWTAutoShutdown.getInstance().notifyThreadBusy(busyThread)
    }
    catch (e: IllegalAccessError) {
      throw RuntimeException("Required '--add-opens' option wasn't added to JVM arguments. If you're running the IDE from sources, most probably it means that 'DevKit' plugin isn't enabled", e)
    }
  }

  // required for both UI scale computation and base LaF
  span("GraphicsEnvironment init") {
    GraphicsEnvironment.getLocalGraphicsEnvironment()
  }
}

internal fun scheduleInitIdeEventQueue(scope: CoroutineScope, initAwtToolkit: Job, isHeadless: Boolean): Job = scope.launch {
  initAwtToolkit.join()
  withContext(RawSwingDispatcher) {
    replaceIdeEventQueue(isHeadless)
  }
}

// the method must be called on EDT
private suspend fun replaceIdeEventQueue(isHeadless: Boolean) {
  span("event queue replacing") {
    // replace system event queue
    IdeEventQueue.getInstance()
    // do not crash AWT on exceptions
    AWTExceptionHandler.register()
  }

  if (!isHeadless && System.getProperty("idea.check.swing.threading").toBoolean()) {
    span("repaint manager set") {
      RepaintManager.setCurrentManager(AssertiveRepaintManager())
    }
  }

  span("set QoS for EDT") {
    if (!isHeadless && setUserInteractiveQosForEdt) {
      UiThreadPriority.adjust()
    }
  }
}

/*
 * The method should be called before `Toolkit#initAssistiveTechnologies`, which is called from `Toolkit#getDefaultToolkit`.
 */
private fun blockATKWrapper() {
  // the registry must not be used here, because this method is called before application loading
  @Suppress("SpellCheckingInspection")
  if (!SystemInfoRt.isLinux || !System.getProperty("linux.jdk.accessibility.atkwrapper.block", "true").toBoolean()) {
    return
  }

  val activity = StartUpMeasurer.startActivity("atk wrapper blocking")
  if (ScreenReader.isEnabled(ScreenReader.ATK_WRAPPER)) {
    // Replacing `AtkWrapper` with a fake `Object`. It'll be instantiated, and garbage collected right away, a NOP.
    System.setProperty("javax.accessibility.assistive_technologies", "java.lang.Object")
    logger<AppStarter>().info("${ScreenReader.ATK_WRAPPER} is blocked, see IDEA-149219")
  }
  activity.end()
}

@VisibleForTesting
fun checkHiDPISettings() {
  if (!System.getProperty("hidpi", "true").toBoolean()) {
    // suppress JRE-HiDPI mode
    System.setProperty("sun.java2d.uiScale.enabled", "false")
  }
}

// must happen after initUi
internal fun scheduleUpdateFrameClassAndWindowIconAndPreloadSystemFonts(
  scope: CoroutineScope,
  initAwtToolkitJob: Job,
  initUiScale: Job,
  appInfoDeferred: Deferred<ApplicationInfoEx>,
) {
  scope.launch {
    initAwtToolkitJob.join()

    if (StartupUiUtil.isXToolkit()) {
      launch(CoroutineName("frame class updating")) {
        appInfoDeferred.join()
        try {
          val toolkit = Toolkit.getDefaultToolkit()
          val aClass = toolkit.javaClass
          if (aClass.name == "sun.awt.X11.XToolkit") {
            MethodHandles.privateLookupIn(aClass, MethodHandles.lookup())
              .findStaticSetter(aClass, "awtAppClassName", String::class.java)
              .invoke(AppUIUtil.getFrameClass())
          }
        }
        catch (t: Throwable) {
          logger<AppStarter>().warn("Failed to set WM frame class in XToolkit: $t")
        }
      }
    }
    else if (StartupUiUtil.isWaylandToolkit()) {
      appInfoDeferred.join()
      System.setProperty("awt.app.id", AppUIUtil.getFrameClass())
    }

    // `updateWindowIcon` should be called after `initUiJob`, because it uses computed system font data for scale context
    if (!isWindowIconAlreadyExternallySet()) {
      launch {
        initUiScale.join()
        appInfoDeferred.join()
        // most of the time is consumed by loading SVG and can be done in parallel
        span("update window icon") {
          updateAppWindowIcon(JOptionPane.getRootFrame())
        }
      }
    }

    // preloading cursors used by the drag-n-drop AWT subsystem, run on SwingDispatcher to avoid a possible deadlock (see RIDER-80810)
    launch(CoroutineName("DnD setup") + RawSwingDispatcher) {
      DragSource.getDefaultDragSource()
    }

    launch(RawSwingDispatcher) {
      WeakFocusStackManager.getInstance()
    }
  }
}
