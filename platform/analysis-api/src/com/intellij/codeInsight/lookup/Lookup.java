// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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
   *
   * @implNote this method does not play well with {@link #getPsiFile()} and {@link #getPsiElement()} if completion is called in injected editor.
   */
  int getLookupStart();

  @Nullable
  LookupElement getCurrentItem();

  void addLookupListener(@NotNull LookupListener listener);
  void removeLookupListener(@NotNull LookupListener listener);

  /**
   * @return bounds in layered pane coordinate system
   */
  @NotNull Rectangle getBounds();

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
   * @implNote this method does not play well with {@link #getLookupStart()} if completion is called in injected editor.
   */
  @Nullable
  PsiFile getPsiFile();

  /**
   * Returns {@code true} if this lookup is used in completion mode, meaning that it's created as a result of completion action or autocompletion.
   * Lookup can be shown by other clients as well, e.g., refactorings, templates, etc. In this case, this method returns {@code false}.
   * @return {@code true} if this lookup is shown in completion mode, {@code false} otherwise.
   */
  boolean isCompletion();

  @Unmodifiable
  @NotNull List<LookupElement> getItems();

  boolean isFocused();

  @NotNull
  String itemPattern(@NotNull LookupElement element);

  @NotNull
  PrefixMatcher itemMatcher(@NotNull LookupElement item);

  boolean isSelectionTouched();

  @Unmodifiable
  List<String> getAdvertisements();
}
