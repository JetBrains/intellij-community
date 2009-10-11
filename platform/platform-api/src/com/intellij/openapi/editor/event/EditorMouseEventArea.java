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
package com.intellij.openapi.editor.event;

import org.jetbrains.annotations.NonNls;

public class EditorMouseEventArea {
  private final String myDebugName;

  public static final EditorMouseEventArea EDITING_AREA = new EditorMouseEventArea("EDITING_AREA");
  public static final EditorMouseEventArea LINE_NUMBERS_AREA = new EditorMouseEventArea("LINE_NUMBERS_AREA");
  public static final EditorMouseEventArea ANNOTATIONS_AREA = new EditorMouseEventArea("ANNOTATIONS_AREA");
  public static final EditorMouseEventArea LINE_MARKERS_AREA = new EditorMouseEventArea("LINE_MARKERS_AREA");
  public static final EditorMouseEventArea FOLDING_OUTLINE_AREA = new EditorMouseEventArea("FOLDING_OUTLINE_AREA");

  private EditorMouseEventArea(@NonNls String debugName) {
    myDebugName = debugName;
  }

  public String toString() {
    return myDebugName;
  }
}
