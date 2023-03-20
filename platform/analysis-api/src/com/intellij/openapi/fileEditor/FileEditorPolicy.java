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
   * <p></p>
   * It is recommended to use {@link FileEditorPolicy#HIDE_OTHER_EDITORS} instead.
   */
  HIDE_DEFAULT_EDITOR,

  /**
   * Hide other editors (if any) for the file.
   * If at least one instance of {@code FileEditorProvider} with such policy exists for the file,
   * then the editor(s) related to such instance(s) of {@code FileEditorProvider} will be created,
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
