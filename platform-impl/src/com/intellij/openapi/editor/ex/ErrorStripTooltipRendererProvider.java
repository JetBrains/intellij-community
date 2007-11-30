/*
 * @author max
 */
package com.intellij.openapi.editor.ex;

import com.intellij.codeInsight.hint.TooltipRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public interface ErrorStripTooltipRendererProvider {
  @Nullable
  TooltipRenderer calcTooltipRenderer(@NotNull Collection<RangeHighlighter> highlighters);
  TooltipRenderer calcTooltipRenderer(@NotNull String text);
  TooltipRenderer calcTooltipRenderer(@NotNull String text, int width);
}