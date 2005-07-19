/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
