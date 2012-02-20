/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import java.util.Map;

/**
 * This interface extends FileEditorState interface to sync some editor's options
 * Example:
 *   we have image editor and open image files, image by image. We'd like to set default zoom
 *   factor to 1:1, but this is not the default value for this option. So, we can use methods in this
 *   interface to store these options somewhere and apply them within the session (the time we need these options).
 *
 * @author Konstantin Bulenkov
 */
public interface TransferableFileEditorState extends FileEditorState {
  /**
   * Returns unique editor ID
   *
   * @return unique editor ID
   */
  String getEditorId();

  /**
   * Options name-value string mapping. Example: {{"zoomFactor": "1:1"}, {"transparentBackground": "false"}}
   *
   * @return name-value string mapping
   */
  Map<String, String> getTransferableOptions();

  /**
   * Applies options to the editor
   * @param options name-value string mapping
   */
  void setTransferableOptions(Map<String, String> options);
}
