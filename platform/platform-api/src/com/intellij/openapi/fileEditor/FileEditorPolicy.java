// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor;

public enum FileEditorPolicy {

  /**
   * Place created editor before default IDE editor (if any).
   */
  /*
   * should be the first declaration
   */
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
   * Place created editor after the default IDE editor (if any).
   */
  /*
   * should be the last declaration
   */
  PLACE_AFTER_DEFAULT_EDITOR
}
