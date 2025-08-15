// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.awt.SwingWindow as ComposeSwingWindow
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Window as ComposeWindow
import androidx.compose.ui.window.WindowDecoration
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.window.singleWindowApplication as composeSingleWindowApplication
import javax.swing.JComponent
import org.jetbrains.jewel.foundation.LocalComponent
import org.jetbrains.jewel.intui.standalone.popup.JDialogRenderer
import org.jetbrains.jewel.ui.component.LocalPopupRenderer

/**
 * Creates a window with the specified parameters and provide the
 * [org.jetbrains.jewel.foundation.component.LocalComponent]. For more details on the parameters, check the
 * [androidx.compose.ui.window.Window] documentation.
 *
 * @param onCloseRequest Callback that will be called when the user closes the window. Usually in this callback we need
 *   to manually tell Compose what to do:
 * - change `isOpen` state of the window (which is manually defined)
 * - close the whole application (`onCloseRequest = ::exitApplication` in [ApplicationScope])
 * - don't close the window on close request (`onCloseRequest = {}`)
 *
 * @param state The state object to be used to control or observe the window's state When size/position/status is
 *   changed by the user, state will be updated. When size/position/status of the window is changed by the application
 *   (changing state), the native window will update its corresponding properties. If application changes, for example
 *   [WindowState.placement], then after the next recomposition, [WindowState.size] will be changed to correspond the
 *   real size of the window. If [WindowState.position] is not [WindowPosition.isSpecified], then after the first show
 *   on the screen [WindowState.position] will be set to the absolute values.
 * @param visible Is [Window] visible to user. If `false`:
 * - internal state of [Window] is preserved and will be restored next time the window will be visible;
 * - native resources will not be released. They will be released only when [Window] will leave the composition.
 *
 * @param title Title in the title bar of the window
 * @param icon Icon in the title bar of the window (for platforms which support this). On macOS individual windows can't
 *   have a separate icon. To change the icon in the Dock, set it via `iconFile` in build.gradle
 *   (https://github.com/JetBrains/compose-jb/tree/master/tutorials/Native_distributions_and_local_execution#platform-specific-options)
 * @param decoration Specifies the decoration for this window.
 * @param transparent Disables or enables window transparency. Transparency should be set only if window is undecorated,
 *   otherwise an exception will be thrown.
 * @param resizable Can window be resized by the user (application still can resize the window changing [state])
 * @param enabled Can window react to input events
 * @param focusable Can window receive focus
 * @param alwaysOnTop Should window always be on top of another windows
 * @param onPreviewKeyEvent This callback is invoked when the user interacts with the hardware keyboard. It gives
 *   ancestors of a focused component the chance to intercept a [KeyEvent]. Return true to stop propagation of this
 *   event. If you return false, the key event will be sent to this [onPreviewKeyEvent]'s child. If none of the children
 *   consume the event, it will be sent back up to the root using the onKeyEvent callback.
 * @param onKeyEvent This callback is invoked when the user interacts with the hardware keyboard. While implementing
 *   this callback, return true to stop propagation of this event. If you return false, the key event will be sent to
 *   this [onKeyEvent]'s parent.
 * @param content Content of the window
 * @see androidx.compose.ui.window.Window
 */
@Composable
@Suppress("ComposableParamOrder")
public fun Window(
    onCloseRequest: () -> Unit,
    state: WindowState = rememberWindowState(),
    visible: Boolean = true,
    title: String = "Untitled",
    icon: Painter? = null,
    decoration: WindowDecoration,
    transparent: Boolean = false,
    resizable: Boolean = true,
    enabled: Boolean = true,
    focusable: Boolean = true,
    alwaysOnTop: Boolean = false,
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
    onKeyEvent: (KeyEvent) -> Boolean = { false },
    content: @Composable FrameWindowScope.() -> Unit,
) {
    ComposeWindow(
        onCloseRequest = onCloseRequest,
        state = state,
        visible = visible,
        title = title,
        icon = icon,
        decoration = decoration,
        transparent = transparent,
        resizable = resizable,
        enabled = enabled,
        focusable = focusable,
        alwaysOnTop = alwaysOnTop,
        onPreviewKeyEvent = onPreviewKeyEvent,
        onKeyEvent = onKeyEvent,
    ) {
        ProvideComponent(content)
    }
}

