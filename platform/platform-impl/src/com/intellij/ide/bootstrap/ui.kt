// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE", "ReplacePutWithAssignment")
// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bootstrap

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.diagnostic.runActivity
import com.intellij.ide.AssertiveRepaintManager
import com.intellij.ide.BootstrapBundle
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.ui.html.GlobalStyleSheetHolder
import com.intellij.ide.ui.laf.IdeaLaf
import com.intellij.ide.ui.laf.darcula.DarculaLaf
import com.intellij.idea.AppExitCodes
import com.intellij.idea.AppStarter
import com.intellij.idea.StartupErrorReporter
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.impl.AWTExceptionHandler
import com.intellij.openapi.application.impl.RawSwingDispatcher
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.wm.WeakFocusStackManager
import com.intellij.platform.diagnostic.telemetry.impl.span
import com.intellij.ui.AppUIUtil
import com.intellij.ui.IconManager
import com.intellij.ui.JreHiDpiUtil
import com.intellij.ui.icons.CoreIconManager
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.updateAppWindowIcon
import com.intellij.util.concurrency.SynchronizedClearableLazy
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.accessibility.ScreenReader
import kotlinx.coroutines.*
import org.jetbrains.annotations.VisibleForTesting
import sun.awt.AWTAutoShutdown
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.dnd.DragSource
import java.lang.invoke.MethodHandles
import java.nio.file.Path
import javax.swing.JOptionPane
import javax.swing.RepaintManager
import javax.swing.UIManager
import kotlin.system.exitProcess

internal fun getSvgIconCacheFile(): Path = Path.of(PathManager.getSystemPath(), "icon-v14.db")

internal suspend fun initUi(isHeadless: Boolean) {
  if (!isHeadless) {
    val env = span("GraphicsEnvironment init") {
      GraphicsEnvironment.getLocalGraphicsEnvironment()
    }
    span("graphics environment checking") {
      if (env.isHeadlessInstance) {
        StartupErrorReporter.showMessage(BootstrapBundle.message("bootstrap.error.title.start.failed"),
                                         BootstrapBundle.message("bootstrap.error.message.no.graphics.environment"), true)
        exitProcess(AppExitCodes.NO_GRAPHICS)
      }
    }
  }

  // we don't need Idea LaF to show splash, but we do need some base LaF to compute system font data (see below for what)

  val baseLaF = span("base LaF creation") { DarculaLaf.createBaseLaF() }
  span("base LaF initialization") {
    // LaF is useless until initialized (`getDefaults` "should only be invoked ... after `initialize` has been invoked.")
    baseLaF.initialize()
    DarculaLaf.setPreInitializedBaseLaf(baseLaF)
  }

  // to compute the system scale factor on non-macOS (JRE HiDPI is not enabled), we need to know system font data,
  // and to compute system font data we need to know `Label.font` UI default (that's why we compute base LaF first)
  if (!isHeadless) {
    JBUIScale.preload {
      runActivity("base LaF defaults getting") { baseLaF.defaults }
    }
  }

  val uiDefaults = span("app-specific laf state initialization") { UIManager.getDefaults() }

  span("html style patching") {
    // create a separate copy for each case
    val globalStyleSheet = GlobalStyleSheetHolder.getGlobalStyleSheet()
    uiDefaults.put("javax.swing.JLabel.userStyleSheet", globalStyleSheet)
    uiDefaults.put("HTMLEditorKit.jbStyleSheet", globalStyleSheet)

    span("global styleSheet updating") {
      GlobalStyleSheetHolder.updateGlobalSwingStyleSheet()
    }
  }
}

private fun CoroutineScope.schedulePreloadingLafClasses() {
  launch(CoroutineName("LaF class preloading") + Dispatchers.IO) {
    val classLoader = AppStarter::class.java.classLoader
    // preload class not in EDT
    Class.forName(DarculaLaf::class.java.name, true, classLoader)
    Class.forName(IdeaLaf::class.java.name, true, classLoader)
    Class.forName(JBUIScale::class.java.name, true, classLoader)
    Class.forName(JreHiDpiUtil::class.java.name, true, classLoader)
    Class.forName(SynchronizedClearableLazy::class.java.name, true, classLoader)
    Class.forName(ScaleContext::class.java.name, true, classLoader)
    Class.forName(GlobalStyleSheetHolder::class.java.name, true, classLoader)
    Class.forName(StartupUiUtil::class.java.name, true, classLoader)
  }
}

internal fun CoroutineScope.scheduleInitAwtToolkitAndEventQueue(lockSystemDirsJob: Job, busyThread: Thread, isHeadless: Boolean): Job {
  val task = launch {
    // this should happen before UI initialization - if we're not going to show the UI (in case another IDE instance is already running),
    // we shouldn't initialize AWT toolkit in order to avoid unnecessary focus stealing and space switching on macOS.
    lockSystemDirsJob.join()

    launch(CoroutineName("initAwtToolkit")) {
      initAwtToolkit(busyThread)
    }

    // IdeaLaF uses AllIcons - icon manager must be activated
    if (!isHeadless) {
      launch(CoroutineName("icon manager activation")) {
        IconManager.activate(CoreIconManager())
      }
    }

    withContext(RawSwingDispatcher) {
      patchSystem(isHeadless)
    }
  }

  launch(CoroutineName("IdeEventQueue class preloading") + Dispatchers.IO) {
    val classLoader = AppStarter::class.java.classLoader
    // preload class not in EDT
    Class.forName(IdeEventQueue::class.java.name, true, classLoader)
    Class.forName(AWTExceptionHandler::class.java.name, true, classLoader)
  }
  schedulePreloadingLafClasses()
  return task
}

