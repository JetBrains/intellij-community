/*
 * Copyright 2000-2018 JetBrains s.r.o.
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
package com.intellij.ui.paint;

import com.intellij.util.ui.RegionPainter;

import java.awt.*;

/**
 * @author Sergey.Malenkov
 */
public enum EffectPainter implements RegionPainter<Font> {
  /**
   * @see com.intellij.openapi.editor.markup.EffectType#LINE_UNDERSCORE
   */
  LINE_UNDERSCORE {
    /**
     * Draws a horizontal line under a text.
     *
     * @param g      the {@code Graphics2D} object to render to
     * @param x      text position
     * @param y      text baseline
     * @param width  text width
     * @param height available space under text
     * @param font   optional font to calculate line metrics
     */
    @Override
    public void paint(Graphics2D g, int x, int y, int width, int height, Font font) {
      EffectPainter2D.LINE_UNDERSCORE.paint(g, x, y, width, height, font);
    }
  },
  /**
   * @see com.intellij.openapi.editor.markup.EffectType#BOLD_LINE_UNDERSCORE
   */
  BOLD_LINE_UNDERSCORE {
    /**
     * Draws a bold horizontal line under a text.
     *
     * @param g      the {@code Graphics2D} object to render to
     * @param x      text position
     * @param y      text baseline
     * @param width  text width
     * @param height available space under text
     * @param font   optional font to calculate line metrics
     */
    @Override
    public void paint(Graphics2D g, int x, int y, int width, int height, Font font) {
      EffectPainter2D.BOLD_LINE_UNDERSCORE.paint(g, x, y, width, height, font);
    }
  },
  /**
   * @see com.intellij.openapi.editor.markup.EffectType#BOLD_DOTTED_LINE
   */
  BOLD_DOTTED_UNDERSCORE {
    /**
     * Draws a bold horizontal line of dots under a text.
     *
     * @param g      the {@code Graphics2D} object to render to
     * @param x      text position
     * @param y      text baseline
     * @param width  text width
     * @param height available space under text
     * @param font   optional font to calculate line metrics
     */
    @Override
    public void paint(Graphics2D g, int x, int y, int width, int height, Font font) {
      EffectPainter2D.BOLD_DOTTED_UNDERSCORE.paint(g, x, y, width, height, font);
    }
  },
  /**
   * @see com.intellij.openapi.editor.markup.EffectType#WAVE_UNDERSCORE
   */
  WAVE_UNDERSCORE {
    /**
     * Draws a horizontal wave under a text.
     *
     * @param g      the {@code Graphics2D} object to render to
     * @param x      text position
     * @param y      text baseline
     * @param width  text width
     * @param height available space under text
     * @param font   optional font to calculate line metrics
     */
    @Override
    public void paint(Graphics2D g, int x, int y, int width, int height, Font font) {
      EffectPainter2D.WAVE_UNDERSCORE.paint(g, x, y, width, height, font);
    }
  },
  /**
   * @see com.intellij.openapi.editor.markup.EffectType#STRIKEOUT
   */
  STRIKE_THROUGH {
    /**
     * Draws a horizontal line through a text.
     *
     * @param g      the {@code Graphics2D} object to render to
     * @param x      text position
     * @param y      text baseline
     * @param width  text width
     * @param height text height
     * @param font   optional font to calculate line metrics
     */
    @Override
    public void paint(Graphics2D g, int x, int y, int width, int height, Font font) {
      EffectPainter2D.STRIKE_THROUGH.paint(g, x, y, width, height, font);
    }
  }
}
