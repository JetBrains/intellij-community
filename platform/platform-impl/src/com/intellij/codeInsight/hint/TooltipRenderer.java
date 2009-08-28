package com.intellij.codeInsight.hint;

import com.intellij.openapi.editor.Editor;
import com.intellij.ui.LightweightHint;

import java.awt.*;

/**
 * @author cdr
 */
public interface TooltipRenderer {
  LightweightHint show(final Editor editor, Point p, boolean alignToRight, TooltipGroup group);
}
