// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.util;

import com.intellij.diff.tools.util.KeyboardModifierListener;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class DiffGutterOperation {
  private final @NotNull RangeHighlighter myHighlighter;

  public DiffGutterOperation(@NotNull Editor editor, int offset) {
    myHighlighter = editor.getMarkupModel().addRangeHighlighter(null, offset, offset,
                                                                HighlighterLayer.ADDITIONAL_SYNTAX,
                                                                HighlighterTargetArea.LINES_IN_RANGE);
  }

  public void dispose() {
    myHighlighter.dispose();
  }

  public void update(boolean force) {
    if (!myHighlighter.isValid()) return;
    myHighlighter.setGutterIconRenderer(createRenderer());
  }

  protected abstract GutterIconRenderer createRenderer();

  public static int lineToOffset(@NotNull Editor editor, int line) {
    Document document = editor.getDocument();
    return line == DiffUtil.getLineCount(document) ? document.getTextLength() : document.getLineStartOffset(line);
  }

  public static final class Simple extends DiffGutterOperation {
    private final @NotNull RendererBuilder myBuilder;

    public Simple(@NotNull Editor editor, int offset,
                  @NotNull RendererBuilder builder) {
      super(editor, offset);
      myBuilder = builder;
      update(true);
    }

    @Override
    protected GutterIconRenderer createRenderer() {
      return myBuilder.createRenderer();
    }
  }

  public static final class WithModifiers extends DiffGutterOperation {
    private final @NotNull ModifiersRendererBuilder myBuilder;

    private final @NotNull KeyboardModifierListener myModifierProvider;
    private boolean myCtrlPressed;
    private boolean myShiftPressed;
    private boolean myAltPressed;

    public WithModifiers(@NotNull Editor editor, int offset,
                         @NotNull KeyboardModifierListener modifierProvider,
                         @NotNull ModifiersRendererBuilder builder) {
      super(editor, offset);
      myBuilder = builder;
      myModifierProvider = modifierProvider;
      update(true);
    }

    @Override
    public void update(boolean force) {
      boolean shouldUpdate = force ||
                             myCtrlPressed == myModifierProvider.isCtrlPressed() ||
                             myShiftPressed == myModifierProvider.isShiftPressed() ||
                             myAltPressed == myModifierProvider.isAltPressed();
      if (!shouldUpdate) return;

      myCtrlPressed = myModifierProvider.isCtrlPressed();
      myShiftPressed = myModifierProvider.isShiftPressed();
      myAltPressed = myModifierProvider.isAltPressed();

      super.update(force);
    }

    @Override
    protected GutterIconRenderer createRenderer() {
      return myBuilder.createRenderer(myCtrlPressed, myShiftPressed, myAltPressed);
    }
  }

  public interface RendererBuilder {
    @Nullable
    GutterIconRenderer createRenderer();
  }

  public interface ModifiersRendererBuilder {
    @Nullable
    GutterIconRenderer createRenderer(boolean ctrlPressed, boolean shiftPressed, boolean altPressed);
  }
}
