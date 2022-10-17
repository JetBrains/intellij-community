// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.models

/**
 * This class represents shortcuts that can't be represented with Swing.
 *
 * For example, Shift+Shift
 *
 * This class is for UI purposes only.
 */
class DummyKeyboardShortcut(val firstKeyStroke: String, val secondKeyStroke: String? = null)