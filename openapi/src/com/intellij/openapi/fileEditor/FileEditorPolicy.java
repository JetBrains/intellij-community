/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.fileEditor;

public class FileEditorPolicy {
  /**
   * No policy
   */
  public static final FileEditorPolicy NONE = new FileEditorPolicy();
  /**
   * Do not create default IDEA's editor (if any) for the file
   */
  public static final FileEditorPolicy HIDE_DEFAULT_EDITOR = new FileEditorPolicy();
  /**
   * Place created editor before default IDEA's editor (if any)
   */
  public static final FileEditorPolicy PLACE_BEFORE_DEFAULT_EDITOR = new FileEditorPolicy();
  
  private FileEditorPolicy() {}
}
