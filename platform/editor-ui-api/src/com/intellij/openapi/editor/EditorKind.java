// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor;

/**
 * Distinguishes between:
 * <ul>
 * <li>a main editor
 * <li>a console editor, typically used to display the read-only output of a process
 * <li>a preview editor, for search results, code style or highlighting
 * <li>a diff editor, for comparing two documents
 * </ul>
 */
public enum EditorKind {
  UNTYPED,
  MAIN_EDITOR,  // instead of SoftWrapAppliancePlaces.MAIN_EDITOR
  CONSOLE,      // EDITOR_IS_CONSOLE_VIEW, SoftWrapAppliancePlaces.CONSOLE
  PREVIEW,      // SoftWrapAppliancePlaces.PREVIEW
  DIFF         // EDITOR_IS_DIFF_KEY
}
