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
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.ex.PrioritizedDocumentListener;

/**
 * Holds values to use for common {@link PrioritizedDocumentListener prioritized document listeners} used within standard IntelliJ
 * editor.
 *
 * @author Denis Zhdanov
 * @since Sep 13, 2010 2:30:48 PM
 */
public class EditorDocumentPriorities {

  /**
   * Assuming that range marker listeners work only with document offsets and don't perform document dimension mappings like
   * {@code 'logical position -> visual position'}, {@code 'offset -> logical position'} etc.
   */
  public static final int RANGE_MARKER = 40;

  public static final int FOLD_MODEL = 60;
  public static final int LOGICAL_POSITION_CACHE = 65;
  public static final int EDITOR_TEXT_LAYOUT_CACHE = 70;
  public static final int LEXER_EDITOR = 80;
  public static final int SOFT_WRAP_MODEL = 100;
  public static final int EDITOR_TEXT_WIDTH_CACHE = 110;
  public static final int CARET_MODEL = 120;
  public static final int INLAY_MODEL = 150;
  public static final int EDITOR_DOCUMENT_ADAPTER = 160;

  private EditorDocumentPriorities() {
  }
}
