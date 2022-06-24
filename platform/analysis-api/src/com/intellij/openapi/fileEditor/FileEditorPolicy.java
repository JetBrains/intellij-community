// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor;

import org.jetbrains.annotations.ApiStatus;

public enum FileEditorPolicy {

  /**
   * Place a created editor before the default IDE editor (if any).
   */
  // should be the first declaration
  PLACE_BEFORE_DEFAULT_EDITOR,

  /**
   * No policy
   */
  NONE,

  /**
   * Do not create default IDE editor (if any) for the file.
   */
  HIDE_DEFAULT_EDITOR,

  /**
   * Hide other editors (if any) for the file.
   * If several instances of {@code FileEditorProvider} with such policy exist for the file,
   * then the editors related to such instances of {@code FileEditorProvider} will be created,
   * but the editors for other instances (if any) of {@code FileEditorProvider} will not.
   *
   * @see FileEditorProvider
   * @see FileEditorProvider#getPolicy()
   */
  @ApiStatus.Experimental
  HIDE_OTHER_EDITORS,

  /**
   * Place created editor after the default IDE editor (if any).
   */
  // should be the last declaration
  PLACE_AFTER_DEFAULT_EDITOR
}