/**
 * Creates a window with the specified parameters and provide the
 * [org.jetbrains.jewel.foundation.component.LocalComponent]. For more details on the parameters, check the
 * [androidx.compose.ui.window.Window] documentation.
 *
 * @param onCloseRequest Callback that will be called when the user closes the window. Usually in this callback we need
 *   to manually tell Compose what to do:
 * - change `isOpen` state of the window (which is manually defined)
 * - close the whole application (`onCloseRequest = ::exitApplication` in [ApplicationScope])
 * - don't close the window on close request (`onCloseRequest = {}`)
 *
 * @param state The state object to be used to control or observe the window's state When size/position/status is
 *   changed by the user, state will be updated. When size/position/status of the window is changed by the application
 *   (changing state), the native window will update its corresponding properties. If application changes, for example
 *   [WindowState.placement], then after the next recomposition, [WindowState.size] will be changed to correspond the
 *   real size of the window. If [WindowState.position] is not [WindowPosition.isSpecified], then after the first show
 *   on the screen [WindowState.position] will be set to the absolute values.
 * @param visible Is [Window] visible to user. If `false`:
 * - internal state of [Window] is preserved and will be restored next time the window will be visible;
 * - native resources will not be released. They will be released only when [Window] will leave the composition.
 *
 * @param title Title in the title bar of the window
 * @param icon Icon in the title bar of the window (for platforms which support this). On macOS individual windows can't
 *   have a separate icon. To change the icon in the Dock, set it via `iconFile` in build.gradle
 *   (https://github.com/JetBrains/compose-jb/tree/master/tutorials/Native_distributions_and_local_execution#platform-specific-options)
 * @param undecorated Disables or enables decorations for this window.
 * @param transparent Disables or enables window transparency. Transparency should be set only if window is undecorated,
 *   otherwise an exception will be thrown.
 * @param resizable Can window be resized by the user (application still can resize the window changing [state])
 * @param enabled Can window react to input events
 * @param focusable Can window receive focus
 * @param alwaysOnTop Should window always be on top of another windows
 * @param onPreviewKeyEvent This callback is invoked when the user interacts with the hardware keyboard. It gives
 *   ancestors of a focused component the chance to intercept a [KeyEvent]. Return true to stop propagation of this
 *   event. If you return false, the key event will be sent to this [onPreviewKeyEvent]'s child. If none of the children
 *   consume the event, it will be sent back up to the root using the onKeyEvent callback.
 * @param onKeyEvent This callback is invoked when the user interacts with the hardware keyboard. While implementing
 *   this callback, return true to stop propagation of this event. If you return false, the key event will be sent to
 *   this [onKeyEvent]'s parent.
 * @param content Content of the window
 * @see androidx.compose.ui.window.Window
 */
@Composable
public fun Window(
    onCloseRequest: () -> Unit,
    state: WindowState = rememberWindowState(),
    visible: Boolean = true,
    title: String = "Untitled",
    icon: Painter? = null,
    undecorated: Boolean = false,
    transparent: Boolean = false,
    resizable: Boolean = true,
    enabled: Boolean = true,
    focusable: Boolean = true,
    alwaysOnTop: Boolean = false,
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
    onKeyEvent: (KeyEvent) -> Boolean = { false },
    content: @Composable FrameWindowScope.() -> Unit,
) {
    ComposeWindow(
        onCloseRequest = onCloseRequest,
        state = state,
        visible = visible,
        title = title,
        icon = icon,
        undecorated = undecorated,
        transparent = transparent,
        resizable = resizable,
        enabled = enabled,
        focusable = focusable,
        alwaysOnTop = alwaysOnTop,
        onPreviewKeyEvent = onPreviewKeyEvent,
        onKeyEvent = onKeyEvent,
    ) {
        ProvideComponent(content)
    }
}

/**
 * Creates a window with the specified parameters and provide the
 * [org.jetbrains.jewel.foundation.component.LocalComponent]. For more details on the parameters, check the
 * [androidx.compose.ui.awt.SwingWindow] documentation.
 *
 * @see androidx.compose.ui.awt.SwingWindow
 */
@Composable
@Suppress("ComposableParamOrder")
public fun SwingWindow(
    visible: Boolean = true,
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
    onKeyEvent: (KeyEvent) -> Boolean = { false },
    create: () -> ComposeWindow,
    dispose: (ComposeWindow) -> Unit,
    update: (ComposeWindow) -> Unit = {},
    content: @Composable FrameWindowScope.() -> Unit,
) {
    ComposeSwingWindow(visible, onPreviewKeyEvent, onKeyEvent, create, dispose, update) { ProvideComponent(content) }
}

