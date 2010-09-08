/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.editor;

/**
 * Defines common interface for <code>'soft wrap'</code>, i.e. for virtual line break that doesn't present at actual file on a disk
 * but is used exclusively during document representation.
 *
 * @author Denis Zhdanov
 * @since Aug 30, 2010 6:07:00 PM
 */
public interface SoftWrap extends TextChange {

  /**
   * @return    number of columns between current soft wrap end and first column on a visual line
   */
  int getIndentInColumns();

  /**
   * @return    number of pixels between current soft wrap end and first column on a visual line
   */
  int getIndentInPixels();
}
