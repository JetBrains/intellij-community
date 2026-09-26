// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui;

/**
 * Enum for different UI modes. Compact mode represents UI with smaller insets in base
 * UI components and more condensed text representation.
 * For example, Compact mode reduces rowHeight in trees and lists as well as line spacing in editor
 *
 * @author Konstantin Bulenkov
 */
public enum UIDensity {
  DEFAULT,
  COMPACT
}
