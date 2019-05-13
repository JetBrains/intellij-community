/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ide.structureView;

/**
 * The listener interface which allows the structure view to receive notifications about
 * changes to the editor selection.
 *
 * @see StructureViewModel#addEditorPositionListener(FileEditorPositionListener)
 * @see StructureViewModel#removeEditorPositionListener(FileEditorPositionListener)
 */
public interface FileEditorPositionListener {
  /**
   * Invoked when the selection in the editor linked to the structure view moves to
   * a different element visible in the structure view.
   */
  void onCurrentElementChanged();
}
