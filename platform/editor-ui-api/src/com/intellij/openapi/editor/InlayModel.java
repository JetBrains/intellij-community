// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor;

import com.intellij.openapi.Disposable;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.EventListener;
import java.util.List;

/**
 * Keeps track of all inlays of a single editor.
 * <p>
 * Inlays are visual additions to the editor that are not reflected in the editor's document.
 * See {@link Inlay} for the available types of inlays.
 *
 * @see Editor#getInlayModel()
 * @see Inlay
 */
public interface InlayModel {
  /**
   * Adds an inline inlay at the given offset that is associated with the text that follows it.
   * Inline inlays are inserted between characters of a line.
   *
   * @see #addInlineElement(int, boolean, EditorCustomElementRenderer)
   */
  default <T extends EditorCustomElementRenderer>
  @Nullable Inlay<T> addInlineElement(int offset, @NotNull T renderer) {
    return addInlineElement(offset, false, renderer);
  }

  /**
   * Adds an inline inlay at the given offset.
   * Inline inlays are inserted between characters of a line.
   *
   * @param relatesToPrecedingText whether the inlay is associated with the preceding or the following text,
   *                               see {@link InlayProperties#relatesToPrecedingText(boolean)}
   * @param renderer               defines the width and appearance of the inlay
   * @return {@code null} if the inlay cannot be created, for example when the editor doesn't support its functionality
   */
  <T extends EditorCustomElementRenderer>
  @Nullable Inlay<T> addInlineElement(int offset, boolean relatesToPrecedingText, @NotNull T renderer);

  /**
   * Adds an inline inlay at the given offset.
   * Inline inlays are inserted between characters of a line.
   *
   * @param relatesToPrecedingText whether the inlay is associated with the preceding or the following text,
   *                               see {@link InlayProperties#relatesToPrecedingText(boolean)}
   * @param priority               if multiple inlays are requested to be displayed for the same position,
   *                               this argument defines the relative positioning of such inlays
   *                               (larger priority value means the inlay will be rendered closer to the left)
   * @param renderer               defines the width and appearance of the inlay
   * @return {@code null} if the inlay cannot be created, for example when the editor doesn't support its functionality
   */
  <T extends EditorCustomElementRenderer>
  @Nullable Inlay<T> addInlineElement(int offset, boolean relatesToPrecedingText, int priority, @NotNull T renderer);

  /**
   * Adds an inline inlay at the given offset.
   * Inline inlays are inserted between characters of a line.
   *
   * @param renderer               defines the width and appearance of the inlay
   * @return {@code null} if the inlay cannot be created, for example when the editor doesn't support its functionality
   */
  <T extends EditorCustomElementRenderer>
  @Nullable Inlay<T> addInlineElement(int offset, @NotNull InlayProperties properties, @NotNull T renderer);

  /**
   * Adds a block inlay at the given offset.
   * Block inlays are inserted between lines.
   *
   * @param relatesToPrecedingText whether the inlay is associated with the preceding or the following text,
   *                               see {@link InlayProperties#relatesToPrecedingText(boolean)}
   * @param showAbove              whether the inlay is displayed above or below its corresponding visual line
   * @param priority               if multiple inlays are requested to be displayed for the same position,
   *                               this argument defines the relative positioning of such inlays
   *                               (larger priority value means the inlay will be rendered closer to the left)
   * @param renderer               defines the size and appearance of the inlay
   * @return {@code null} if the inlay cannot be created, for example when the editor doesn't support its functionality
   * @see BlockInlayPriority
   */
  <T extends EditorCustomElementRenderer>
  @Nullable Inlay<T> addBlockElement(int offset, boolean relatesToPrecedingText, boolean showAbove, int priority, @NotNull T renderer);

  /**
   * Adds a block inlay at the given offset.
   * Block inlays are inserted between lines.
   *
   * @param renderer defines the size and appearance of the inlay
   * @return {@code null} if the inlay cannot be created, for example when the editor doesn't support its functionality
   */
  <T extends EditorCustomElementRenderer>
  @Nullable Inlay<T> addBlockElement(int offset, @NotNull InlayProperties properties, @NotNull T renderer);


  /**
   * Adds an after-line-end inlay at the given offset.
   * After-line-end inlays are appended to the logical line.
   *
   * @param relatesToPrecedingText whether the inlay is associated with the preceding or the following text,
   *                               see {@link InlayProperties#relatesToPrecedingText(boolean)}
   * @param renderer               defines the width and appearance of the inlay
   * @return {@code null} if the inlay cannot be created, for example when the editor doesn't support its functionality
   */
  <T extends EditorCustomElementRenderer>
  @Nullable Inlay<T> addAfterLineEndElement(int offset, boolean relatesToPrecedingText, @NotNull T renderer);

  /**
   * Adds an after-line-end inlay at the given offset.
   * After-line-end inlays are appended to the logical line.
   *
   * @param renderer defines the width and appearance of the inlay
   * @return {@code null} if the inlay cannot be created, for example when the editor doesn't support its functionality
   */
  <T extends EditorCustomElementRenderer>
  @Nullable Inlay<T> addAfterLineEndElement(int offset, @NotNull InlayProperties properties, @NotNull T renderer);

