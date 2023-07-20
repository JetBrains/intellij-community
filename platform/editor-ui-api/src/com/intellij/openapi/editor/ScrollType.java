/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.editor;

// TODO: usage recommendations
public enum ScrollType {
  /**
   * <strong>Behavior for vertical scrolling:</strong>
   * <ul>
   *   <li>
   *     If the scrolling target location is higher than the current viewport, then after scrolling, the target location will be positioned at the top edge of the viewport.
   *   </li>
   *   <li>
   *     If the scrolling target location is lower than the current viewport, then after scrolling, the target location will be positioned at the bottom edge of the viewport.
   *   </li>
   * </ul>
   * <strong>Behavior for horizontal scrolling:</strong>
   * <ul>
   *   <li>
   *     If the scrolling target location is to the left of the current viewport, then after scrolling, the target location will be positioned at the left edge of the viewport.
   *   </li>
   *   <li>
   *     If the scrolling target location is to the right of the current viewport, then after scrolling, the target location will be positioned at the right edge of the viewport.
   *   </li>
   * </ul>
   */
  RELATIVE,
  /**
   * <strong>Behavior for vertical scrolling:</strong>
   * <p>If the target location is outside the viewport, then it will be vertically centered.</p><br>
   * <strong>Behavior for horizontal scrolling:</strong>
   * <ul>
   *   <li>
   *     If the scrolling target location is to the left of the current viewport, then after scrolling, the target location will either:
   *     <ul>
   *       <li>Be at the leftmost position when possible (viewport will contain target location)</li>
   *       <li>Be at the left edge of the viewport.</li>
   *     </ul>
   *   </li>
   *   <li>
   *     If the scrolling target location is to the right of the current viewport, then after scrolling, the target location will be positioned at the right edge of the viewport.
   *   </li>
   * </ul>
   */
  MAKE_VISIBLE,
  /**
   * <strong>Behavior for vertical scrolling:</strong>
   * <p>Vertically centers the target location.</p><br>
   * <strong>Behavior for horizontal scrolling:</strong>
   * <ul>
   *   <li>
   *     If the scrolling target location is to the left of the current viewport, then after scrolling, the target location will be positioned at the left edge of the viewport.
   *   </li>
   *   <li>
   *     If the scrolling target location is to the right of the current viewport, then after scrolling, the target location will be positioned at the right edge of the viewport.
   *   </li>
   * </ul>
   */
  CENTER,
  /**
   * <strong>Behavior for vertical scrolling:</strong>
   * <p>Vertically centers the target location, if the target location is outside the viewport or in its upper half.</p><br>
   * <strong>Behavior for horizontal scrolling:</strong>
   * <ul>
   *   <li>
   *     If the scrolling target location is to the left of the current viewport, then after scrolling, the target location will be positioned at the left edge of the viewport.
   *   </li>
   *   <li>
   *     If the scrolling target location is to the right of the current viewport, then after scrolling, the target location will be positioned at the right edge of the viewport.
   *   </li>
   * </ul>
   */
  CENTER_UP,
  /**
   * <strong>Behavior for vertical scrolling:</strong>
   * <p>Vertically centers the target location, if the target location is outside the viewport or in its lower half.</p><br>
   * <strong>Behavior for horizontal scrolling:</strong>
   * <ul>
   *   <li>
   *     If the scrolling target location is to the left of the current viewport, then after scrolling, the target location will be positioned at the left edge of the viewport.
   *   </li>
   *   <li>
   *     If the scrolling target location is to the right of the current viewport, then after scrolling, the target location will be positioned at the right edge of the viewport.
   *   </li>
   * </ul>
   */
  CENTER_DOWN,
}
