// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

/**
 * Priorities for different types of 'block' visual elements in editor.
 *
 * @see InlayModel#addBlockElement(int, boolean, boolean, int, EditorCustomElementRenderer)
 */
public interface BlockInlayPriority {
  int DOC_RENDER = -300;
  int CODE_AUTHOR = -200;
  int CODE_VISION_INHERITORS = -100;
  int CODE_VISION_USAGES = 0;
  int PROBLEMS = 100;
  int ANNOTATIONS = 200;
}