  /**
   * Returns the inline inlays for a given offset range (both limits are inclusive).
   * The returned list is sorted by offset.
   * Both visible and invisible (due to folding) inlays are returned.
   */
  @NotNull List<Inlay<?>> getInlineElementsInRange(int startOffset, int endOffset);

  /**
   * Returns the inline inlays for a given offset range (both limits are inclusive).
   * The returned list contains only inlays whose renderer is of the given type.
   * The returned list is sorted by offset.
   * Both visible and invisible (due to folding) inlays are returned.
   */
  default <T> List<Inlay<? extends T>> getInlineElementsInRange(int startOffset, int endOffset, @NotNull Class<T> type) {
    //noinspection unchecked,rawtypes
    return (List)ContainerUtil.filter(getInlineElementsInRange(startOffset, endOffset), inlay -> type.isInstance(inlay.getRenderer()));
  }

  /**
   * Returns the block inlays for a given offset range (both limits are inclusive).
   * The returned list is sorted by priority (higher priority first).
   * Both visible and invisible (due to folding) inlays are returned.
   */
  @NotNull List<Inlay<?>> getBlockElementsInRange(int startOffset, int endOffset);

  /**
   * Returns the block inlays for a given offset range (both limits are inclusive).
   * The returned list contains only inlays whose renderer is of the given type.
   * The returned list is sorted by priority (higher priority first).
   * Both visible and invisible (due to folding) inlays are returned.
   */
  default <T> List<Inlay<? extends T>> getBlockElementsInRange(int startOffset, int endOffset, @NotNull Class<T> type) {
    //noinspection unchecked,rawtypes
    return (List)ContainerUtil.filter(getBlockElementsInRange(startOffset, endOffset), inlay -> type.isInstance(inlay.getRenderer()));
  }

  /**
   * Returns the block inlays that are displayed for a given visual line.
   * The returned list is sorted in appearance order (top to bottom).
   * Only visible (not folded) inlays are returned.
   */
  @NotNull List<Inlay<?>> getBlockElementsForVisualLine(int visualLine, boolean above);

  /**
   * Tells whether there exists at least one block inlay currently.
   */
  default boolean hasBlockElements() {
    return !getBlockElementsInRange(0, Integer.MAX_VALUE).isEmpty();
  }

  /**
   * Tells whether the given range of offsets (both sides inclusive) contains at least one inline inlay.
   */
  default boolean hasInlineElementsInRange(int startOffset, int endOffset) {
    return !getInlineElementsInRange(startOffset, endOffset).isEmpty();
  }

  /**
   * Tells whether there exists at least one inline inlay currently.
   */
  default boolean hasInlineElements() {
    return hasInlineElementsInRange(0, Integer.MAX_VALUE);
  }

  /**
   * Tells whether there exists an inline inlay at the given offset.
   */
  boolean hasInlineElementAt(int offset);

  /**
   * Tells whether there exists an inline inlay at the given visual position.
   * Only the visual position to the left of the inlay is recognized.
   */
  default boolean hasInlineElementAt(@NotNull VisualPosition visualPosition) {
    return getInlineElementAt(visualPosition) != null;
  }

  /**
   * Returns an inline inlay at a given visual position.
   * Only the visual position to the left of the inlay is recognized.
   */
  @Nullable Inlay getInlineElementAt(@NotNull VisualPosition visualPosition);

  /**
   * Returns an inlay at the given coordinates in the editor's coordinate space,
   * or {@code null} if there's no inlay at the given point.
   */
  @Nullable Inlay getElementAt(@NotNull Point point);

  /**
   * Returns an inlay at the given coordinates in the editor's coordinate space
   * whose renderer is of the given type,
   * or {@code null} if there's no such inlay at the given point.
   */
  default <T> @Nullable Inlay<? extends T> getElementAt(@NotNull Point point, @NotNull Class<T> type) {
    //noinspection unchecked
    Inlay<? extends T> inlay = getElementAt(point);
    return inlay != null && type.isInstance(inlay.getRenderer()) ? inlay : null;
  }

  /**
   * Returns the after-line-end inlays for the given offset range (both limits are inclusive).
   * The returned list is sorted by offset.
   * Both visible and invisible (due to folding) inlays are returned.
   *
   * @see #addAfterLineEndElement(int, boolean, EditorCustomElementRenderer)
   */
  @NotNull List<Inlay<?>> getAfterLineEndElementsInRange(int startOffset, int endOffset);

