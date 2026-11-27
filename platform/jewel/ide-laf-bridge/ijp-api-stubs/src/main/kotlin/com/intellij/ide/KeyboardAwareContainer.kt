// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import java.awt.Component
import java.awt.event.KeyEvent

/**
 * Compile-only stub of the platform interface introduced with IJPL-250885.
 *
 * The bridge sources reference the real interface, which the released IntelliJ Platform artifacts this Gradle build
 * compiles against do not contain yet. This stub is on the compile classpath only — it is never packaged into any Jewel
 * artifact, and at runtime the platform's own class is the one loaded.
 *
 * Delete this directory (and its wiring in `ide-laf-bridge/build.gradle.kts`) as soon as `libs.versions.toml`'s `idea`
 * version points at a build that ships `com.intellij.ide.KeyboardAwareContainer`.
 */
public interface KeyboardAwareContainer {
    public fun skipKeyEventDispatcher(focusOwner: Component, event: KeyEvent): Boolean
}
