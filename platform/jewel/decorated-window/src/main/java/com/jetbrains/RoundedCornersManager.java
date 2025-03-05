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

/**
 * This manager allows decorated AWT Window with rounded corners.
 * The appearance depends on the operating system.
 */
public interface RoundedCornersManager {
    /**
     * Sets rounded corners on the target {@link Window}.
     *
     * @param params For macOS, it is a {@code Float} object with the radius or an Array containing:
     *               <ul>
     *               <li>A {@code Float} for the radius</li>
     *               <li>An {@code Integer} for the border width</li>
     *               <li>A {@code java.awt.Color} for the border color</li>
     *               </ul>
     *               <br/>
     *               For Windows 11, it is a {@code String} with one of these values:
     *               <ul>
     *               <li>{@code "default"} — let the system decide whether to round window corners</li>
     *               <li>{@code "none"} — never round window corners</li>
     *               <li>{@code "full"} — round the corners if appropriate</li>
     *               <li>{@code "small"} — round the corners if appropriate, with a small radius</li>
     *               </ul>
     *               <br/>
     */
    void setRoundedCorners(Window window, Object params);
}
