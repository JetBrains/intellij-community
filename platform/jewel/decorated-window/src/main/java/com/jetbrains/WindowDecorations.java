/*
 * Copyright 2023 JetBrains s.r.o.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation. Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.jetbrains;

import java.awt.*;
import java.util.Map;

/**
 * Window decorations consist of title bar, window controls and border.
 * @see CustomTitleBar
 */
public interface WindowDecorations {

    /**
     * If {@code customTitleBar} is not null, system-provided title bar is removed and client area is extended to the
     * top of the frame with window controls painted over the client area.
     * {@code customTitleBar=null} resets to the default appearance with system-provided title bar.
     * @see CustomTitleBar
     * @see #createCustomTitleBar()
     */
    void setCustomTitleBar(Frame frame, CustomTitleBar customTitleBar);

    /**
     * If {@code customTitleBar} is not null, system-provided title bar is removed and client area is extended to the
     * top of the dialog with window controls painted over the client area.
     * {@code customTitleBar=null} resets to the default appearance with system-provided title bar.
     * @see CustomTitleBar
     * @see #createCustomTitleBar()
     */
    void setCustomTitleBar(Dialog dialog, CustomTitleBar customTitleBar);

    /**
     * You must {@linkplain CustomTitleBar#setHeight(float) set title bar height} before adding it to a window.
     * @see CustomTitleBar
     * @see #setCustomTitleBar(Frame, CustomTitleBar)
     * @see #setCustomTitleBar(Dialog, CustomTitleBar)
     */
    CustomTitleBar createCustomTitleBar();

    /**
     * Custom title bar allows merging of window content with native title bar,
     * which is done by treating title bar as part of client area, but with some
     * special behavior like dragging or maximizing on double click.
     * Custom title bar has {@linkplain CustomTitleBar#getHeight()  height} and controls.
     * @implNote Behavior is platform-dependent, only macOS and Windows are supported.
     * @see #setCustomTitleBar(Frame, CustomTitleBar)
     */
    interface CustomTitleBar {

        /**
         * @return title bar height, measured in pixels from the top of client area, i.e. excluding top frame border.
         */
        float getHeight();

        /**
         * @param height title bar height, measured in pixels from the top of client area,
         *               i.e. excluding top frame border. Must be > 0.
         */
        void setHeight(float height);

        /**
         * @see #putProperty(String, Object)
         */
        Map<String, Object> getProperties();

        /**
         * @see #putProperty(String, Object)
         */
        void putProperties(Map<String, ?> m);

        /**
         * Windows & macOS properties:
         * <ul>
         *     <li>{@code controls.visible} : {@link Boolean} - whether title bar controls
         *         (minimize/maximize/close buttons) are visible, default = true.</li>
         * </ul>
         * Windows properties:
         * <ul>
         *     <li>{@code controls.width} : {@link Number} - width of block of buttons (not individual buttons).
         *         Note that dialogs have only one button, while frames usually have 3 of them.</li>
         *     <li>{@code controls.dark} : {@link Boolean} - whether to use dark or light color theme
         *         (light or dark icons respectively).</li>
         *     <li>{@code controls.<layer>.<state>} : {@link Color} - precise control over button colors,
         *         where {@code <layer>} is one of:
         *         <ul><li>{@code foreground}</li><li>{@code background}</li></ul>
         *         and {@code <state>} is one of:
         *         <ul>
         *             <li>{@code normal}</li>
         *             <li>{@code hovered}</li>
         *             <li>{@code pressed}</li>
         *             <li>{@code disabled}</li>
         *             <li>{@code inactive}</li>
         *         </ul>
         * </ul>
         */
        void putProperty(String key, Object value);

        /**
         * @return space occupied by title bar controls on the left (px)
         */
        float getLeftInset();
        /**
         * @return space occupied by title bar controls on the right (px)
         */
        float getRightInset();

        /**
         * By default, any component which has no cursor or mouse event listeners set is considered transparent for
         * native title bar actions. That is, dragging simple JPanel in title bar area will drag the
         * window, but dragging a JButton will not. Adding mouse listener to a component will prevent any native actions
         * inside bounds of that component.
         * <p>
         * This method gives you precise control of whether to allow native title bar actions or not.
         * <ul>
         *     <li>{@code client=true} means that mouse is currently over a client area. Native title bar behavior is disabled.</li>
         *     <li>{@code client=false} means that mouse is currently over a non-client area. Native title bar behavior is enabled.</li>
         * </ul>
         * <em>Intended usage:
         * <ul>
         *     <li>This method must be called in response to all {@linkplain java.awt.event.MouseEvent mouse events}
         *         except {@link java.awt.event.MouseEvent#MOUSE_EXITED} and {@link java.awt.event.MouseEvent#MOUSE_WHEEL}.</li>
         *     <li>This method is called per-event, i.e. when component has multiple listeners, you only need to call it once.</li>
         *     <li>If this method hadn't been called, title bar behavior is reverted back to default upon processing the event.</li>
         * </ul></em>
         * Note that hit test value is relevant only for title bar area, e.g. calling
         * {@code forceHitTest(false)} will not make window draggable via non-title bar area.
         *
         * <h2>Example:</h2>
         * Suppose you have a {@code JPanel} in the title bar area. You want it to respond to right-click for
         * some popup menu, but also retain native drag and double-click behavior.
         * <pre>
         *     CustomTitleBar titlebar = ...;
         *     JPanel panel = ...;
         *     MouseAdapter adapter = new MouseAdapter() {
         *         private void hit() { titlebar.forceHitTest(false); }
         *         public void mouseClicked(MouseEvent e) {
         *             hit();
         *             if (e.getButton() == MouseEvent.BUTTON3) ...;
         *         }
         *         public void mousePressed(MouseEvent e) { hit(); }
         *         public void mouseReleased(MouseEvent e) { hit(); }
         *         public void mouseEntered(MouseEvent e) { hit(); }
         *         public void mouseDragged(MouseEvent e) { hit(); }
         *         public void mouseMoved(MouseEvent e) { hit(); }
         *     };
         *     panel.addMouseListener(adapter);
         *     panel.addMouseMotionListener(adapter);
         * </pre>
         */
        void forceHitTest(boolean client);

        Window getContainingWindow();
    }
}
