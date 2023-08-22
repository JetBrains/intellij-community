// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.openapi.editor;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Indent guides are vertical lines that visualize the indentation level of
 * parts of the text.
 */
public interface IndentsModel {
  @Nullable
  IndentGuideDescriptor getCaretIndentGuide();

  /**
   * Tries to return a descriptor (if any) that defines indent guide for the given lines.
   *
   * @param startLine   logical line where target indent guide is started
   * @param endLine     logical line where target indent guide is ended
   * @return            indent guide descriptor registered for the given lines at the current model previously if any;
   *                    {@code null} otherwise
   */
  @Nullable
  IndentGuideDescriptor getDescriptor(int startLine, int endLine);

  void assumeIndents(List<IndentGuideDescriptor> descriptors);

}