/**
 * Creates a window with the specified parameters and provide the
 * [org.jetbrains.jewel.foundation.component.LocalComponent]. For more details on the parameters, check the
 * [androidx.compose.ui.awt.SwingWindow] documentation.
 *
 * @see androidx.compose.ui.awt.SwingWindow
 */
@Composable
@Suppress("ComposableParamOrder")
public fun SwingWindow(
    onCloseRequest: () -> Unit,
    state: WindowState = rememberWindowState(),
    visible: Boolean = true,
    title: String = "Untitled",
    icon: Painter? = null,
    decoration: WindowDecoration = WindowDecoration.SystemDefault,
    transparent: Boolean = false,
    resizable: Boolean = true,
    enabled: Boolean = true,
    focusable: Boolean = true,
    alwaysOnTop: Boolean = false,
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
    onKeyEvent: (KeyEvent) -> Boolean = { false },
    init: (ComposeWindow) -> Unit,
    content: @Composable FrameWindowScope.() -> Unit,
) {
    ComposeSwingWindow(
        onCloseRequest = onCloseRequest,
        state = state,
        visible = visible,
        title = title,
        icon = icon,
        decoration = decoration,
        transparent = transparent,
        resizable = resizable,
        enabled = enabled,
        focusable = focusable,
        alwaysOnTop = alwaysOnTop,
        onPreviewKeyEvent = onPreviewKeyEvent,
        onKeyEvent = onKeyEvent,
        init = init,
    ) {
        ProvideComponent(content)
    }
}

/**
 * Creates a single window entry point for the Compose application and provide the
 * [org.jetbrains.jewel.foundation.component.LocalComponent]. For more details in the parameters, check the
 * [androidx.compose.ui.window.singleWindowApplication] documentation.
 *
 * @param state The state object to be used to control or observe the window's state When size/position/status is
 *   changed by the user, state will be updated. When size/position/status of the window is changed by the application
 *   (changing state), the native window will update its corresponding properties. If application changes, for example
 *   [WindowState.placement], then after the next recomposition, [WindowState.size] will be changed to correspond the
 *   real size of the window. If [WindowState.position] is not [WindowPosition.isSpecified], then after the first show
 *   on the screen [WindowState.position] will be set to the absolute values.
 * @param visible Is [Window] visible to user. If `false`:
 * - internal state of [Window] is preserved and will be restored next time the window will be visible;
 * - native resources will not be released. They will be released only when [Window] will leave the composition.
 *
 * @param title Title in the title bar of the window
 * @param icon Icon in the title bar of the window (for platforms which support this). On macOS individual windows can't
 *   have a separate icon. To change the icon in the Dock, set it via `iconFile` in build.gradle
 *   (https://github.com/JetBrains/compose-jb/tree/master/tutorials/Native_distributions_and_local_execution#platform-specific-options)
 * @param decoration Specifies the decoration for this window.
 * @param transparent Disables or enables window transparency. Transparency should be set only if window is undecorated,
 *   otherwise an exception will be thrown.
 * @param resizable Can window be resized by the user (application still can resize the window changing [state])
 * @param enabled Can window react to input events
 * @param focusable Can window receive focus
 * @param alwaysOnTop Should window always be on top of another windows
 * @param onPreviewKeyEvent This callback is invoked when the user interacts with the hardware keyboard. It gives
 *   ancestors of a focused component the chance to intercept a [KeyEvent]. Return true to stop propagation of this
 *   event. If you return false, the key event will be sent to this [onPreviewKeyEvent]'s child. If none of the children
 *   consume the event, it will be sent back up to the root using the onKeyEvent callback.
 * @param onKeyEvent This callback is invoked when the user interacts with the hardware keyboard. While implementing
 *   this callback, return true to stop propagation of this event. If you return false, the key event will be sent to
 *   this [onKeyEvent]'s parent.
 * @param exitProcessOnExit should `exitProcess(0)` be called after the window is closed. exitProcess speedup process
 *   exit (instant instead of 1-4sec). If `false`, the execution of the function will be unblocked after application is
 *   exited (when the last window is closed, and all [LaunchedEffect]s are complete).
 * @param content Content of the window
 * @see androidx.compose.ui.window.singleWindowApplication
 */
public fun singleWindowApplication(
    state: WindowState = WindowState(),
    visible: Boolean = true,
    title: String = "Untitled",
    icon: Painter? = null,
    decoration: WindowDecoration,
    transparent: Boolean = false,
    resizable: Boolean = true,
    enabled: Boolean = true,
    focusable: Boolean = true,
    alwaysOnTop: Boolean = false,
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
    onKeyEvent: (KeyEvent) -> Boolean = { false },
    exitProcessOnExit: Boolean = true,
    content: @Composable FrameWindowScope.() -> Unit,
) {
    composeSingleWindowApplication(
        state,
        visible,
        title,
        icon,
        decoration,
        transparent,
        resizable,
        enabled,
        focusable,
        alwaysOnTop,
        onPreviewKeyEvent,
        onKeyEvent,
        exitProcessOnExit,
    ) {
        ProvideComponent(content)
    }
}

