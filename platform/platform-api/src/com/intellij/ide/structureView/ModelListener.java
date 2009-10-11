/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
 * The listener interface which allows the structure view to receive notification about
 * changes to data shown in the structure view.
 *
 * @see StructureViewModel#addModelListener(ModelListener)
 * @see StructureViewModel#removeModelListener(ModelListener)
 */
public interface ModelListener {
  /**
   * Invoked when the data represented by the structure view
   * is changed and the structure view needs to be rebuilt.
   */
  void onModelChanged();
}
