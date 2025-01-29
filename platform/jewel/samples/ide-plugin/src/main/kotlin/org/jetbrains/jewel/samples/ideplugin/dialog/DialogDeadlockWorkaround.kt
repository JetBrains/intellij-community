// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.samples.ideplugin.dialog

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

/**
 * Initializes the MainDispatcherChecker in Compose; this must be called from the UI thread in non-modal state. This is
 * a workaround for a freeze in the UI thread that is triggered by using Compose in a dialog before it has been used in
 * a non-modal state.
 *
 * See https://youtrack.jetbrains.com/issue/IJPL-166436
 *
 * TODO Delete this when the upstream bug is fixed.
 */
internal fun initializeComposeMainDispatcherChecker() {
    object : LifecycleOwner {
        override val lifecycle = LifecycleRegistry(this)

        init {
            lifecycle.currentState = Lifecycle.State.STARTED
        }
    }
}