private suspend fun initAwtToolkit(busyThread: Thread) {
  checkHiDPISettings()
  blockATKWrapper()

  @Suppress("SpellCheckingInspection")
  System.setProperty("sun.awt.noerasebackground", "true")
  // mute system Cmd+`/Cmd+Shift+` shortcuts on macOS to avoid a conflict with corresponding platform actions (JBR-specific option)
  System.setProperty("apple.awt.captureNextAppWinKey", "true")

  span("awt toolkit creating") {
    Toolkit.getDefaultToolkit()
  }

  span("awt auto shutdown configuring") {
    // Make EDT to always persist while the main thread is alive.
    // Otherwise, it's possible to have EDT being terminated by [AWTAutoShutdown], which will break a `ReadMostlyRWLock` instance.
    // [AWTAutoShutdown.notifyThreadBusy(Thread)] will put the main thread into the thread map,
    // and thus will effectively disable auto shutdown behavior for this application.
    AWTAutoShutdown.getInstance().notifyThreadBusy(busyThread)
  }
}

// the method must be called on EDT
private suspend fun patchSystem(isHeadless: Boolean) {
  span("event queue replacing") {
    // replace system event queue
    IdeEventQueue.getInstance()
    // do not crash AWT on exceptions
    AWTExceptionHandler.register()
  }
  if (!isHeadless && "true" == System.getProperty("idea.check.swing.threading")) {
    span("repaint manager set") {
      RepaintManager.setCurrentManager(AssertiveRepaintManager())
    }
  }
}

/*
 * The method should be called before `Toolkit#initAssistiveTechnologies`, which is called from `Toolkit#getDefaultToolkit`.
 */
private fun blockATKWrapper() {
  // the registry must not be used here, because this method is called before application loading
  @Suppress("SpellCheckingInspection")
  if (!SystemInfoRt.isLinux || !java.lang.Boolean.parseBoolean(System.getProperty("linux.jdk.accessibility.atkwrapper.block", "true"))) {
    return
  }

  val activity = StartUpMeasurer.startActivity("atk wrapper blocking")
  if (ScreenReader.isEnabled(ScreenReader.ATK_WRAPPER)) {
    // Replacing `AtkWrapper` with a fake `Object`. It'll be instantiated & garbage collected right away, a NOP.
    System.setProperty("javax.accessibility.assistive_technologies", "java.lang.Object")
    logger<StartupUiUtil>().info("${ScreenReader.ATK_WRAPPER} is blocked, see IDEA-149219")
  }
  activity.end()
}

@VisibleForTesting
fun checkHiDPISettings() {
  if (!java.lang.Boolean.parseBoolean(System.getProperty("hidpi", "true"))) {
    // suppress JRE-HiDPI mode
    System.setProperty("sun.java2d.uiScale.enabled", "false")
  }
}

// must happen after initUi
internal fun CoroutineScope.updateFrameClassAndWindowIconAndPreloadSystemFonts(initUiDeferred: Job) {
  launch {
    initUiDeferred.join()

    launch(CoroutineName("system fonts loading") + Dispatchers.IO) {
      // forces loading of all system fonts; the following statement alone might not do it (see JBR-1825)
      Font("N0nEx1st5ntF0nt", Font.PLAIN, 1).family
      // caches available font family names for the default locale to speed up editor reopening (see `ComplementaryFontsRegistry`)
      GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames
    }

    if (!SystemInfoRt.isWindows && !SystemInfoRt.isMac) {
      launch(CoroutineName("frame class updating")) {
        try {
          val toolkit = Toolkit.getDefaultToolkit()
          val aClass = toolkit.javaClass
          if (aClass.name == "sun.awt.X11.XToolkit") {
            MethodHandles.privateLookupIn(aClass, MethodHandles.lookup())
              .findStaticSetter(aClass, "awtAppClassName", String::class.java)
              .invoke(AppUIUtil.getFrameClass())
          }
        }
        catch (ignore: Throwable) {
        }
      }
    }

    launch(CoroutineName("update window icon")) {
      // `updateWindowIcon` should be called after `initUiJob`, because it uses computed system font data for scale context
      if (!AppUIUtil.isWindowIconAlreadyExternallySet && !PluginManagerCore.isRunningFromSources()) {
        // most of the time is consumed by loading SVG and can be done in parallel
        updateAppWindowIcon(JOptionPane.getRootFrame())
      }
    }

    // preload cursors used by the drag-n-drop AWT subsystem, run on SwingDispatcher to avoid a possible deadlock - see RIDER-80810
    launch(CoroutineName("DnD setup") + RawSwingDispatcher) {
      DragSource.getDefaultDragSource()
    }

    launch(RawSwingDispatcher) {
      WeakFocusStackManager.getInstance()
    }
  }
}
