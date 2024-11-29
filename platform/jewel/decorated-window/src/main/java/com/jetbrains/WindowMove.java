/*
 * Copyright 2000-2023 JetBrains s.r.o.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
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

public interface WindowMove {
    /**
     * Starts moving the top-level parent window of the given window together with the mouse pointer.
     * The intended use is to facilitate the implementation of window management similar to the way
     * it is done natively on the platform.
     *
     * Preconditions for calling this method:
     * <ul>
     * <li>WM supports _NET_WM_MOVE_RESIZE (this is checked automatically when an implementation
     *     of this interface is obtained).</li>
     * <li>Mouse pointer is within this window's bounds.</li>
     * <li>The mouse button specified by {@code mouseButton} is pressed.</li>
     * </ul>
     *
     * Calling this method will make the window start moving together with the mouse pointer until
     * the specified mouse button is released or Esc is pressed. The conditions for cancelling
     * the move may differ between WMs.
     *
     * @param mouseButton indicates the mouse button that was pressed to start moving the window;
     *                   must be one of {@code MouseEvent.BUTTON1}, {@code MouseEvent.BUTTON2},
     *                   or {@code MouseEvent.BUTTON3}.
     */
    void startMovingTogetherWithMouse(Window window, int mouseButton);
}
