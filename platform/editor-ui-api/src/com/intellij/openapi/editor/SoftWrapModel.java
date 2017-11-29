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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Defines contract for the services that bring {@code 'soft wrapping'} to the editor.
 * <p/>
 * {@code 'Soft wrap'} here means representation of the line that exceeds right component viewport margin as if it is wrapped.
 * No changes are introduced to the actual document/file system during that, i.e. it's a pure {@code 'view'} facility.
 * <p/>
 * <b>Example:</b>
 * <p/>
 * <b>Raw text (as stored at file system)</b>
 * <p/>
 * <pre>
 *     public class Test {                                         | &lt;- right margin
 *                                                                 |
 *         public void baz() {                                     |
 *             foo("test1", "test2", "test3", "test4", "test5", "test6");
 *         }                                                       |
 *                                                                 |
 *         public void foo(Object ... args) {                      |
 *         }                                                       |
 *     }                                                           |
 *      </pre>
 * <p/>
 * <b>The same text with soft wraps as shown to end-user at editor</b>
 * <p/>
 * <pre>
 *     public class Test {                                         | &lt;- right margin
 *                                                                 |
 *         public void baz() {                                     |
 *             foo("test1", "test2", "test3", "test4", "test5",    |
 *                 "test6");                                       |
 *         }                                                       |
 *                                                                 |
 *         public void foo(Object ... args) {                      |
 *         }                                                       |
 *     }                                                           |
 *      </pre>
 * <p/>
 * Another important soft wrap feature is that as soon as the user starts typing on a line which representation is affected by
 * soft wrap (e.g. starts adding new call argument after {@code "test6"} at example above), that soft wraps becomes
 * {@code 'hard wrap'}, i.e. virtual changes introduced by it are flushed to the underlying document.
 * <p/>
 * <b>Note:</b> soft wrap is assumed to provide as user-friendly indentation for those wrapped line as possible
 * (note that {@code "test6"} at example below is aligned to the parameters start).
 * <p/>
 * Implementations of this interface are not obliged to be thread-safe.
 *
 * @author Denis Zhdanov
 * @since Jun 8, 2010 3:15:18 PM
 */
public interface SoftWrapModel {

  /**
   * Allows to answer if {@code 'soft wrap'} feature is enabled.
   *
   * @return    {@code true} if {@code 'soft wraps'} are enabled; {@code false} otherwise
   */
  boolean isSoftWrappingEnabled();

  /**
   * Asks current model for the soft wrap registered for the given document offset if any.
   *
   * @param offset      target document offset
   * @return            soft wrap registered for the given offset within the current model if any; {@code null} otherwise
   */
  @Nullable
  SoftWrap getSoftWrap(int offset);

  /**
   * Allows to ask current model about all soft wraps registered for the given document offsets range.
   *
   * @param start   start document offset range to use (inclusive)
   * @param end     end document offset range to use (inclusive)
   * @return        all soft wraps registered for the target document offsets range
   */
  @NotNull
  List<? extends SoftWrap> getSoftWrapsForRange(int start, int end);

  /**
   * Allows to ask current model about all soft wraps registered for the given document line.
   *
   * @param documentLine    target document line
   * @return                all soft wraps registered for the given document line
   */
  @NotNull
  List<? extends SoftWrap> getSoftWrapsForLine(int documentLine);

  /**
   * Allows to answer if given soft wrap is shown.
   * <p/>
   * The soft wrap may be not shown if it's located, for example, inside collapsed folding region.
   *
   * @param softWrap    soft wrap to check
   * @return            {@code true} if given soft wrap is visible; {@code false} otherwise
   */
  boolean isVisible(SoftWrap softWrap);

  /**
   * Notifies current model that target document is about to be changed at current caret location.
   * <p/>
   * Primary purpose of this method is to perform {@code 'soft wrap' -> 'hard wrap'} conversion if the user types in virtual
   * soft wraps-introduced space.
   */
  void beforeDocumentChangeAtCaret();

  /**
   * Allows to answer if given visual position points to soft wrap-introduced virtual space.
   *
   * @param position    target visual position to check
   * @return            {@code true} if given visual position points to soft wrap-introduced virtual space;
   *                    {@code false} otherwise
   */
  boolean isInsideSoftWrap(@NotNull VisualPosition position);

  /**
   * Allows to answer if given visual position points to soft wrap-introduced virtual space or points just before soft wrap.
   *
   * @param visual    target visual position to check
   * @return          {@code true} if given visual position points to soft wrap-introduced virtual space;
   *                  {@code false} otherwise
   */
  boolean isInsideOrBeforeSoftWrap(@NotNull VisualPosition visual);

  /**
   * Callback method to ask soft wrap model to release all resources.
   */
  void release();
}
