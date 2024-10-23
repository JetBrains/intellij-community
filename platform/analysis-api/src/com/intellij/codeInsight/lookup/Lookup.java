// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.lookup;

import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.awt.*;
import java.util.List;

/**
 * Represents list with suggestions shown in code completion, refactorings, live templates etc.
 */
public interface Lookup {
  char NORMAL_SELECT_CHAR = '\n';
  char REPLACE_SELECT_CHAR = '\t';
  char COMPLETE_STATEMENT_SELECT_CHAR = '\r';
  char AUTO_INSERT_SELECT_CHAR = (char) 0;
  ColorKey LOOKUP_COLOR = ColorKey.createColorKey("LOOKUP_COLOR");

  /**
   * @return the offset in {@link #getTopLevelEditor()} which this lookup's left side should be aligned with. Note that if the lookup doesn't fit
   * the screen due to its dimensions, the actual position might differ from this editor offset.
   */
  int getLookupStart();

  @Nullable
  LookupElement getCurrentItem();

  void addLookupListener(LookupListener listener);
  void removeLookupListener(LookupListener listener);

  /**
   * @return bounds in layered pane coordinate system
   */
  Rectangle getBounds();

  /**
   * @return bounds of the current item in the layered pane coordinate system.
   */
  Rectangle getCurrentItemBounds();
  boolean isPositionedAboveCaret();

  /**
   * @return leaf PSI element at this lookup's start position (see {@link #getLookupStart()}) in {@link #getPsiFile()} result.
   */
  @Nullable
  PsiElement getPsiElement();

  /**
   * Consider using {@link #getTopLevelEditor()} if you don't need injected editor.
   * @return editor, possibly injected, where this lookup is shown
   */
  @NotNull
  Editor getEditor();

  /**
   * @return the non-injected editor where this lookup is shown
   */
  @NotNull
  Editor getTopLevelEditor();

  @NotNull
  Project getProject();

  /**
   * @return PSI file, possibly injected, associated with this lookup's editor
   * @see #getEditor()
   */
  @Nullable
  PsiFile getPsiFile();

  boolean isCompletion();

  @Unmodifiable
  List<LookupElement> getItems();

  boolean isFocused();

  @NotNull
  String itemPattern(@NotNull LookupElement element);

  @NotNull
  PrefixMatcher itemMatcher(@NotNull LookupElement item);

  boolean isSelectionTouched();

  List<String> getAdvertisements();
}