  /**
   * Returns the after-line-end inlays for the given offset range (both limits are inclusive).
   * The returned list contains only inlays whose renderer is of the given type.
   * The returned list is sorted by offset.
   * Both visible and invisible (due to folding) inlays are returned.
   *
   * @see #addAfterLineEndElement(int, boolean, EditorCustomElementRenderer)
   */
  default <T> @NotNull List<Inlay<? extends T>> getAfterLineEndElementsInRange(int startOffset, int endOffset, @NotNull Class<T> type) {
    //noinspection unchecked,rawtypes
    return (List)ContainerUtil.filter(getAfterLineEndElementsInRange(startOffset, endOffset),
                                      inlay -> type.isInstance(inlay.getRenderer()));
  }

  /**
   * Returns the after-line-end inlays for the given logical line,
   * in creation order (this is also the order they are displayed in).
   * The inlays are returned regardless of whether they are currently visible.
   *
   * @see #addAfterLineEndElement(int, boolean, EditorCustomElementRenderer)
   */
  @NotNull List<Inlay<?>> getAfterLineEndElementsForLogicalLine(int logicalLine);

  /**
   * Tells whether there exists at least one after-line-end inlay currently.
   */
  default boolean hasAfterLineEndElements() {
    return !getAfterLineEndElementsInRange(0, Integer.MAX_VALUE).isEmpty();
  }

  /**
   * When text is inserted at an inline inlay's offset,
   * the resulting inlay's position is determined by its
   * {@link InlayProperties#relatesToPrecedingText(boolean)} property.
   * But to enable natural editing experience around inline inlays
   * (so that the typed text appears at the caret visual position),
   * the caret position is also taken into account at document insertion.
   * This method allows disabling this accounting for the caret position,
   * and can be useful for document modifications that don't originate directly from user actions.
   */
  void setConsiderCaretPositionOnDocumentUpdates(boolean enabled);

  /**
   * Allows to perform a group of inlay operations (adding, disposing, updating) in batch mode.
   * For a large number of operations, this decreases the total time taken by the operations.
   * <p>
   * Batch mode introduces additional overhead though (roughly proportional to the file
   * size), so for a small number of operations in a big file,
   * using it will result in larger total execution time.
   * <p>
   * Using batch mode can also change certain outcomes of the applied changes.
   * In particular, the resulting caret's visual position might be different in batch
   * mode (due to simplified update logic) if inlays are added or removed at caret offset.
   * <p>
   * The number of changes that justifies using batch mode can be determined empirically,
   * but usually it's around hundred(s).
   * <p>
   * The executed code should not perform document- or editor-related operations
   * other than those operating on inlays. In particular, modifying the document,
   * querying or updating the folding, soft-wrap or caret state,
   * as well as performing editor coordinate transformations
   * (e.g., visual to logical position conversions)
   * might lead to incorrect results or throw an exception.
   *
   * @param batchMode whether to enable batch mode for executed inlay operations
   */
  void execute(boolean batchMode, @NotNull Runnable operation);

  /**
   * Tells whether the current code is executing as part of a batch inlay update operation.
   *
   * @see #execute(boolean, Runnable)
   */
  boolean isInBatchMode();

  /**
   * Adds a listener that will be notified after adding, updating and removal of custom visual elements.
   */
  void addListener(@NotNull Listener listener, @NotNull Disposable disposable);

  interface Listener extends EventListener {
    default void onAdded(@NotNull Inlay<?> inlay) { }

    default void onUpdated(@NotNull Inlay<?> inlay) { }

    /**
     * @param changeFlags see {@link ChangeFlags}
     */
    default void onUpdated(@NotNull Inlay<?> inlay, @MagicConstant(flagsFromClass = ChangeFlags.class) int changeFlags) {
      onUpdated(inlay);
    }

    default void onRemoved(@NotNull Inlay<?> inlay) { }

    /**
     * @see #execute(boolean, Runnable)
     */
    default void onBatchModeStart(@NotNull Editor editor) { }

    /**
     * @see #execute(boolean, Runnable)
     */
    default void onBatchModeFinish(@NotNull Editor editor) { }
  }

  /**
   * An adapter useful for cases where the same action is to be performed
   * after adding, updating or removing an inlay.
   */
  abstract class SimpleAdapter implements Listener {
    @Override
    public void onAdded(@NotNull Inlay<?> inlay) {
      onUpdated(inlay, ChangeFlags.WIDTH_CHANGED |
                       ChangeFlags.HEIGHT_CHANGED |
                       (inlay.getGutterIconRenderer() == null ? 0 : ChangeFlags.GUTTER_ICON_PROVIDER_CHANGED));
    }

    @Override
    public void onRemoved(@NotNull Inlay<?> inlay) {
      onUpdated(inlay, ChangeFlags.WIDTH_CHANGED |
                       ChangeFlags.HEIGHT_CHANGED |
                       (inlay.getGutterIconRenderer() == null ? 0 : ChangeFlags.GUTTER_ICON_PROVIDER_CHANGED));
    }
  }

  /**
   * Flags provided by {@link Listener#onUpdated(Inlay, int)}.
   */
  interface ChangeFlags {
    int WIDTH_CHANGED = 0x1;
    int HEIGHT_CHANGED = 0x2;
    int GUTTER_ICON_PROVIDER_CHANGED = 0x4;
  }
}