/**
 * Creates a single window entry point for the Compose application and provide the
 * [org.jetbrains.jewel.foundation.component.LocalComponent]. For more details in the parameters, check the
 * [androidx.compose.ui.window.singleWindowApplication] documentation.
 *
 * @param state The state object to be used to control or observe the window's state When size/position/status is
 *   changed by the user, state will be updated. When size/position/status of the window is changed by the application
 *   (changing state), the native window will update its corresponding properties. If application changes, for example
 *   [WindowState.placement], then after the next recomposition, [WindowState.size] will be changed to correspond the
 *   real size of the window. If [WindowState.position] is not [WindowPosition.isSpecified], then after the first show
 *   on the screen [WindowState.position] will be set to the absolute values.
 * @param visible Is [Window] visible to user. If `false`:
 * - internal state of [Window] is preserved and will be restored next time the window will be visible;
 * - native resources will not be released. They will be released only when [Window] will leave the composition.
 *
 * @param title Title in the title bar of the window
 * @param icon Icon in the title bar of the window (for platforms which support this). On macOS individual windows can't
 *   have a separate icon. To change the icon in the Dock, set it via `iconFile` in build.gradle
 *   (https://github.com/JetBrains/compose-jb/tree/master/tutorials/Native_distributions_and_local_execution#platform-specific-options)
 * @param undecorated Disables or enables decorations for this window.
 * @param transparent Disables or enables window transparency. Transparency should be set only if window is undecorated,
 *   otherwise an exception will be thrown.
 * @param resizable Can window be resized by the user (application still can resize the window changing [state])
 * @param enabled Can window react to input events
 * @param focusable Can window receive focus
 * @param alwaysOnTop Should window always be on top of another windows
 * @param onPreviewKeyEvent This callback is invoked when the user interacts with the hardware keyboard. It gives
 *   ancestors of a focused component the chance to intercept a [KeyEvent]. Return true to stop propagation of this
 *   event. If you return false, the key event will be sent to this [onPreviewKeyEvent]'s child. If none of the children
 *   consume the event, it will be sent back up to the root using the onKeyEvent callback.
 * @param onKeyEvent This callback is invoked when the user interacts with the hardware keyboard. While implementing
 *   this callback, return true to stop propagation of this event. If you return false, the key event will be sent to
 *   this [onKeyEvent]'s parent.
 * @param exitProcessOnExit should `exitProcess(0)` be called after the window is closed. exitProcess speedup process
 *   exit (instant instead of 1-4sec). If `false`, the execution of the function will be unblocked after application is
 *   exited (when the last window is closed, and all [LaunchedEffect]s are complete).
 * @param content Content of the window
 * @see androidx.compose.ui.window.singleWindowApplication
 */
public fun singleWindowApplication(
    state: WindowState = WindowState(),
    visible: Boolean = true,
    title: String = "Untitled",
    icon: Painter? = null,
    undecorated: Boolean = false,
    transparent: Boolean = false,
    resizable: Boolean = true,
    enabled: Boolean = true,
    focusable: Boolean = true,
    alwaysOnTop: Boolean = false,
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
    onKeyEvent: (KeyEvent) -> Boolean = { false },
    exitProcessOnExit: Boolean = true,
    content: @Composable FrameWindowScope.() -> Unit,
) {
    composeSingleWindowApplication(
        state,
        visible,
        title,
        icon,
        undecorated,
        transparent,
        resizable,
        enabled,
        focusable,
        alwaysOnTop,
        onPreviewKeyEvent,
        onKeyEvent,
        exitProcessOnExit,
    ) {
        ProvideComponent(content)
    }
}

/**
 * Provides the ComposeWindowPanel as a LocalComponent.
 *
 * @param content Content to be rendered within the window.
 */
@Composable
private fun FrameWindowScope.ProvideComponent(content: @Composable FrameWindowScope.() -> Unit) {
    val currentComponent = remember(window) { window.contentPane.components.filterIsInstance<JComponent>().first() }
    CompositionLocalProvider(LocalComponent provides currentComponent, LocalPopupRenderer provides JDialogRenderer) {
        content()
    }
}
