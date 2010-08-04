/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
 * Defines contract for the services that bring <code>'soft wrapping'</code> to the editor.
 * <p/>
 * <code>'Soft wrap'</code> here means representation of the line that exceeds right component viewport margin as if it is wrapped.
 * No changes are introduced to the actual document/file system during that, i.e. it's a pure <code>'view'</code> facility.
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
 * soft wrap (e.g. starts adding new call argument after <code>"test6"</code> at example above), that soft wraps becomes
 * <code>'hard wrap'</code>, i.e. virtual changes introduced by it are flushed to the underlying document.
 * <p/>
 * <b>Note:</b> soft wrap is assumed to provide as user-friendly indentation for those wrapped line as possible
 * (note that <code>"test6"</code> at example below is aligned to the parameters start).
 * <p/>
 * Implementations of this interface are not obliged to be thread-safe.
 *
 * @author Denis Zhdanov
 * @since Jun 8, 2010 3:15:18 PM
 */
public interface SoftWrapModel {

  /**
   * Allows to answer if <code>'soft wrap'</code> feature is enabled.
   *
   * @return    <code>true</code> if <code>'soft wraps'</code> are enabled; <code>false</code> otherwise
   */
  boolean isSoftWrappingEnabled();

  /**
   * Asks current model for the soft wrap registered for the given document offset if any.
   *
   * @param offset      target document offset
   * @return            soft wrap registered for the given offset within the current model if any; <code>null</code> otherwise
   */
  @Nullable
  TextChange getSoftWrap(int offset);

  /**
   * Allows to ask current model about all soft wraps registered for the given document offsets range.
   *
   * @param start   start document offset range to use (inclusive)
   * @param end     end document offset range to use (exclusive)
   * @return        all soft wraps registered for the target document offsets range
   */
  @NotNull
  List<? extends TextChange> getSoftWrapsForRange(int start, int end);

  /**
   * Allows to ask current model about all soft wraps registered for the given document line.
   *
   * @param documentLine    target document line
   * @return                all soft wraps registered for the given document line
   */
  @NotNull
  List<? extends TextChange> getSoftWrapsForLine(int documentLine);

  /**
   * Allows to answer if given soft wrap is shown.
   * <p/>
   * The soft wrap may be not shown if it's located, for example, inside collapsed folding region.
   *
   * @param softWrap    soft wrap to check
   * @return            <code>true</code> if given soft wrap is visible; <code>false</code> otherwise
   */
  boolean isVisible(TextChange softWrap);

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
   * @return            <code>true</code> if given visual position points to soft wrap-introduced virtual space;
   *                    <code>false</code> otherwise
   */
  boolean isInsideSoftWrap(@NotNull VisualPosition position);

  /**
   * Asks current model to calculate indent width of the given soft wrap in pixels.
   * <p/>
   * <code>'Soft wrap indent width'</code> here means visual width of soft wrap-introduced editor space on a line
   * that contains document text just after soft wrap. Basically, resulting value is a resulting width sum of soft
   * wrap symbols that follow last soft wrap line feed plus <code>'after soft wrap'</code> drawing width.
   *
   * @param softWrap    soft wrap which indent width should be calculated
   * @return            indent width in pixels for the given soft wrap
   */
  int getSoftWrapIndentWidthInPixels(@NotNull TextChange softWrap);

  /**
   * Asks current model to calculate indent width of the given soft wrap in {@link VisualPosition#column columns}.
   * <p/>
   * <code>'Soft wrap indent width'</code> here means number of soft wrap symbols that follow last soft wrap line
   * feed plus one symbol for <code>'after soft wrap'</code> drawing.
   *
   * @param softWrap    soft wrap which indent width should be calculated
   * @return            indent width in columns for the given soft wrap
   */
  int getSoftWrapIndentWidthInColumns(@NotNull TextChange softWrap);
}
